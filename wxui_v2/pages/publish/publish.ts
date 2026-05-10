import { request } from '../../utils/request'
import { uploadImage } from '../../utils/upload'

const app = getApp<{ globalData: { baseUrl: string } }>()

const CONDITION_OPTIONS = ['全新', '9成新', '8成新', '7成新', '6成新及以下']
const LOCATION_OPTIONS = ['本部', '东校区']

interface PublishPageData {
  goodsId: string
  loading: boolean
  submitting: boolean
  info: string
  categories: Category[]
  conditionOptions: string[]
  locationOptions: string[]
  form: PublishForm
  errors: Record<string, string>
}

Component({
  data: {
    goodsId: '',
    loading: false,
    submitting: false,
    info: '',
    categories: [],
    conditionOptions: CONDITION_OPTIONS,
    locationOptions: LOCATION_OPTIONS,
    form: {
      title: '',
      description: '',
      price: '',
      conditionLevel: CONDITION_OPTIONS[1],
      campusLocation: LOCATION_OPTIONS[0],
      categoryId: '',
      photos: []
    },
    errors: {}
  } as PublishPageData,

  methods: {
    onLoad(options: Record<string, string | undefined>) {
      if (!wx.getStorageSync('token')) {
        wx.redirectTo({ url: '/pages/auth/auth?redirect=/pages/publish/publish' })
        return
      }
      this.setData({ goodsId: options.id || '' })
      this.loadCategories()
      if (options.id) {
        this.loadGoods(options.id)
      }
    },

    onShow() {
      if (!wx.getStorageSync('token')) {
        wx.redirectTo({ url: '/pages/auth/auth?redirect=/pages/publish/publish' })
        return
      }
      const editId = wx.getStorageSync('editGoodsId')
      if (editId) {
        wx.removeStorageSync('editGoodsId')
        this.setData({ goodsId: editId })
        this.loadGoods(editId)
      }
    },

    async loadCategories() {
      try {
        const res = await request<Category[]>({
          url: `${app.globalData.baseUrl}/categories`,
          method: 'GET'
        })
        const categories = (res.data?.data as unknown as Category[]) || []
        this.setData({ categories })
      } catch (_err) {
        this.setData({ info: '分类加载失败，请重新登录后再试' })
      }
    },

    async loadGoods(id: string) {
      this.setData({ loading: true })
      try {
        const res = await request<ApiResponse<GoodsItem>>({
          url: `${app.globalData.baseUrl}/goods/${id}`,
          method: 'GET'
        })
        const goods = res.data?.data as unknown as GoodsItem | undefined
        if (!goods) return
        this.setData({
          form: {
            title: goods.title || '',
            description: goods.description || '',
            price: String(goods.price || ''),
            conditionLevel: goods.conditionLevel || CONDITION_OPTIONS[1],
            campusLocation: goods.campusLocation || LOCATION_OPTIONS[0],
            categoryId: String(goods.category?.id || ''),
            photos: (goods.imageUrls || []).map((url, i) => ({
              url,
              filename: (goods.imageKeys || [])[i] || url
            }))
          }
        })
      } catch (_err) {
        this.setData({ info: '商品信息加载失败' })
      } finally {
        this.setData({ loading: false })
      }
    },

    onPriceInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'form.price': e.detail.value, 'errors.price': '' })
    },

    onTitleInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'form.title': e.detail.value, 'errors.title': '' })
    },

    onDescriptionInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'form.description': e.detail.value, 'errors.description': '' })
    },

    chooseCondition(e: WechatMiniprogram.TouchEvent) {
      this.setData({ 'form.conditionLevel': e.currentTarget.dataset.value as string })
    },

    chooseLocation(e: WechatMiniprogram.TouchEvent) {
      this.setData({ 'form.campusLocation': e.currentTarget.dataset.value as string })
    },

    chooseCategory(e: WechatMiniprogram.TouchEvent) {
      const id = String(e.currentTarget.dataset.id || '')
      if (this.data.form.categoryId === id) return
      const cat = (this.data.categories as Category[]).find(c => String(c.id) === id)
      const catName = cat?.name || ''
      this.setData({
        'form.categoryId': id,
        'form.title': catName,
        'form.description': catName,
        'errors.categoryId': ''
      })
    },

    chooseImages() {
      const remain = 9 - this.data.form.photos.length
      if (remain <= 0) return
      wx.chooseMedia({
        count: remain,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const files = res.tempFiles || []
          wx.showLoading({ title: '上传中...', mask: true })
          try {
            const results = await Promise.all(
              files.map(file => uploadImage(file.tempFilePath))
            )
            const photos = this.data.form.photos.concat(results)
            this.setData({ 'form.photos': photos, 'errors.photos': '', info: '' })
          } catch (_err) {
            this.setData({ info: '图片上传失败，请稍后重试' })
          } finally {
            wx.hideLoading()
          }
        }
      })
    },

    removePhoto(e: WechatMiniprogram.TouchEvent) {
      const index = e.currentTarget.dataset.index as number
      const photos = this.data.form.photos.slice()
      photos.splice(index, 1)
      this.setData({ 'form.photos': photos })
    },

    validateForm() {
      const form = this.data.form
      const errors: Record<string, string> = {}
      if (!form.photos.length) errors.photos = '请至少上传一张图片'
      const priceNum = Number(form.price)
      if (!form.price || Number.isNaN(priceNum) || priceNum <= 0) errors.price = '请输入正确价格'
      if (!form.categoryId) errors.categoryId = '请选择商品分类'
      if (!form.title || !form.title.trim()) errors.title = '请输入标题'
      if (!form.description || !form.description.trim()) errors.description = '请输入描述'
      this.setData({ errors })
      return !Object.keys(errors).length
    },

    resetForm() {
      this.setData({
        goodsId: '',
        info: '',
        form: {
          title: '',
          description: '',
          price: '',
          conditionLevel: CONDITION_OPTIONS[1],
          campusLocation: LOCATION_OPTIONS[0],
          categoryId: '',
          photos: []
        },
        errors: {}
      })
    },

    async submit() {
      if (this.data.submitting) return
      if (!this.validateForm()) return

      const form = this.data.form
      const payload = {
        title: form.title.trim(),
        description: form.description.trim(),
        price: Number(form.price),
        conditionLevel: form.conditionLevel,
        campusLocation: form.campusLocation,
        categoryId: form.categoryId || null,
        imageUrls: form.photos.map(item => item.filename)
      }

      this.setData({ submitting: true, info: '' })
      try {
        if (this.data.goodsId) {
          await request({
            url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}`,
            method: 'PUT',
            data: payload
          })
        } else {
          await request({
            url: `${app.globalData.baseUrl}/goods`,
            method: 'POST',
            data: payload
          })
        }
        wx.showToast({
          title: this.data.goodsId ? '已更新，等待审核' : '已提交，等待审核',
          icon: 'success'
        })
        this.resetForm()
        setTimeout(() => {
          wx.switchTab({ url: '/pages/profile/profile' })
        }, 1200)
      } catch (_err) {
        this.setData({ info: '提交失败，请稍后重试' })
      } finally {
        this.setData({ submitting: false })
      }
    }
  }
})
