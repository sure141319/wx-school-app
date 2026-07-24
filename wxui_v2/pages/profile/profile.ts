import { clearToken, request } from '../../utils/request'
import { deleteStagedImage, uploadImage } from '../../utils/upload'
import { resolveProfileDisplayAvatar, resolveQqAvatarPreview } from '../../utils/avatar'
import { COMMON_MESSAGES, actionFailed, loadFailed } from '../../utils/messages'

const app = getApp<{ globalData: { baseUrl: string } }>()
const QQ_REGEX = /^\d{5,12}$/
const QQ_EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@qq\.com$/
const SUPPORT_AUTHOR_AD_UNIT_ID = 'adunit-f3d20d1b06422a8d'
const PROFILE_DATA_DIRTY_KEY = 'profileDataDirty'
const GOODS_LIST_DIRTY_KEY = 'goodsListDirty'
const PROFILE_CACHE_TTL_MS = 2 * 60 * 1000
const CAMPUS_MINIPROGRAM_CODE_IMAGES = [
  '/static/ahut-campus-miniprogram-code.jpg',
  '/static/ahut-other-miniprogram-code.jpg'
]

let supportAuthorVideoAd: WechatMiniprogram.RewardedVideoAd | null = null

interface ProfilePageData {
  profile: UserProfile
  profileDraft: UserProfile
  displayAvatarUrl: string
  draftAvatarUrl: string
  qqPreviewAvatarUrl: string
  avatarValue: string
  avatarChanged: boolean
  goodsItems: MyGoodsListItem[]
  loading: boolean
  loadingMore: boolean
  saving: boolean
  bindingWechat: boolean
  unbindingWechat: boolean
  sendingBindEmailCode: boolean
  bindingEmail: boolean
  unbindingEmail: boolean
  info: string
  accountBindMessage: string
  accountMergeHint: boolean
  showProfileModal: boolean
  showAccountBindModal: boolean
  showBindEmailForm: boolean
  showFeedbackModal: boolean
  showCampusOtherModal: boolean
  bindEmailForm: {
    email: string
    code: string
    password: string
  }
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
    bindingWechat: false,
    unbindingWechat: false,
    sendingBindEmailCode: false,
    bindingEmail: false,
    unbindingEmail: false,
    info: '',
    accountBindMessage: '',
    accountMergeHint: false,
    showProfileModal: false,
    showAccountBindModal: false,
    showBindEmailForm: false,
    showFeedbackModal: false,
    showCampusOtherModal: false,
    bindEmailForm: {
      email: '',
      code: '',
      password: ''
    },
    page: 0,
    size: 20,
    hasMore: true
  } as ProfilePageData,

  methods: {
    onLoad() {
      this.initSupportAuthorVideoAd()
    },

    onShow() {
      const token = wx.getStorageSync('token')
      if (!token) {
        wx.redirectTo({ url: '/pages/auth/auth?redirect=/pages/profile/profile' })
        return
      }
      const shouldOpenAccountBindModal = Boolean(wx.getStorageSync('openAccountBindModal'))
      const shouldOpenEmailBindForm = Boolean(wx.getStorageSync('openEmailBindForm'))
      if (shouldOpenAccountBindModal) {
        wx.removeStorageSync('openAccountBindModal')
      }
      if (shouldOpenEmailBindForm) {
        wx.removeStorageSync('openEmailBindForm')
      }
      const now = Date.now()
      const dirty = Boolean(wx.getStorageSync(PROFILE_DATA_DIRTY_KEY))
      const userChanged = (this as any)._loadedToken !== token
      if (dirty) {
        wx.removeStorageSync(PROFILE_DATA_DIRTY_KEY)
      }
      const shouldLoadProfile = dirty
        || userChanged
        || shouldOpenAccountBindModal
        || now - ((this as any)._lastProfileLoadTime || 0) > PROFILE_CACHE_TTL_MS
      const shouldLoadGoods = dirty
        || userChanged
        || now - ((this as any)._lastGoodsLoadTime || 0) > PROFILE_CACHE_TTL_MS
      ;(this as any)._loadedToken = token

      const profileLoad = shouldLoadProfile ? this.loadProfile() : Promise.resolve()
      profileLoad.then(() => {
        if (shouldOpenAccountBindModal) {
          this.openAccountBindModal()
          if (shouldOpenEmailBindForm && !this.data.profile.email) {
            this.setData({ showBindEmailForm: true })
          }
        }
      })
      if (shouldLoadGoods) {
        this.loadMyGoods(true)
      }
    },

    onReachBottom() {
      if (this.data.hasMore && !this.data.loading && !this.data.loadingMore) {
        this.loadMyGoods(false, true)
      }
    },

    initSupportAuthorVideoAd() {
      if (supportAuthorVideoAd || typeof wx.createRewardedVideoAd !== 'function') return

      supportAuthorVideoAd = wx.createRewardedVideoAd({
        adUnitId: SUPPORT_AUTHOR_AD_UNIT_ID
      })
      supportAuthorVideoAd.onLoad(() => {})
      supportAuthorVideoAd.onError((err) => {
        console.error('激励视频广告加载失败', err)
      })
      supportAuthorVideoAd.onClose((res) => {
        if (!res || res.isEnded) {
          wx.showToast({ title: '感谢支持作者', icon: 'success' })
          return
        }
        wx.showToast({ title: '广告未完整观看', icon: 'none' })
      })
    },

    destroySupportAuthorVideoAd() {
      if (!supportAuthorVideoAd) return
      supportAuthorVideoAd.offLoad()
      supportAuthorVideoAd.offError()
      supportAuthorVideoAd.offClose()
      supportAuthorVideoAd.destroy()
      supportAuthorVideoAd = null
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
        ;(this as any)._lastProfileLoadTime = Date.now()
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
        const items = (pageData?.items || []) as unknown as MyGoodsListItem[]
        const total = pageData?.total || 0
        const hasMore = items.length === this.data.size && (nextPage + 1) * this.data.size < total

        const allItems: MyGoodsListItem[] = (append && !resetPage)
          ? [...this.data.goodsItems, ...items]
          : items

        this.setData({
          goodsItems: allItems,
          page: nextPage + 1,
          hasMore
        })
        if (resetPage) {
          ;(this as any)._lastGoodsLoadTime = Date.now()
        }
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
      const stagedAvatar = this.data.avatarChanged ? this.data.avatarValue : ''
      this.setData({
        showProfileModal: false,
        profileDraft: { ...this.data.profile },
        draftAvatarUrl: '',
        qqPreviewAvatarUrl: '',
        avatarValue: '',
        avatarChanged: false,
        info: ''
      })
      if (stagedAvatar) {
        deleteStagedImage(stagedAvatar).catch(() => undefined)
      }
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
        sizeType: ['compressed'],
        success: async (res) => {
          const filePath = res.tempFiles?.[0]?.tempFilePath
          if (!filePath) return
          wx.showLoading({ title: '上传头像...', mask: true })
          try {
            const previousStagedAvatar = this.data.avatarChanged ? this.data.avatarValue : ''
            const upload = await uploadImage(filePath, 'avatar')
            this.setData({
              'profileDraft.avatarUrl': upload.url,
              draftAvatarUrl: upload.url,
              qqPreviewAvatarUrl: '',
              avatarValue: upload.filename,
              avatarChanged: true,
              info: '头像已上传，保存后生效'
            })
            if (previousStagedAvatar && previousStagedAvatar !== upload.filename) {
              deleteStagedImage(previousStagedAvatar).catch(() => undefined)
            }
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

    discardDraftAvatar() {
      if (!this.data.avatarChanged || !this.data.avatarValue) return
      deleteStagedImage(this.data.avatarValue).catch(() => undefined)
    },

    openAccountBindModal() {
      this.setData({
        showAccountBindModal: true,
        showBindEmailForm: false,
        accountBindMessage: '',
        accountMergeHint: false,
        bindEmailForm: {
          email: this.data.profile.email || '',
          code: '',
          password: ''
        },
        info: ''
      })
    },

    closeAccountBindModal() {
      if (this.data.bindingWechat || this.data.unbindingWechat || this.data.sendingBindEmailCode || this.data.bindingEmail || this.data.unbindingEmail) return
      this.setData({
        showAccountBindModal: false,
        showBindEmailForm: false,
        accountBindMessage: '',
        accountMergeHint: false,
        bindEmailForm: {
          email: '',
          code: '',
          password: ''
        }
      })
    },

    onBindEmailInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'bindEmailForm.email': e.detail.value, accountBindMessage: '', accountMergeHint: false })
    },

    onBindEmailCodeInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'bindEmailForm.code': e.detail.value, accountBindMessage: '', accountMergeHint: false })
    },

    onBindEmailPasswordInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'bindEmailForm.password': e.detail.value, accountBindMessage: '', accountMergeHint: false })
    },

    bindWechat() {
      if (this.data.bindingWechat || this.data.saving || this.data.profile.wechatOpenid) return
      this.setData({ bindingWechat: true, info: '' })
      this.getWechatLoginCode()
        .then((code) => request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me/wechat-bind`,
          method: 'POST',
          data: { code }
        }))
        .then((res) => {
          if (!res.data?.success || !res.data?.data) {
            this.setData({ accountBindMessage: res.data?.message || actionFailed('绑定微信') })
            return
          }
          const profile = res.data.data as UserProfile
          this.updateStoredProfile(profile)
          this.setData({
            profile,
            profileDraft: { ...profile },
            displayAvatarUrl: resolveProfileDisplayAvatar(profile),
            draftAvatarUrl: resolveProfileDisplayAvatar(profile),
            accountBindMessage: '',
            accountMergeHint: false
          })
          wx.showToast({ title: '绑定成功', icon: 'success' })
        })
        .catch(() => {
          this.setData({ accountBindMessage: COMMON_MESSAGES.NETWORK_ERROR })
        })
        .finally(() => {
          this.setData({ bindingWechat: false })
        })
    },

    confirmUnbindWechat() {
      if (this.data.unbindingWechat || !this.data.profile.wechatOpenid) return
      if (!this.data.profile.email) {
        wx.showModal({
          title: '暂不可解绑',
          content: '请先绑定QQ邮箱，确保账号仍可登录后再解绑微信。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }

      wx.showModal({
        title: '解绑微信登录？',
        content: '解绑后将不能再用当前微信快捷登录，但仍可使用QQ邮箱和密码登录。',
        confirmText: '确认解绑',
        confirmColor: '#D92D20',
        success: (res) => {
          if (res.confirm) this.unbindWechat()
        }
      })
    },

    toggleBindEmailForm() {
      if (this.data.profile.email || this.data.sendingBindEmailCode || this.data.bindingEmail) return
      this.setData({
        showBindEmailForm: !this.data.showBindEmailForm,
        accountBindMessage: '',
        accountMergeHint: false
      })
    },

    async unbindWechat() {
      this.setData({ unbindingWechat: true, accountBindMessage: '' })
      try {
        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me/wechat-bind`,
          method: 'DELETE'
        })
        if (!res.data || !res.data.success || !res.data.data) {
          this.setData({ accountBindMessage: (res.data && res.data.message) || actionFailed('解绑微信') })
          return
        }
        const profile = res.data.data
        this.updateStoredProfile(profile)
        this.setData({
          profile,
          profileDraft: { ...profile },
          accountBindMessage: ''
        })
        wx.showToast({ title: '微信已解绑', icon: 'success' })
      } catch (_err) {
        this.setData({ accountBindMessage: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ unbindingWechat: false })
      }
    },

    sendBindEmailCode() {
      if (this.data.sendingBindEmailCode || this.data.profile.email) return
      const email = (this.data.bindEmailForm.email || '').trim().toLowerCase()
      if (!QQ_EMAIL_REGEX.test(email)) {
        this.setData({ accountBindMessage: '请输入正确的QQ邮箱', accountMergeHint: false })
        return
      }

      this.setData({ sendingBindEmailCode: true, accountBindMessage: '', accountMergeHint: false })
      request<ApiResponse>({
        url: `${app.globalData.baseUrl}/auth/email-code`,
        method: 'POST',
        data: { email, purpose: 'BIND_EMAIL' }
      })
        .then((res) => {
          if (!res.data?.success) {
            this.setData({
              accountBindMessage: res.data?.message || actionFailed('发送验证码'),
              accountMergeHint: this.isAccountMergeMessage(res.data?.message)
            })
            return
          }
          this.setData({ 'bindEmailForm.email': email, accountBindMessage: '验证码已发送', accountMergeHint: false })
        })
        .catch(() => {
          this.setData({ accountBindMessage: COMMON_MESSAGES.NETWORK_ERROR, accountMergeHint: false })
        })
        .finally(() => {
          this.setData({ sendingBindEmailCode: false })
        })
    },

    bindEmail() {
      if (this.data.bindingEmail || this.data.profile.email) return
      const email = (this.data.bindEmailForm.email || '').trim().toLowerCase()
      const code = (this.data.bindEmailForm.code || '').trim()
      const password = this.data.bindEmailForm.password || ''
      if (!QQ_EMAIL_REGEX.test(email)) {
        this.setData({ accountBindMessage: '请输入正确的QQ邮箱', accountMergeHint: false })
        return
      }
      if (!/^\d{6}$/.test(code)) {
        this.setData({ accountBindMessage: '请输入6位验证码', accountMergeHint: false })
        return
      }
      if (password.length < 6 || password.length > 64) {
        this.setData({ accountBindMessage: '密码需为6-64位', accountMergeHint: false })
        return
      }

      this.setData({ bindingEmail: true, accountBindMessage: '', accountMergeHint: false })
      request<ApiResponse<UserProfile>>({
        url: `${app.globalData.baseUrl}/users/me/email-bind`,
        method: 'POST',
        data: { email, code, password }
      })
        .then((res) => {
          if (!res.data?.success || !res.data?.data) {
            this.setData({
              accountBindMessage: res.data?.message || actionFailed('绑定邮箱'),
              accountMergeHint: this.isAccountMergeMessage(res.data?.message)
            })
            return
          }
          const profile = res.data.data as UserProfile
          this.updateStoredProfile(profile)
          this.setData({
            profile,
            profileDraft: { ...profile },
            bindEmailForm: { email: profile.email || email, code: '', password: '' },
            showBindEmailForm: false,
            accountBindMessage: '',
            accountMergeHint: false
          })
          wx.showToast({ title: '邮箱已绑定', icon: 'success' })
        })
        .catch(() => {
          this.setData({ accountBindMessage: COMMON_MESSAGES.NETWORK_ERROR, accountMergeHint: false })
        })
        .finally(() => {
          this.setData({ bindingEmail: false })
        })
    },

    confirmUnbindEmail() {
      if (this.data.unbindingEmail || !this.data.profile.email) return
      if (!this.data.profile.wechatOpenid) {
        wx.showModal({
          title: '暂不可解绑',
          content: '请先绑定微信，确保账号仍可登录后再解绑邮箱。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }

      wx.showModal({
        title: '解绑QQ邮箱？',
        content: '解绑后邮箱和原密码将不能再登录，但仍可使用当前微信快捷登录。',
        confirmText: '确认解绑',
        confirmColor: '#D92D20',
        success: (res) => {
          if (res.confirm) this.unbindEmail()
        }
      })
    },

    async unbindEmail() {
      this.setData({ unbindingEmail: true, accountBindMessage: '' })
      try {
        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me/email-bind`,
          method: 'DELETE'
        })
        if (!res.data || !res.data.success || !res.data.data) {
          this.setData({ accountBindMessage: (res.data && res.data.message) || actionFailed('解绑邮箱') })
          return
        }
        const profile = res.data.data
        this.updateStoredProfile(profile)
        this.setData({
          profile,
          profileDraft: { ...profile },
          bindEmailForm: { email: '', code: '', password: '' },
          showBindEmailForm: false,
          accountBindMessage: ''
        })
        wx.showToast({ title: '邮箱已解绑', icon: 'success' })
      } catch (_err) {
        this.setData({ accountBindMessage: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ unbindingEmail: false })
      }
    },

    isAccountMergeMessage(message?: string) {
      return Boolean(message && (message.includes('已注册') || message.includes('账号合并')))
    },

    updateStoredProfile(profile: UserProfile) {
      const user = JSON.parse(wx.getStorageSync('user') || '{}')
      wx.setStorageSync('user', JSON.stringify({
        ...user,
        email: profile.email,
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl,
        avatarSource: profile.avatarSource,
        wechatOpenid: profile.wechatOpenid,
        wechatId: profile.wechatId,
        qq: profile.qq
      }))
    },

    getWechatLoginCode(): Promise<string> {
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
        wx.setStorageSync(GOODS_LIST_DIRTY_KEY, true)
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
            wx.setStorageSync(GOODS_LIST_DIRTY_KEY, true)
            this.loadMyGoods(true)
          } catch (_err) {
            this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
          }
        }
      })
    },

    onUnload() {
      this.destroySupportAuthorVideoAd()
      this.discardDraftAvatar()
    },

    logout() {
      clearToken()
      wx.reLaunch({ url: '/pages/index/index' })
    },

    showSupportAuthor() {
      if (typeof wx.createRewardedVideoAd !== 'function') {
        wx.showModal({
          title: '暂不支持',
          content: '当前微信版本暂不支持激励视频广告，请升级微信后再试。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }

      this.initSupportAuthorVideoAd()
      if (!supportAuthorVideoAd) {
        wx.showToast({ title: '广告暂不可用', icon: 'none' })
        return
      }

      const videoAd = supportAuthorVideoAd
      videoAd.show().catch(() => {
        videoAd.load()
          .then(() => videoAd.show())
          .catch((err) => {
            console.error('激励视频广告显示失败', err)
            wx.showToast({ title: '广告暂时无法播放', icon: 'none' })
          })
      })
    },

    showFeedback() {
      this.setData({ showFeedbackModal: true })
    },

    closeFeedback() {
      this.setData({ showFeedbackModal: false })
    },

    showCampusOther() {
      this.setData({ showCampusOtherModal: true })
    },

    closeCampusOther() {
      this.setData({ showCampusOtherModal: false })
    },

    previewCampusOtherCode(e: WechatMiniprogram.TouchEvent) {
      const current = (e.currentTarget.dataset.src as string) || CAMPUS_MINIPROGRAM_CODE_IMAGES[0]
      wx.previewImage({
        urls: CAMPUS_MINIPROGRAM_CODE_IMAGES,
        current
      })
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
