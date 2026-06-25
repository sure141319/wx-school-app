import { getToken, clearTokenCache } from './request'
import { COMMON_MESSAGES } from './messages'

const app = getApp<{ globalData: { baseUrl: string } }>()

type CompressImageFn = (options: {
  src: string
  quality: number
  success: (res: { tempFilePath: string }) => void
  fail: () => void
}) => void

function handleLoginRequired(reject: (reason?: unknown) => void) {
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
  clearTokenCache()
  wx.redirectTo({ url: '/pages/auth/auth' })
  reject(new Error(COMMON_MESSAGES.IMAGE_UPLOAD_LOGIN_REQUIRED))
}

function compressImageForUpload(filePath: string): Promise<string> {
  return new Promise((resolve) => {
    const compressImage = (wx as unknown as { compressImage?: CompressImageFn }).compressImage
    if (!compressImage) {
      resolve(filePath)
      return
    }

    compressImage({
      src: filePath,
      quality: 70,
      success: res => resolve(res.tempFilePath || filePath),
      fail: () => resolve(filePath)
    })
  })
}

export async function uploadImage(filePath: string): Promise<UploadResult> {
  const uploadPath = await compressImageForUpload(filePath)

  return new Promise((resolve, reject) => {
    const token = getToken()
    if (!token) {
      handleLoginRequired(reject)
      return
    }

    wx.uploadFile({
      url: `${app.globalData.baseUrl}/uploads/image`,
      filePath: uploadPath,
      name: 'file',
      header: { Authorization: `Bearer ${token}` },
      success: (res: WechatMiniprogram.UploadFileSuccessCallbackResult) => {
        if (res.statusCode === 401 || res.statusCode === 403) {
          handleLoginRequired(reject)
          return
        }

        try {
          const data = JSON.parse(res.data || '{}')
          if (res.statusCode >= 400) {
            reject(new Error(data.message || COMMON_MESSAGES.IMAGE_UPLOAD_FAILED))
            return
          }
          resolve(data.data || {})
        } catch (_err) {
          reject(new Error(COMMON_MESSAGES.IMAGE_UPLOAD_FAILED))
        }
      },
      fail: () => reject(new Error(COMMON_MESSAGES.NETWORK_ERROR))
    })
  })
}
