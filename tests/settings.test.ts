import { describe, expect, it } from 'vitest'
import { clean, parse } from '../src/main/home'

describe('new Home settings from the desktop app', () => {
  it('takes daily limits of 5 to 720 minutes for real package names only', () => {
    expect(clean({ limits: { 'com.google.android.youtube.tv': 60 } })).toEqual({ limits: { 'com.google.android.youtube.tv': 60 } })
    expect(() => clean({ limits: { 'com.netflix.ninja': 3 } })).toThrow(/limits/)
    expect(() => clean({ limits: { 'rm -rf /': 60 } })).toThrow(/limits/)
  })

  it('takes the font, idle style, Bing every day and the editor lock', () => {
    expect(clean({ font: 'serif', idle_style: 'photos', bing_daily: true, lock_editor: true })).toEqual({ font: 'serif', idle_style: 'photos', bing_daily: true, lock_editor: true })
    expect(() => clean({ font: 'comic' })).toThrow(/font/)
    expect(clean({ quick: ['timer', 'settings'] })).toEqual({ quick: ['timer', 'settings'] })
  })

  it('takes an input id or the last input for power on, waits of 0, 3, 5 or 10 s, and reads the TV\'s inputs', () => {
    const hdmi = 'com.mediatek.tvinput/.hdmi.HDMIInputService/HW5'
    expect(clean({ power_on_target: hdmi, power_on_wait: 5 })).toEqual({ power_on_target: hdmi, power_on_wait: 5 })
    expect(clean({ power_on_target: '' })).toEqual({ power_on_target: '' })
    expect(() => clean({ power_on_target: 'x; reboot' })).toThrow(/power_on_target/)
    expect(() => clean({ power_on_wait: 7 })).toThrow(/power_on_wait/)
    const cfg = parse(`Result: Bundle[{config=${JSON.stringify({ power_on_wait: 4, tv_inputs: [{ id: hdmi, name: 'PS5' }, { id: 'bad id', name: 'x' }, { id: 'a/b', name: ' ' }] })}}]`)
    expect(cfg.power_on_wait).toBe(0)
    expect(cfg.power_on_target).toBe('')
    expect(cfg.tv_inputs).toEqual([{ id: hdmi, name: 'PS5' }, { id: 'a/b', name: 'a/b' }])
  })

  it('takes where new apps go: nowhere, the dock or a row by name, as plain text', () => {
    expect(clean({ new_apps_to: 'dock' })).toEqual({ new_apps_to: 'dock' })
    expect(clean({ new_apps_to: '' })).toEqual({ new_apps_to: '' })
    expect(clean({ new_apps_to: ' Watch\n' })).toEqual({ new_apps_to: 'Watch' })
    expect(clean({ new_apps_to: 'x'.repeat(40) }).new_apps_to).toHaveLength(30)
    expect(() => clean({ new_apps_to: 3 })).toThrow(/new_apps_to/)
    expect(parse('Result: Bundle[{config={}}]').new_apps_to).toBe('')
    expect(parse(`Result: Bundle[{config=${JSON.stringify({ new_apps_to: 'Watch' })}}]`).new_apps_to).toBe('Watch')
  })
})
