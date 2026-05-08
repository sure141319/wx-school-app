// 环境配置 - 根据实际情况修改
// 模拟器调试：用局域网 IP 或 localhost
// 真机调试：用手机和电脑同一局域网下的电脑 IP
// 上线部署：用公网 IP 或域名

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
