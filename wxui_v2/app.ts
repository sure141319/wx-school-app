import { getBaseUrl } from './config/env'

App({
  globalData: {
    userInfo: null as UserProfile | null,
    baseUrl: getBaseUrl()
  }
})
