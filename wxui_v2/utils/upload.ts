import { getToken, clearTokenCache, request } from './request'
import { COMMON_MESSAGES } from './messages'

const app = getApp<{ globalData: { baseUrl: string } }>()

type CompressImageFn = (options: {
  src: string
  quality: number
  compressedWidth?: number
  compressedHeight?: number
  success: (res: { tempFilePath: string }) => void
  fail: () => void
}) => void

type GetImageInfoFn = (options: {
  src: string
  success: (res: { width: number; height: number }) => void
  fail: () => void
}) => void

const AVATAR_UPLOAD_MAX_EDGE = 1024
const GOODS_UPLOAD_MAX_EDGE = 2048

function handleLoginRequired(reject: (reason?: unknown) => void) {
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
  clearTokenCache()
  wx.redirectTo({ url: '/pages/auth/auth' })
  reject(new Error(COMMON_MESSAGES.IMAGE_UPLOAD_LOGIN_REQUIRED))
}

function getResizeOptions(filePath: string, maxEdge: number): Promise<{
  compressedWidth?: number
  compressedHeight?: number
}> {
  return new Promise((resolve) => {
    const getImageInfo = (wx as unknown as { getImageInfo?: GetImageInfoFn }).getImageInfo
    if (!getImageInfo) {
      resolve({})
      return
    }
    getImageInfo({
      src: filePath,
      success: ({ width, height }) => {
        if (Math.max(width, height) <= maxEdge) {
          resolve({})
          return
        }
        resolve(width >= height
          ? { compressedWidth: maxEdge }
          : { compressedHeight: maxEdge })
      },
      fail: () => resolve({})
    })
  })
}

async function compressImageForUpload(filePath: string, usage: 'avatar' | 'goods'): Promise<string> {
  const resizeOptions = await getResizeOptions(
    filePath,
    usage === 'avatar' ? AVATAR_UPLOAD_MAX_EDGE : GOODS_UPLOAD_MAX_EDGE
  )
  return new Promise((resolve) => {
    const compressImage = (wx as unknown as { compressImage?: CompressImageFn }).compressImage
    if (!compressImage) {
      resolve(filePath)
      return
    }

    compressImage({
      src: filePath,
      quality: usage === 'avatar' ? 65 : 70,
      ...resizeOptions,
      success: res => resolve(res.tempFilePath || filePath),
      fail: () => resolve(filePath)
    })
  })
}

export async function uploadImage(filePath: string, usage: 'avatar' | 'goods' = 'goods'): Promise<UploadResult> {
  const uploadPath = await compressImageForUpload(filePath, usage)

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
      formData: { usage },
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

export async function deleteStagedImage(objectKey: string): Promise<void> {
  if (!objectKey) return
  const res = await request<ApiResponse<void>>({
    url: `${app.globalData.baseUrl}/uploads/image?objectKey=${encodeURIComponent(objectKey)}`,
    method: 'DELETE'
  })
  if (res.statusCode >= 400 || !res.data?.success) {
    throw new Error(res.data?.message || '暂存图片删除失败')
  }
}
