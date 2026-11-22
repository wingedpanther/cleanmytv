import { describe, expect, it } from 'vitest'
import { parseHealth } from '../src/main/system'

describe('parseHealth', () => {
  it('reads memory, uptime, the hottest chip zone and the chip from a TV', () => {
    const out = [
      'MemTotal:        2025912 kB',
      'MemAvailable:     246420 kB',
      '951867.13 879853.50',
      'zone cpu-thermal 61250',
      'zone gpu-thermal 58000',
      'zone battery 30000',
      'chip mt5895 mt5895',
      'cores 4'
    ].join('\n')
    expect(parseHealth(out)).toEqual({ memTotalMb: 1978, memFreeMb: 241, uptimeS: 951867, tempC: 61, chip: 'mt5895', cores: 4 })
  })

  it("falls back to thermalservice, leaves out the battery, and says nothing when there's no reading", () => {
    const zones = 'zone  \n\tTemperature{mValue=44.5, mType=0, mName=cpu, mStatus=0}\n\tTemperature{mValue=90.0, mType=2, mName=battery, mStatus=0}\nchip  ranchu\ncores 2'
    expect(parseHealth(zones)).toMatchObject({ tempC: 45, chip: 'ranchu', cores: 2 })
    expect(parseHealth('zone tsens_tz_sensor0 0\nzone x 255000\nchip  \n')).toMatchObject({ tempC: null, chip: '', memTotalMb: 0 })
  })
})
