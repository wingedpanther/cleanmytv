import { describe, expect, it } from 'vitest'
import { choiceFor } from '../src/main/debloat'
import { defaultOff, defaultTick } from '../src/renderer/src/lib/ticks'
import type { DebloatEntry, PackageState } from '@shared/api'

const entry = (pkg: string, state: PackageState, extra: Partial<DebloatEntry> = {}): DebloatEntry => {
  const e = { pkg, note: '', suggested: true, state, common: 0, ...extra }
  return { ...e, choice: choiceFor(e) }
}

describe('the defaults Clean up and the guided run switch off', () => {
  it('takes plain bloat that is on', () => {
    expect(defaultTick(entry('com.vendor.ads', 'enabled'))).toBe(true)
  })

  it('leaves the Photos screensaver, phone remote, casting and voice on (#57)', () => {
    for (const pkg of ['com.google.android.apps.tv.dreamx', 'com.google.android.backdrop', 'com.google.android.tv.remote.service', 'com.google.android.apps.mediashell', 'com.google.android.katniss']) {
      expect(defaultTick(entry(pkg, 'enabled')), pkg).toBe(false)
    }
    const section = choiceFor({ pkg: 'com.vendor.assistant', note: '', suggested: true, section: 'Personal choice: voice assistant' })
    expect(defaultTick({ ...entry('com.vendor.assistant', 'enabled'), choice: section })).toBe(false)
  })

  it('skips packages that are not on the TV, and ones the list only mentions', () => {
    expect(defaultTick(entry('com.vendor.ads', 'absent'))).toBe(false)
    expect(defaultTick(entry('com.vendor.ads', 'enabled', { suggested: false }))).toBe(false)
  })

  it('keeps what an earlier run switched off, so running again still has a set', () => {
    expect(defaultTick(entry('com.vendor.ads', 'disabled'))).toBe(true)
    expect(defaultTick(entry('com.vendor.extra', 'disabled', { suggested: false, recorded: 'enabled' }))).toBe(true)
    expect(defaultTick(entry('com.vendor.owner', 'disabled', { suggested: false }))).toBe(false)
  })

  it('sends only what is still on, and nothing once an earlier run took care of it', () => {
    const entries = [
      entry('com.vendor.ads', 'enabled'),
      entry('com.vendor.done', 'disabled'),
      entry('com.vendor.gone', 'absent'),
      entry('com.google.android.apps.tv.dreamx', 'enabled')
    ]
    expect(defaultOff(entries)).toEqual(['com.vendor.ads'])
    expect(defaultOff(entries.slice(1))).toEqual([])
  })
})
