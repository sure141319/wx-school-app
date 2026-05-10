import { request } from '../../utils/request'
import { uploadImage } from '../../utils/upload'

const app = getApp<{ globalData: { baseUrl: string } }>()

interface ProfilePageData {
  profile: UserProfile
  avatarValue: string
  goodsItems: GoodsItem[]
  loading: boolean
  loadingMore: boolean
  saving: boolean
  info: string
  showFeedbackModal: boolean
  page: number
  size: number
  hasMore: boolean
}

Component({
  data: {
    profile: { nickname: '', avatarUrl: '' },
    avatarValue: '',
    goodsItems: [],
    loading: false,
    loadingMore: false,
    saving: false,
    info: '',
    showFeedbackModal: false,
    page: 0,
    size: 20,
    hasMore: true
  } as ProfilePageData,

  methods: {
    onShow() {
      if (!wx.getStorageSync('token')) {
        wx.redirectTo({ url: '/pages/auth/auth?redirect=/pages/profile/profile' })
        return
      }
      this.loadProfile()
      this.loadMyGoods(true)
    },

    onReachBottom() {
      if (this.data.hasMore && !this.data.loading && !this.data.loadingMore) {
        this.loadMyGoods(false, true)
      }
    },

    async loadProfile() {
      try {
        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me`,
          method: 'GET'
        })
        const profile = (res.data?.data as unknown as UserProfile) || { nickname: '', avatarUrl: '' }
        this.setData({
          profile,
          avatarValue: profile.avatarUrl || ''
        })
      } catch (_err) {
        this.setData({ info: '个人资料加载失败' })
      }
    },

    async loadMyGoods(resetPage = false, append = false) {
      const nextPage = resetPage ? 0 : this.data.page

      if (resetPage) {
        this.setData({ loading: true, hasMore: true, page: 0 })
      } else if (append) {
        this.setData({ loadingMore: true })
      } else {
        this.setData({ loading: true })
      }

      try {
        const res = await request<ApiResponse<PageInfo>>({
          url: `${app.globalData.baseUrl}/goods/mine?page=${nextPage}&size=${this.data.size}`,
          method: 'GET'
        })

        const pageData = res.data?.data as unknown as PageInfo | undefined
        const items = ((pageData?.items || []) as unknown as GoodsItem[]).map(item => ({
          ...item,
          imageUrls: item.imageUrls || []
        }))
        const total = pageData?.total || 0
        const hasMore = items.length === this.data.size && (nextPage + 1) * this.data.size < total

        const allItems = (append && !resetPage)
          ? [...this.data.goodsItems, ...items]
          : items

        this.setData({
          goodsItems: allItems,
          page: nextPage + 1,
          hasMore
        })
      } catch (_err) {
        if (!append) {
          this.setData({ info: '商品列表加载失败' })
        }
      } finally {
        this.setData({ loading: false, loadingMore: false })
      }
    },

    onNicknameInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'profile.nickname': e.detail.value, info: '' })
    },

    chooseAvatar() {
      wx.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const filePath = res.tempFiles?.[0]?.tempFilePath
          if (!filePath) return
          wx.showLoading({ title: '上传头像...', mask: true })
          try {
            const upload = await uploadImage(filePath)
            this.setData({
              'profile.avatarUrl': upload.url,
              avatarValue: upload.filename,
              info: '头像已上传，保存后生效'
            })
          } catch (_err) {
            this.setData({ info: '头像上传失败' })
          } finally {
            wx.hideLoading()
          }
        }
      })
    },

    async saveProfile() {
      const nickname = (this.data.profile.nickname || '').trim()
      if (!nickname) {
        this.setData({ info: '请输入昵称' })
        return
      }
      if (this.data.saving) return

      this.setData({ saving: true, info: '' })
      try {
        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me`,
          method: 'PUT',
          data: {
            nickname,
            avatarUrl: this.data.avatarValue || null
          } as unknown as Record<string, unknown>
        })
        const profile = (res.data?.data as unknown as UserProfile) || this.data.profile
        const user = JSON.parse(wx.getStorageSync('user') || '{}')
        wx.setStorageSync('user', JSON.stringify({
          ...user,
          nickname: profile.nickname,
          avatarUrl: profile.avatarUrl
        }))
        this.setData({
          profile,
          avatarValue: profile.avatarUrl || this.data.avatarValue,
          info: ''
        })
        wx.showToast({ title: '保存成功', icon: 'success' })
      } catch (_err) {
        this.setData({ info: '保存失败，请稍后重试' })
      } finally {
        this.setData({ saving: false })
      }
    },

    editGoods(e: WechatMiniprogram.TouchEvent) {
      const id = e.currentTarget.dataset.id
      wx.setStorageSync('editGoodsId', id)
      wx.switchTab({ url: '/pages/publish/publish' })
    },

    async toggleStatus(e: WechatMiniprogram.TouchEvent) {
      const id = e.currentTarget.dataset.id as number
      const status = e.currentTarget.dataset.status as string
      if (!id) return
      if (status === 'PENDING_REVIEW' || status === 'REJECTED') {
        wx.showToast({ title: '审核中或已驳回的商品不能上下架', icon: 'none' })
        return
      }
      const nextStatus = status === 'ON_SALE' ? 'OFF_SHELF' : 'ON_SALE'
      try {
        await request({
          url: `${app.globalData.baseUrl}/goods/${id}/status`,
          method: 'PATCH',
          data: { status: nextStatus }
        })
        wx.showToast({ title: nextStatus === 'ON_SALE' ? '已上架' : '已下架', icon: 'success' })
        this.loadMyGoods(true)
      } catch (_err) {
        this.setData({ info: '状态更新失败' })
      }
    },

    removeGoods(e: WechatMiniprogram.TouchEvent) {
      const id = e.currentTarget.dataset.id as number
      if (!id) return
      wx.showModal({
        title: '确认删除',
        content: '删除后无法恢复，确认删除这个商品吗？',
        confirmText: '删除',
        confirmColor: '#D92D20',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await request({
              url: `${app.globalData.baseUrl}/goods/${id}`,
              method: 'DELETE'
            })
            wx.showToast({ title: '已删除', icon: 'success' })
            this.loadMyGoods(true)
          } catch (_err) {
            this.setData({ info: '删除失败' })
          }
        }
      })
    },

    onUnload() {
      const hasUnsavedAvatar = this.data.avatarValue && this.data.avatarValue !== this.data.profile.avatarUrl
      if (hasUnsavedAvatar) {
        wx.showModal({
          title: '保存头像',
          content: '你上传了头像但还没有保存，是否现在保存？',
          confirmText: '保存',
          cancelText: '不保存',
          success: (res) => {
            if (res.confirm) {
              this.saveProfile()
            }
          }
        })
      }
    },

    logout() {
      wx.removeStorageSync('token')
      wx.removeStorageSync('user')
      wx.reLaunch({ url: '/pages/index/index' })
    },

    showFeedback() {
      this.setData({ showFeedbackModal: true })
    },

    closeFeedback() {
      this.setData({ showFeedbackModal: false })
    },

    copyQQ() {
      wx.setClipboardData({
        data: '1078739008',
        success: () => {
          wx.showToast({ title: '已复制', icon: 'success' })
        }
      })
    }
  }
})
