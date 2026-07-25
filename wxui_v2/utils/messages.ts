export const COMMON_MESSAGES = {
  LOGIN_REQUIRED: '请先登录',
  SESSION_EXPIRED: '登录已过期，请重新登录',
  LOGIN_INVALID: '登录状态无效，请重新登录',
  NETWORK_ERROR: '网络连接异常，请稍后重试',
  WECHAT_LOGIN_FAILED: '微信登录失败，请稍后重试',
  WECHAT_BIND_FAILED: '微信绑定失败，请稍后重试',
  COPY_FAILED: '复制失败，请重新点击',
  IMAGE_SELECTION_FAILED: '无法选择图片，请检查相册或相机权限后重试',
  IMAGE_UPLOAD_LOGIN_REQUIRED: '图片上传失败，请先登录',
  IMAGE_UPLOAD_FAILED: '图片上传失败，请稍后重试',
  AVATAR_UPLOAD_FAILED: '头像上传失败，请稍后重试'
} as const

export function loadFailed(subject: string): string {
  return `${subject}加载失败，请稍后重试`
}

export function actionFailed(subject: string): string {
  return `${subject}失败，请稍后重试`
}

export function isUserCancellation(error?: { errMsg?: string }): boolean {
  const errMsg = error && error.errMsg
  return typeof errMsg === 'string' && errMsg.toLowerCase().includes('cancel')
}
