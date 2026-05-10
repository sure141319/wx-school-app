// Environment config. Use a LAN IP for real-device debugging and the public
// domain for production/release builds.
interface EnvConfig {
  baseUrl: string
}

interface EnvMap {
  current: string
  dev: EnvConfig
  prod: EnvConfig
  [key: string]: EnvConfig | string
}

const ENV: EnvMap = {
  current: 'prod',

  dev: {
    baseUrl: 'http://localhost:8080/api/v1',
  },

  prod: {
    baseUrl: 'https://www.ahut-campus.site/api/v1',
  }
}

export function getBaseUrl(): string {
  const config = (ENV[ENV.current] as EnvConfig) || ENV.dev
  return config.baseUrl
}
