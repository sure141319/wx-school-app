import { request } from '../../utils/request'

const app = getApp<{ globalData: { baseUrl: string } }>()

interface IndexPageData {
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  goodsItems: GoodsItem[]
  leftGoods: GoodsItem[]
  rightGoods: GoodsItem[]
  statusText: string
  keyword: string
  categoryId: string
  page: number
  size: number
  categoryItems: Category[]
}

Component({
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
  } as IndexPageData,

  methods: {
    onLoad() {
      this.loadData()
    },

    onShow() {
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

    onSearchInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ keyword: e.detail.value })
    },

    onSearch() {
      this.loadGoods(true)
    },

    chooseCategory(e: WechatMiniprogram.TouchEvent) {
      const id = (e.currentTarget.dataset.id as string) || ''
      this.setData({ categoryId: id })
      this.loadGoods(true)
    },

    openGoods(e: WechatMiniprogram.TouchEvent) {
      const id = e.currentTarget.dataset.id
      wx.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    goToPublish() {
      const token = wx.getStorageSync('token')
      if (!token) {
        wx.navigateTo({ url: '/pages/auth/auth?redirect=/pages/publish/publish' })
        return
      }
      wx.switchTab({ url: '/pages/publish/publish' })
    },

    async loadData() {
      await this.loadCategories()
      await this.loadGoods(true)
    },

    async loadCategories() {
      try {
        const res = await request<Category[]>({
          url: `${app.globalData.baseUrl}/categories`,
          method: 'GET'
        })
        const categories = (res.data?.data as unknown as Category[]) || []
        this.setData({
          categoryItems: [{ id: '', name: '推荐' }, ...categories]
        })
      } catch (_err) {
        this.setData({ statusText: '分类加载失败，请稍后重试' })
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
        const params: Record<string, string | number> = {
          status: 'ON_SALE',
          page: nextPage,
          size: this.data.size
        }
        if (this.data.keyword.trim()) params.keyword = this.data.keyword.trim()
        if (this.data.categoryId) params.categoryId = this.data.categoryId

        const res = await request<ApiResponse<PageInfo>>({
          url: `${app.globalData.baseUrl}/goods`,
          method: 'GET',
          data: params
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

        const leftGoods: GoodsItem[] = []
        const rightGoods: GoodsItem[] = []
        allItems.forEach((item, i) => {
          if (i % 2 === 0) {
            leftGoods.push(item)
          } else {
            rightGoods.push(item)
          }
        })

        this.setData({
          goodsItems: allItems,
          leftGoods,
          rightGoods,
          page: nextPage + 1,
          hasMore
        })
      } catch (_err) {
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
  }
})
