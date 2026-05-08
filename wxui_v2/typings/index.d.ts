interface ApiResponse<T = unknown> {
  success: boolean
  message: string
  data: T
}

interface PageInfo {
  items: unknown[]
  total: number
  page: number
  size: number
}

interface GoodsItem {
  id: number
  title: string
  description: string
  price: number
  coverImage: string
  imageUrls: string[]
  imageKeys?: string[]
  categoryName?: string
  sellerName?: string
  sellerAvatar?: string
  qq?: string
  status: string
  conditionLevel?: string
  campusLocation?: string
  createdAt?: string
  viewCount?: number
  collectCount?: number
  category?: Category
  seller?: SellerInfo
  auditRemark?: string
}

interface SellerInfo {
  id?: number
  nickname: string
  avatarUrl?: string
  email?: string
  qq?: string
}

interface Category {
  id: number | string
  name: string
}

interface UserProfile {
  id?: number
  nickname: string
  avatarUrl: string
  email?: string
  qq?: string
}

interface UploadResult {
  url: string
  filename: string
}

interface PublishForm {
  title: string
  description: string
  price: string
  conditionLevel: string
  campusLocation: string
  categoryId: string
  photos: UploadResult[]
}

interface AuthForm {
  email: string
  password: string
}

interface RegisterForm {
  email: string
  code: string
  password: string
  nickname: string
}

interface ResetForm {
  email: string
  code: string
  newPassword: string
  confirmPassword: string
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown>
  header?: Record<string, string>
}

interface WxResponse<T = unknown> {
  data: T
  statusCode: number
  header: Record<string, string>
}
