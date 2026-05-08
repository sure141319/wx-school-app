let cachedToken: string | null | undefined

export function getToken(): string | undefined {
  if (cachedToken === undefined) {
    cachedToken = wx.getStorageSync('token') || null
  }
  return cachedToken || undefined
}

export function clearTokenCache(): void {
  cachedToken = undefined
}

export function request<T = unknown>(options: RequestOptions): Promise<WxResponse<T>> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.header
    }

    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    wx.request({
      url: options.url,
      method: options.method || 'GET',
      data: options.data as WechatMiniprogram.IAnyObject,
      header,
      success: (res: WechatMiniprogram.RequestSuccessCallbackResult<T>) => {
        if (res.statusCode === 401) {
          const isAuthEndpoint = options.url.includes('/auth/')
          if (isAuthEndpoint) {
            resolve(res as unknown as WxResponse<T>)
          } else {
            wx.removeStorageSync('token')
            wx.removeStorageSync('user')
            clearTokenCache()
            wx.redirectTo({ url: '/pages/auth/auth' })
            reject(new Error('未登录'))
          }
          return
        }
        resolve(res as unknown as WxResponse<T>)
      },
      fail: (err: WechatMiniprogram.GeneralCallbackResult) => reject(err)
    })
  })
}
