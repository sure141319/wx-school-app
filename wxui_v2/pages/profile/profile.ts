import { request } from '../../utils/request'
import { uploadImage } from '../../utils/upload'
import { resolveProfileDisplayAvatar, resolveQqAvatarPreview } from '../../utils/avatar'
import { COMMON_MESSAGES, actionFailed, loadFailed } from '../../utils/messages'

const app = getApp<{ globalData: { baseUrl: string } }>()
const QQ_REGEX = /^\d{5,12}$/

interface ProfilePageData {
  profile: UserProfile
  profileDraft: UserProfile
  displayAvatarUrl: string
  draftAvatarUrl: string
  qqPreviewAvatarUrl: string
  avatarValue: string
  avatarChanged: boolean
  goodsItems: GoodsItem[]
  loading: boolean
  loadingMore: boolean
  saving: boolean
  info: string
  showProfileModal: boolean
  showFeedbackModal: boolean
  page: number
  size: number
  hasMore: boolean
}

Component({
  data: {
    profile: { nickname: '', avatarUrl: '', wechatId: '', qq: '' },
    profileDraft: { nickname: '', avatarUrl: '', wechatId: '', qq: '' },
    displayAvatarUrl: '',
    draftAvatarUrl: '',
    qqPreviewAvatarUrl: '',
    avatarValue: '',
    avatarChanged: false,
    goodsItems: [],
    loading: false,
    loadingMore: false,
    saving: false,
    info: '',
    showProfileModal: false,
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
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || loadFailed('个人资料') })
          return
        }
        const profile = (res.data?.data as unknown as UserProfile) || { nickname: '', avatarUrl: '' }
        this.setData({
          profile,
          profileDraft: { ...profile },
          displayAvatarUrl: resolveProfileDisplayAvatar(profile),
          draftAvatarUrl: '',
          qqPreviewAvatarUrl: '',
          avatarValue: '',
          avatarChanged: false
        })
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
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
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || loadFailed('商品列表') })
          return
        }

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
          this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
        }
      } finally {
        this.setData({ loading: false, loadingMore: false })
      }
    },

    openProfileEditor() {
      const profile = this.data.profile || { nickname: '', avatarUrl: '', wechatId: '', qq: '' }
      this.setData({
        showProfileModal: true,
        profileDraft: { ...profile },
        draftAvatarUrl: resolveProfileDisplayAvatar(profile),
        qqPreviewAvatarUrl: '',
        avatarValue: '',
        avatarChanged: false,
        info: ''
      })
    },

    closeProfileEditor() {
      if (this.data.saving) return
      this.setData({
        showProfileModal: false,
        profileDraft: { ...this.data.profile },
        draftAvatarUrl: '',
        qqPreviewAvatarUrl: '',
        avatarValue: '',
        avatarChanged: false,
        info: ''
      })
    },

    onNicknameInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'profileDraft.nickname': e.detail.value, info: '' })
    },

    onWechatIdInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'profileDraft.wechatId': e.detail.value, info: '' })
    },

    onQqInput(e: WechatMiniprogram.InputEvent) {
      const qq = e.detail.value
      this.setData({
        'profileDraft.qq': qq,
        qqPreviewAvatarUrl: resolveQqAvatarPreview(this.data.profile, qq, this.data.avatarChanged),
        info: ''
      })
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
              'profileDraft.avatarUrl': upload.url,
              draftAvatarUrl: upload.url,
              qqPreviewAvatarUrl: '',
              avatarValue: upload.filename,
              avatarChanged: true,
              info: '头像已上传，保存后生效'
            })
          } catch (err) {
            const msg = err instanceof Error ? err.message : COMMON_MESSAGES.AVATAR_UPLOAD_FAILED
            this.setData({ info: msg })
          } finally {
            wx.hideLoading()
          }
        }
      })
    },

    async saveProfile() {
      const nickname = (this.data.profileDraft.nickname || '').trim()
      if (!nickname) {
        this.setData({ info: '请输入专业昵称' })
        return
      }
      const wechatId = (this.data.profileDraft.wechatId || '').trim()
      const qq = (this.data.profileDraft.qq || '').trim()
      if (qq && !QQ_REGEX.test(qq)) {
        this.setData({ info: 'QQ号需为5-12位数字' })
        return
      }
      if (this.data.saving) return

      this.setData({ saving: true, info: '' })
      try {
        const data: Record<string, unknown> = { nickname, wechatId, qq }
        if (this.data.avatarChanged && this.data.avatarValue) {
          data.avatarUrl = this.data.avatarValue
        }

        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me`,
          method: 'PUT',
          data
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || actionFailed('保存') })
          return
        }
        const profile = (res.data?.data as unknown as UserProfile) || this.data.profile
        const user = JSON.parse(wx.getStorageSync('user') || '{}')
        wx.setStorageSync('user', JSON.stringify({
          ...user,
          nickname: profile.nickname,
          avatarUrl: profile.avatarUrl,
          avatarSource: profile.avatarSource,
          wechatOpenid: profile.wechatOpenid,
          wechatId: profile.wechatId,
          qq: profile.qq
        }))
        this.setData({
          profile,
          profileDraft: { ...profile },
          displayAvatarUrl: resolveProfileDisplayAvatar(profile),
          draftAvatarUrl: '',
          qqPreviewAvatarUrl: '',
          avatarValue: '',
          avatarChanged: false,
          showProfileModal: false,
          info: ''
        })
        wx.showToast({ title: '保存成功', icon: 'success' })
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
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
        const res = await request<ApiResponse>({
          url: `${app.globalData.baseUrl}/goods/${id}/status`,
          method: 'PATCH',
          data: { status: nextStatus }
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || actionFailed('状态更新') })
          return
        }
        wx.showToast({ title: nextStatus === 'ON_SALE' ? '已上架' : '已下架', icon: 'success' })
        this.loadMyGoods(true)
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
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
            const deleteRes = await request<ApiResponse>({
              url: `${app.globalData.baseUrl}/goods/${id}`,
              method: 'DELETE'
            })
            if (!deleteRes.data?.success) {
              this.setData({ info: deleteRes.data?.message || actionFailed('删除') })
              return
            }
            wx.showToast({ title: '已删除', icon: 'success' })
            this.loadMyGoods(true)
          } catch (_err) {
            this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
          }
        }
      })
    },

    onUnload() {
      const hasUnsavedAvatar = this.data.avatarChanged
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
