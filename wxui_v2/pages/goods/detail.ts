import { request } from '../../utils/request'
import { COMMON_MESSAGES, loadFailed } from '../../utils/messages'

const app = getApp<{ globalData: { baseUrl: string } }>()
const CONTACT_EMAIL_AD_UNIT_ID = 'adunit-f3d20d1b06422a8d'
const CONTACT_EMAIL_AD_REWARD_STORAGE_KEY = 'contactEmailAdReward'
const CONTACT_EMAIL_AD_REWARD_TTL = 24 * 60 * 60 * 1000

let contactEmailVideoAd: WechatMiniprogram.RewardedVideoAd | null = null

interface ContactEmailAdReward {
  userId: string
  validUntil: number
}

interface DetailPageData {
  goods: GoodsItem | null
  loading: boolean
  info: string
  goodsId: string
  currentImgIndex: number
  displayCreatedAt: string
  statusBarHeight: number
  navContentHeight: number
  navBarHeight: number
  checkingContactEmail: boolean
  sendingContactEmail: boolean
  contactAdShowing: boolean
  isOwnGoods: boolean
}

Component({
  data: {
    goods: null,
    loading: false,
    info: '',
    goodsId: '',
    currentImgIndex: 0,
    displayCreatedAt: '',
    statusBarHeight: 20,
    navContentHeight: 44,
    navBarHeight: 64,
    checkingContactEmail: false,
    sendingContactEmail: false,
    contactAdShowing: false,
    isOwnGoods: false
  } as DetailPageData,

  methods: {
    onLoad(options: Record<string, string | undefined>) {
      this.initNavigation()
      this.initContactEmailVideoAd()
      if (!options.id) {
        this.setData({ info: '商品不存在或已下架' })
        return
      }
      this.setData({ goodsId: options.id })
      this.loadGoods()
    },

    onUnload() {
      this.destroyContactEmailVideoAd()
    },

    initContactEmailVideoAd() {
      if (contactEmailVideoAd || typeof wx.createRewardedVideoAd !== 'function') return

      contactEmailVideoAd = wx.createRewardedVideoAd({
        adUnitId: CONTACT_EMAIL_AD_UNIT_ID
      })
      contactEmailVideoAd.onLoad(() => {})
      contactEmailVideoAd.onError((err) => {
        console.error('联系卖家激励视频广告加载失败', err)
        this.setData({ contactAdShowing: false })
      })
      contactEmailVideoAd.onClose((res) => {
        this.setData({ contactAdShowing: false })
        if (!res || res.isEnded) {
          this.saveContactEmailAdReward()
          this.sendContactEmail()
          return
        }
        wx.showToast({ title: '看完广告后才能发送', icon: 'none' })
      })
    },

    destroyContactEmailVideoAd() {
      if (!contactEmailVideoAd) return
      contactEmailVideoAd.offLoad()
      contactEmailVideoAd.offError()
      contactEmailVideoAd.offClose()
      contactEmailVideoAd.destroy()
      contactEmailVideoAd = null
    },

    goHome() {
      wx.switchTab({ url: '/pages/index/index' })
    },

    goBack() {
      if (getCurrentPages().length > 1) {
        wx.navigateBack({ delta: 1 })
        return
      }
      this.goHome()
    },

    initNavigation() {
      const systemInfo = wx.getSystemInfoSync()
      const statusBarHeight = systemInfo.statusBarHeight || 20
      let navContentHeight = 44

      try {
        const menuButton = wx.getMenuButtonBoundingClientRect()
        if (menuButton && menuButton.height) {
          navContentHeight = (menuButton.top - statusBarHeight) * 2 + menuButton.height
        }
      } catch (_err) {
        navContentHeight = 44
      }

      this.setData({
        statusBarHeight,
        navContentHeight,
        navBarHeight: statusBarHeight + navContentHeight
      })
    },

    onSwiperChange(e: WechatMiniprogram.SwiperChangeEvent) {
      this.setData({ currentImgIndex: e.detail.current })
    },

    async loadGoods() {
      this.setData({ loading: true, info: '', displayCreatedAt: '' })
      try {
        const res = await request<ApiResponse<GoodsItem>>({
          url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}`,
          method: 'GET'
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || loadFailed('商品详情') })
          return
        }
        const goods = res.data?.data as unknown as GoodsItem | undefined

        if (goods && !goods.seller) {
          goods.seller = {
            nickname: goods.sellerName || '同校卖家',
            avatarUrl: goods.sellerAvatar || ''
          }
        }
        if (goods && !goods.imageUrls) {
          goods.imageUrls = []
        }

        if (goods?.seller?.email?.endsWith('@qq.com')) {
          goods.seller.qq = goods.seller.email.replace('@qq.com', '')
        }

        this.setData({
          goods: goods || null,
          displayCreatedAt: this.formatCreatedAt(goods?.createdAt),
          isOwnGoods: this.isCurrentUsersGoods(goods)
        })
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ loading: false })
      }
    },

    formatCreatedAt(createdAt?: string) {
      if (!createdAt) return ''
      const normalized = createdAt.replace('T', ' ').slice(0, 16)
      if (!normalized.trim()) return ''

      const date = new Date(createdAt)
      if (Number.isNaN(date.getTime())) {
        return normalized
      }

      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hour = String(date.getHours()).padStart(2, '0')
      const minute = String(date.getMinutes()).padStart(2, '0')
      return `${month}-${day} ${hour}:${minute}`
    },

    previewImage(e: WechatMiniprogram.TouchEvent) {
      if (!this.data.goods?.imageUrls?.length) return
      const index = (e.currentTarget.dataset.index as number) || 0
      wx.previewImage({
        urls: this.data.goods.imageUrls,
        current: this.data.goods.imageUrls[Math.min(index, this.data.goods.imageUrls.length - 1)]
      })
    },

    copyQQ(e: WechatMiniprogram.TouchEvent) {
      const qq = e.currentTarget.dataset.qq as string
      if (!qq) return
      wx.setClipboardData({
        data: qq,
        success() {
          wx.showToast({ title: 'QQ 号已复制', icon: 'success' })
        }
      })
    },

    copyWechatId(e: WechatMiniprogram.TouchEvent) {
      const wechatId = e.currentTarget.dataset.wechatId as string
      if (!wechatId) return
      wx.setClipboardData({
        data: wechatId,
        success() {
          wx.showToast({ title: '微信号已复制', icon: 'success' })
        }
      })
    },

    isCurrentUsersGoods(goods?: GoodsItem): boolean {
      const userId = this.getCurrentUserId()
      const sellerId = goods && goods.seller && goods.seller.id
      return Boolean(userId && sellerId !== undefined && sellerId !== null && userId === String(sellerId))
    },

    goMyGoods() {
      wx.switchTab({ url: '/pages/profile/profile' })
    },

    async contactSellerByEmail() {
      if (this.data.checkingContactEmail || this.data.sendingContactEmail || this.data.contactAdShowing) return
      if (this.data.isOwnGoods) {
        this.goMyGoods()
        return
      }
      if (!wx.getStorageSync('token')) {
        this.showLoginRequired()
        return
      }

      this.setData({ checkingContactEmail: true })
      try {
        const res = await request<ApiResponse<ContactEmailEligibility>>({
          url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}/contact-email-eligibility`,
          method: 'GET'
        })
        if (!res.data || !res.data.success || !res.data.data) {
          wx.showToast({ title: (res.data && res.data.message) || '暂时无法检查邮箱状态', icon: 'none' })
          return
        }

        const eligibility = res.data.data
        if (eligibility.ownGoods) {
          this.setData({ isOwnGoods: true })
          wx.showToast({ title: '这是你发布的商品', icon: 'none' })
          return
        }
        if (!eligibility.buyerEmailBound) {
          this.showBuyerEmailRequired()
          return
        }
        if (!eligibility.sellerEmailBound) {
          wx.showModal({
            title: '无法发送',
            content: '卖家未绑定邮箱，无法发送',
            showCancel: false,
            confirmText: '知道了'
          })
          return
        }

        if (this.hasValidContactEmailAdReward()) {
          this.sendContactEmail()
          return
        }
        this.confirmWatchAdAndSend()
      } catch (_err) {
        wx.showToast({ title: COMMON_MESSAGES.NETWORK_ERROR, icon: 'none' })
      } finally {
        this.setData({ checkingContactEmail: false })
      }
    },

    showLoginRequired() {
      wx.showModal({
        title: '请先登录',
        content: '登录并绑定邮箱后，才能一键通知卖家。',
        confirmText: '去登录',
        success: (res) => {
          if (!res.confirm) return
          const redirect = encodeURIComponent(`/pages/goods/detail?id=${this.data.goodsId}`)
          wx.navigateTo({ url: `/pages/auth/auth?redirect=${redirect}` })
        }
      })
    },

    showBuyerEmailRequired() {
      wx.showModal({
        title: '请绑定邮箱',
        content: '请先到「我的 - 绑定账号」绑定QQ邮箱，再联系卖家。',
        confirmText: '去绑定',
        success: (res) => {
          if (!res.confirm) return
          wx.setStorageSync('openAccountBindModal', true)
          wx.setStorageSync('openEmailBindForm', true)
          wx.switchTab({ url: '/pages/profile/profile' })
        }
      })
    },

    confirmWatchAdAndSend() {
      wx.showModal({
        title: '一键发邮箱',
        content: '观看一次广告即可自动发送邮件通知卖家。看完后24小时内再次发送无需观看广告。',
        confirmText: '观看并发送',
        success: (res) => {
          if (res.confirm) this.showContactEmailVideoAd()
        }
      })
    },

    showContactEmailVideoAd() {
      if (typeof wx.createRewardedVideoAd !== 'function') {
        wx.showModal({
          title: '暂不支持',
          content: '当前微信版本暂不支持激励视频广告，请升级微信后再试。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }

      this.initContactEmailVideoAd()
      if (!contactEmailVideoAd) {
        wx.showToast({ title: '广告暂不可用', icon: 'none' })
        return
      }

      this.setData({ contactAdShowing: true })
      const videoAd = contactEmailVideoAd
      videoAd.show().catch(() => {
        videoAd.load()
          .then(() => videoAd.show())
          .catch((err) => {
            console.error('联系卖家激励视频广告显示失败', err)
            this.setData({ contactAdShowing: false })
            wx.showToast({ title: '广告暂时无法播放', icon: 'none' })
          })
      })
    },

    getCurrentUserId(): string {
      try {
        const user = JSON.parse(wx.getStorageSync('user') || '{}') as UserProfile
        return user.id === undefined || user.id === null ? '' : String(user.id)
      } catch (_err) {
        return ''
      }
    },

    hasValidContactEmailAdReward(): boolean {
      const userId = this.getCurrentUserId()
      if (!userId) return false
      const reward = wx.getStorageSync(CONTACT_EMAIL_AD_REWARD_STORAGE_KEY) as ContactEmailAdReward | undefined
      return Boolean(reward && reward.userId === userId && Number(reward.validUntil) > Date.now())
    },

    saveContactEmailAdReward() {
      const userId = this.getCurrentUserId()
      if (!userId) return
      wx.setStorageSync(CONTACT_EMAIL_AD_REWARD_STORAGE_KEY, {
        userId,
        validUntil: Date.now() + CONTACT_EMAIL_AD_REWARD_TTL
      } as ContactEmailAdReward)
    },

    async sendContactEmail() {
      if (this.data.sendingContactEmail) return
      this.setData({ sendingContactEmail: true })
      wx.showLoading({ title: '正在发送...', mask: true })
      let toastTitle = ''
      let sendSucceeded = false
      try {
        const res = await request<ApiResponse>({
          url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}/contact-email`,
          method: 'POST'
        })
        if (!res.data || !res.data.success) {
          toastTitle = (res.data && res.data.message) || '邮件发送失败'
        } else {
          toastTitle = '已通知卖家'
          sendSucceeded = true
        }
      } catch (_err) {
        toastTitle = COMMON_MESSAGES.NETWORK_ERROR
      } finally {
        wx.hideLoading()
        this.setData({ sendingContactEmail: false })
      }
      wx.showToast({ title: toastTitle || '邮件发送失败', icon: sendSucceeded ? 'success' : 'none' })
    }
  }
})
