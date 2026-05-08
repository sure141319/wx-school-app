import { getToken, clearTokenCache } from './request'

const app = getApp<{ globalData: { baseUrl: string } }>()

export function uploadImage(filePath: string): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    wx.uploadFile({
      url: `${app.globalData.baseUrl}/uploads/image`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res: WechatMiniprogram.UploadFileSuccessCallbackResult) => {
        try {
          const data = JSON.parse(res.data || '{}')
          if (res.statusCode === 401) {
            wx.removeStorageSync('token')
            wx.removeStorageSync('user')
            clearTokenCache()
            wx.redirectTo({ url: '/pages/auth/auth' })
            reject(new Error('未登录'))
            return
          }
          if (res.statusCode >= 400) {
            reject(new Error(data.message || 'upload failed'))
            return
          }
          resolve(data.data || {})
        } catch (err) {
          reject(err)
        }
      },
      fail: (err: WechatMiniprogram.GeneralCallbackResult) => reject(err)
    })
  })
}
