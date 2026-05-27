import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { defaultClock, setClockForTests } from './src/lib/clock/clock'
import { resetEnvCacheForTests } from './src/lib/env/env'

// Guard against singleton leakage between tests in the same worker.
afterEach(() => {
  setClockForTests(defaultClock)
  resetEnvCacheForTests()
})
