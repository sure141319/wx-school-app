export function readStoredUser(): Partial<UserProfile> {
  let value: unknown
  try {
    value = wx.getStorageSync('user') as unknown
  } catch (_error) {
    return {}
  }
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value as Partial<UserProfile>
  }
  if (typeof value !== 'string') return {}

  try {
    const parsed = JSON.parse(value) as unknown
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Partial<UserProfile>
      : {}
  } catch (_error) {
    return {}
  }
}

export function updateStoredUser(profile: Partial<UserProfile>): void {
  try {
    wx.setStorageSync('user', JSON.stringify({
      ...readStoredUser(),
      ...profile
    }))
  } catch (_error) {
    // 用户资料以服务端为准，本地缓存写入失败不应把已成功的操作显示成失败。
  }
}
