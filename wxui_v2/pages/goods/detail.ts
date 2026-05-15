import { request } from '../../utils/request'

const app = getApp<{ globalData: { baseUrl: string } }>()
const PLACEHOLDER_IMAGE = '/static/auditing.webp'

interface DetailPageData {
  goods: GoodsItem | null
  loading: boolean
  info: string
  goodsId: string
  currentImgIndex: number
}

Component({
  data: {
    goods: null,
    loading: false,
    info: '',
    goodsId: '',
    currentImgIndex: 0
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
      this.setData({ loading: true, info: '' })
      try {
        const res = await request<ApiResponse<GoodsItem>>({
          url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}`,
          method: 'GET'
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || '商品详情加载失败' })
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

        this.setData({ goods: goods || null })
      } catch (_err) {
        this.setData({ info: '商品详情加载失败，请检查网络连接' })
      } finally {
        this.setData({ loading: false })
      }
    },

    previewImage(e: WechatMiniprogram.TouchEvent) {
      if (!this.data.goods?.imageUrls?.length) return
      const index = (e.currentTarget.dataset.index as number) || 0
      const realImages = this.data.goods.imageUrls.filter(url => url !== PLACEHOLDER_IMAGE)
      if (!realImages.length) return
      wx.previewImage({
        urls: realImages,
        current: realImages[Math.min(index, realImages.length - 1)]
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
    }
  }
})
