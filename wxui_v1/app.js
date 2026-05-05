const { getBaseUrl } = require('./config/env.js')

App({
  globalData: {
    userInfo: null,
    baseUrl: getBaseUrl()
  }
})
