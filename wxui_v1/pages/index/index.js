const { request } = require('../../utils/request.js')
const app = getApp()

Page({
  data: {
    loading: false,
    loadingMore: false,
    hasMore: true,
    goodsItems: [],
    leftGoods: [],
    rightGoods: [],
    statusText: '',
    keyword: '',
    categoryId: '',
    page: 0,
    size: 12,
    categoryItems: [{ id: '', name: '推荐' }]
  },

  onLoad() {
    this.loadData()
  },

  onShow() {
    // 从其他页面返回时刷新数据
    this.loadGoods(true)
  },

  onPullDownRefresh() {
    this.loadData().finally(() => {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading && !this.data.loadingMore) {
      this.loadGoods(false, true)
    }
  },

  onSearchInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.loadGoods(true)
  },

  chooseCategory(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ categoryId: id })
    this.loadGoods(true)
  },

  openGoods(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/goods/detail?id=${id}` })
  },

  goToPublish() {
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.navigateTo({ url: '/pages/auth/auth?redirect=/pages/publish/publish' })
      return
    }
    wx.navigateTo({ url: '/pages/publish/publish' })
  },

  async loadData() {
    await this.loadCategories()
    await this.loadGoods(true)
  },

  async loadCategories() {
    try {
      const res = await request({
        url: `${app.globalData.baseUrl}/categories`,
        method: 'GET'
      })
      const categories = res.data?.data || []
      this.setData({
        categoryItems: [{ id: '', name: '推荐' }, ...categories]
      })
    } catch (err) {
      this.setData({ statusText: '分类加载失败' })
    }
  },

  async loadGoods(resetPage = false, append = false) {
    const nextPage = resetPage ? 0 : this.data.page
    
    if (resetPage) {
      this.setData({ loading: true, hasMore: true, statusText: '', page: 0 })
    } else if (append) {
      this.setData({ loadingMore: true })
    } else {
      this.setData({ loading: true })
    }

    try {
      const params = {
        status: 'ON_SALE',
        page: nextPage,
        size: this.data.size
      }
      if (this.data.keyword) params.keyword = this.data.keyword
      if (this.data.categoryId) params.categoryId = this.data.categoryId

      const res = await request({
        url: `${app.globalData.baseUrl}/goods`,
        method: 'GET',
        data: params
      })

      const items = res.data?.data?.items || []
      const total = res.data?.data?.total || 0
      const hasMore = items.length === this.data.size && (nextPage + 1) * this.data.size < total

      const heights = [184, 236, 196, 244, 206, 220]

      let allItems
      if (append && !resetPage) {
        allItems = [...this.data.goodsItems, ...items]
      } else {
        allItems = items
      }

      const leftGoods = allItems.filter((_, i) => i % 2 === 0).map((item, i) => ({
        ...item,
        coverHeight: heights[i % heights.length]
      }))

      const rightGoods = allItems.filter((_, i) => i % 2 === 1).map((item, i) => ({
        ...item,
        coverHeight: heights[i % heights.length]
      }))

      this.setData({ 
        goodsItems: allItems, 
        leftGoods, 
        rightGoods,
        page: nextPage + 1,
        hasMore
      })
    } catch (err) {
      if (!append) {
        this.setData({
          statusText: '商品加载失败，请稍后重试',
          goodsItems: [],
          leftGoods: [],
          rightGoods: []
        })
      }
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  }
})
