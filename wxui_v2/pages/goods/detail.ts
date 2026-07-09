import { request } from '../../utils/request'
import { COMMON_MESSAGES, loadFailed } from '../../utils/messages'

const app = getApp<{ globalData: { baseUrl: string } }>()

interface DetailPageData {
  goods: GoodsItem | null
  loading: boolean
  info: string
  goodsId: string
  currentImgIndex: number
  displayCreatedAt: string
}

Component({
  data: {
    goods: null,
    loading: false,
    info: '',
    goodsId: '',
    currentImgIndex: 0,
    displayCreatedAt: ''
  } as DetailPageData,

  methods: {
    onLoad(options: Record<string, string | undefined>) {
      if (!options.id) {
        this.setData({ info: '商品不存在或已下架' })
        return
      }
      this.setData({ goodsId: options.id })
      this.loadGoods()
    },

    goHome() {
      wx.switchTab({ url: '/pages/index/index' })
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
          displayCreatedAt: this.formatCreatedAt(goods?.createdAt)
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
    }
  }
})
