import { describe, expect, it } from 'vitest'
import { usbOffWhileDebugging } from '../src/renderer/src/lib/usb'

describe('USB off while debugging', () => {
  it('knows the Xiaomi boards that lose USB while debugging is on (#71)', () => {
    for (const model of ['MiTV-MZTU0', 'MiTV-MZTU1', 'MiTV-AFMU0', 'MiTV-AFKR0', 'MIBOX4', ' MiTV-MZTU0 ']) {
      expect(usbOffWhileDebugging({ model })).toBe(true)
    }
  })

  it('leaves other TVs alone', () => {
    for (const model of ['MiTV-MOOQ0', 'MiTV-MSSP3', 'SHIELD Android TV', '']) expect(usbOffWhileDebugging({ model })).toBe(false)
    expect(usbOffWhileDebugging(undefined)).toBe(false)
  })
})
