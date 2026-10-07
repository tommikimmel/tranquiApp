import { describe, expect, it } from 'vitest'
import { fitWithin, PROFILE_PHOTO_MAX_SIDE } from './profilePhoto'

describe('fitWithin', () => {
  it('scales a landscape photo so its longest side is the max', () => {
    expect(fitWithin(4000, 3000)).toEqual({ width: PROFILE_PHOTO_MAX_SIDE, height: 300 })
  })

  it('scales a portrait photo by its height', () => {
    expect(fitWithin(1080, 1920)).toEqual({ width: 225, height: PROFILE_PHOTO_MAX_SIDE })
  })

  it('never upscales a small photo', () => {
    expect(fitWithin(200, 150)).toEqual({ width: 200, height: 150 })
  })

  it('returns zero size for an image without dimensions', () => {
    expect(fitWithin(0, 0)).toEqual({ width: 0, height: 0 })
  })
})
