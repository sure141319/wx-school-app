import { request } from '../utils/request'

interface ProfileUpdateInput {
  nickname: string
  wechatId: string
  qq: string
  avatarUrl?: string
}

interface EmailBindInput {
  email: string
  code: string
  password: string
}

export async function fetchProfile(baseUrl: string): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me`,
    method: 'GET'
  }, '个人资料加载失败')
}

export async function fetchMyGoods(
  baseUrl: string,
  page: number,
  size: number
): Promise<PageResponse<MyGoodsListItem>> {
  return requestData<PageResponse<MyGoodsListItem>>({
    url: `${baseUrl}/goods/mine?page=${page}&size=${size}`,
    method: 'GET'
  }, '商品列表加载失败')
}

export async function saveProfile(
  baseUrl: string,
  data: ProfileUpdateInput
): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me`,
    method: 'PUT',
    data: { ...data }
  }, '保存失败，请稍后重试')
}

export async function bindWechatAccount(baseUrl: string, code: string): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me/wechat-bind`,
    method: 'POST',
    data: { code }
  }, '绑定微信失败，请稍后重试')
}

export async function unbindWechatAccount(baseUrl: string): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me/wechat-bind`,
    method: 'DELETE'
  }, '解绑微信失败，请稍后重试')
}

export async function sendEmailBindCode(baseUrl: string, email: string): Promise<void> {
  await requestSuccess({
    url: `${baseUrl}/auth/email-code`,
    method: 'POST',
    data: { email, purpose: 'BIND_EMAIL' }
  }, '发送验证码失败，请稍后重试')
}

export async function bindEmailAccount(
  baseUrl: string,
  data: EmailBindInput
): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me/email-bind`,
    method: 'POST',
    data: { ...data }
  }, '绑定邮箱失败，请稍后重试')
}

export async function unbindEmailAccount(baseUrl: string): Promise<UserProfile> {
  return requestData<UserProfile>({
    url: `${baseUrl}/users/me/email-bind`,
    method: 'DELETE'
  }, '解绑邮箱失败，请稍后重试')
}

export async function updateMyGoodsStatus(
  baseUrl: string,
  goodsId: number,
  status: string
): Promise<void> {
  await requestSuccess({
    url: `${baseUrl}/goods/${goodsId}/status`,
    method: 'PATCH',
    data: { status }
  }, '状态更新失败，请稍后重试')
}

export async function deleteMyGoods(baseUrl: string, goodsId: number): Promise<void> {
  await requestSuccess({
    url: `${baseUrl}/goods/${goodsId}`,
    method: 'DELETE'
  }, '删除失败，请稍后重试')
}

export function getWechatLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    wx.login({
      timeout: 10000,
      success: (res) => {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error(res.errMsg || 'wx.login failed'))
        }
      },
      fail: reject
    })
  })
}

export function isAccountMergeMessage(message?: string): boolean {
  return Boolean(message && (message.includes('已注册') || message.includes('账号合并')))
}

export function getProfileApiErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function requestData<T>(options: RequestOptions, fallback: string): Promise<T> {
  const response = await request<ApiResponse<T>>(options)
  const payload = response.data
  if (!payload?.success || payload.data === null || payload.data === undefined) {
    throw new Error(payload?.message || fallback)
  }
  return payload.data
}

async function requestSuccess(options: RequestOptions, fallback: string): Promise<void> {
  const response = await request<ApiResponse<unknown>>(options)
  if (!response.data?.success) {
    throw new Error(response.data?.message || fallback)
  }
}
