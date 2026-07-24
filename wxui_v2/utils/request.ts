import { COMMON_MESSAGES } from './messages'

const AUTH_REDIRECT_DELAY_MS = 800
const AUTH_REDIRECT_GUARD_MS = 1500

let cachedToken: string | null | undefined
let loginRedirectPending = false

type WxRequestFn = (options: Record<string, unknown>) => void

interface ApiErrorPayload {
  code?: string
  message?: string
}

function readApiError(data: unknown): ApiErrorPayload {
  if (data && typeof data === 'object') {
    const payload = data as ApiErrorPayload
    const message = typeof payload.message === 'string' ? payload.message.trim() : ''
    return {
      code: typeof payload.code === 'string' ? payload.code : undefined,
      message: message || undefined
    }
  }
  return {}
}

function getAuthenticationFallback(code: string | undefined, hadToken: boolean): string {
  if (code === 'AUTH_LOGIN_REQUIRED') return COMMON_MESSAGES.LOGIN_REQUIRED
  if (code === 'AUTH_TOKEN_INVALID') return COMMON_MESSAGES.LOGIN_INVALID
  if (code === 'AUTH_TOKEN_EXPIRED') return COMMON_MESSAGES.SESSION_EXPIRED
  return hadToken ? COMMON_MESSAGES.SESSION_EXPIRED : COMMON_MESSAGES.LOGIN_REQUIRED
}

export function getToken(): string | undefined {
  if (cachedToken === undefined) {
    cachedToken = wx.getStorageSync('token') || null
  }
  return cachedToken || undefined
}

export function setToken(token: string): void {
  cachedToken = token || null
  loginRedirectPending = false
  if (cachedToken) {
    wx.setStorageSync('token', cachedToken)
  } else {
    wx.removeStorageSync('token')
  }
}

export function clearToken(): void {
  cachedToken = null
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
}

function isAuthPageActive(): boolean {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  return Boolean(currentPage && currentPage.route === 'pages/auth/auth')
}

export function redirectToLogin(failedToken?: string, redirect?: string, message?: string): void {
  if (getToken() !== failedToken) return

  const hadToken = Boolean(failedToken)
  clearToken()
  if (loginRedirectPending || isAuthPageActive()) return

  loginRedirectPending = true
  wx.showToast({
    title: message || (hadToken ? COMMON_MESSAGES.SESSION_EXPIRED : COMMON_MESSAGES.LOGIN_REQUIRED),
    icon: 'none',
    duration: 1500
  })

  const authUrl = redirect
    ? `/pages/auth/auth?redirect=${encodeURIComponent(redirect)}`
    : '/pages/auth/auth'
  setTimeout(() => {
    wx.redirectTo({
      url: authUrl,
      success: () => {
        setTimeout(() => {
          loginRedirectPending = false
        }, AUTH_REDIRECT_GUARD_MS)
      },
      fail: () => {
        loginRedirectPending = false
      }
    })
  }, AUTH_REDIRECT_DELAY_MS)
}

export function request<T = unknown>(options: RequestOptions): Promise<WxResponse<T>> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.header
    }

    if (token) {
      header.Authorization = `Bearer ${token}`
    }

    const wxRequest = (wx as unknown as { request: WxRequestFn }).request
    wxRequest({
      url: options.url,
      method: options.method || 'GET',
      data: options.data as WechatMiniprogram.IAnyObject,
      header,
      success: (res: WechatMiniprogram.RequestSuccessCallbackResult) => {
        if (res.statusCode === 401) {
          const isAuthEndpoint = options.url.includes('/auth/')
          if (isAuthEndpoint) {
            resolve(res as unknown as WxResponse<T>)
          } else {
            const authError = readApiError(res.data)
            const message = authError.message || getAuthenticationFallback(authError.code, Boolean(token))
            redirectToLogin(token, undefined, message)
            reject(new Error(message))
          }
          return
        }
        resolve(res as unknown as WxResponse<T>)
      },
      fail: () => reject(new Error(COMMON_MESSAGES.NETWORK_ERROR))
    })
  })
}
