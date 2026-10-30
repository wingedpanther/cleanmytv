// Copies the debloat lists from upstream cleanyourtv into resources/upstream, which the app bundles.
// Themes are left out: they are for upstream's launcher, and CleanMyTV Home has its own. The commit
// and a content hash are pinned in upstream.json (bump them with pin-upstream.mjs after reviewing
// upstream); nothing is copied unless the content matches.
//   npm run sync                         download the pinned commit
//   UPSTREAM_DIR=<clone> npm run sync    use a local checkout of the same commit
//   UPSTREAM_UNPINNED=1 ...              allow other content, for trying upstream changes locally
import { execFileSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { treeHash } from './tree-hash.mjs'

const pin = JSON.parse(readFileSync('upstream.json', 'utf8'))
const out = resolve('resources/upstream')
let src = process.env.UPSTREAM_DIR && resolve(process.env.UPSTREAM_DIR)
let tmp = null
if (!src) {
  tmp = mkdtempSync(join(tmpdir(), 'cytv-upstream-'))
  const res = await fetch(`https://codeload.github.com/${pin.repo}/tar.gz/${pin.commit}`)
  if (!res.ok) throw new Error(`download failed: ${res.status}`)
  writeFileSync(join(tmp, 'src.tar.gz'), Buffer.from(await res.arrayBuffer()))
  execFileSync('tar', ['-xf', join(tmp, 'src.tar.gz'), '-C', tmp])
  src = join(tmp, readdirSync(tmp).find((n) => n.startsWith('cleanyourtv')))
}
if (!existsSync(join(src, 'examples'))) throw new Error(`${src} is not a cleanyourtv checkout`)
const got = treeHash(src, ['examples', 'themes', 'LICENSE'])
if (got !== pin.dataSha256) {
  const msg = `upstream lists and themes do not match upstream.json (got ${got}, pinned ${pin.dataSha256} at ${pin.commit})`
  if (!process.env.UPSTREAM_UNPINNED) throw new Error(msg + '. Review the change, then run scripts/pin-upstream.mjs.')
  console.warn('warning: ' + msg)
}

rmSync(out, { recursive: true, force: true })
mkdirSync(join(out, 'examples'), { recursive: true })
let lists = 0
for (const id of readdirSync(join(src, 'examples'))) {
  for (const f of ['debloat.txt', 'config.json', 'tv.json']) {
    const p = join(src, 'examples', id, f)
    if (existsSync(p)) {
      mkdirSync(join(out, 'examples', id), { recursive: true })
      cpSync(p, join(out, 'examples', id, f))
      if (f === 'debloat.txt') lists++
    }
  }
}
cpSync(join(src, 'LICENSE'), join(out, 'LICENSE'))
writeFileSync(join(out, 'SOURCE.json'), JSON.stringify({ repo: `https://github.com/${pin.repo}`, rev: got === pin.dataSha256 ? pin.commit : 'unpinned', dataSha256: got, synced: new Date().toISOString() }, null, 2))
if (tmp) rmSync(tmp, { recursive: true, force: true })
console.log(`synced ${lists} lists into ${out}`)
