// 环境配置 - 根据实际情况修改
// 模拟器调试：用局域网 IP（如 10.70.12.54）或 localhost
// 真机调试：用手机和电脑同一局域网下的电脑 IP
// 上线部署：用公网 IP 或域名

const ENV = {
  // 当前环境，可选值: 'dev' | 'prod'
  current: 'prod',

  dev: {
    // 后端 API 地址
    baseUrl: 'http://localhost:8080/api/v1',
  },

  prod: {
    baseUrl: 'https://www.ahut-campus.site/api/v1',
  }
}

function getBaseUrl() {
  const config = ENV[ENV.current] || ENV.dev
  return config.baseUrl
}

module.exports = { getBaseUrl }
