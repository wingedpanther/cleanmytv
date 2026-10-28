// A stand-in look for apps the desktop app can't draw: a colour from the package name and a monogram.

const KNOWN_COLOURS: Record<string, string> = {
  'com.netflix.ninja': '#b20710',
  'com.google.android.youtube.tv': '#c4302b',
  'com.google.android.youtube.tvmusic': '#a5221c',
  'com.amazon.amazonvideo.livingroom': '#1a98ff',
  'com.disney.disneyplus': '#113ccf',
  'com.spotify.tv.android': '#1db954',
  'com.plexapp.android': '#cc7b19',
  'com.apple.atve.androidtv.appletv': '#333333',
  'tv.twitch.android.app': '#7a3fd8',
  'org.videolan.vlc': '#e85e00',
  'com.hbo.hbonow': '#2b2bd9',
  'in.startv.hotstar': '#0f1a5c'
}

export function appColour(pkg: string): string {
  if (KNOWN_COLOURS[pkg]) return KNOWN_COLOURS[pkg]
  let h = 0
  for (const c of pkg) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return `hsl(${h % 360} 45% 34%)`
}

export function monogram(label: string): string {
  const words = label.replace(/[^\p{L}\p{N} ]/gu, ' ').split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  if (words.length === 1) return words[0][0].toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}
