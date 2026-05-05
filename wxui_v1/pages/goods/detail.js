const { request } = require('../../utils/request.js')
const app = getApp()

Page({
  data: {
    goods: null,
    loading: false,
    info: '',
    goodsId: '',
    currentImgIndex: 0
  },

  onLoad(options) {
    this.setData({ goodsId: options.id })
    this.loadGoods()
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' })
  },

  onSwiperChange(e) {
    this.setData({ currentImgIndex: e.detail.current })
  },

  async loadGoods() {
    this.setData({ loading: true, info: '' })
    try {
      const res = await request({
        url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}`,
        method: 'GET'
      })
      const goods = res.data?.data

      // 从 seller.email 提取 QQ 号
      if (goods?.seller?.email) {
        const email = goods.seller.email
        if (email.endsWith('@qq.com')) {
          goods.seller.qq = email.replace('@qq.com', '')
        }
      }

      this.setData({ goods })
    } catch (err) {
      this.setData({ info: '商品详情加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  previewImage(e) {
    if (!this.data.goods?.imageUrls?.length) return
    const index = e.currentTarget.dataset.index || 0
    const PLACEHOLDER = '/static/auditing.webp'
    const realImages = this.data.goods.imageUrls.filter(url => url !== PLACEHOLDER)
    if (!realImages.length) return
    wx.previewImage({
      urls: realImages,
      current: realImages[Math.min(index, realImages.length - 1)]
    })
  },

  copyQQ(e) {
    const qq = e.currentTarget.dataset.qq
    if (!qq) return
    wx.setClipboardData({
      data: qq,
      success() {
        wx.showToast({ title: 'QQ号已复制', icon: 'success' })
      }
    })
  }
})
