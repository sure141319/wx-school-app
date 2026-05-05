const { request } = require('../../utils/request.js')
const { uploadImage } = require('../../utils/upload.js')
const app = getApp()

// 固定选项
const CONDITION_OPTIONS = ['全新', '9成新', '8成新', '7成新', '6成新及以下']
const LOCATION_OPTIONS = ['本部', '东校区']

Page({
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
  },

  onLoad(options) {
    this.setData({ goodsId: options.id || '' })
    this.loadCategories()
    if (options.id) {
      this.loadGoods(options.id)
    }
  },

  onShow() {
    const editId = wx.getStorageSync('editGoodsId')
    if (editId) {
      wx.removeStorageSync('editGoodsId')
      this.setData({ goodsId: editId })
      this.loadGoods(editId)
    }
  },

  async loadCategories() {
    try {
      const res = await request({
        url: `${app.globalData.baseUrl}/categories`,
        method: 'GET'
      })
      const categories = res.data?.data || []
      this.setData({ categories })
    } catch (err) {}
  },

  // 加载商品（编辑功能）
  async loadGoods(id) {
    this.setData({ loading: true })
    try {
      const res = await request({
        url: `${app.globalData.baseUrl}/goods/${id}`,
        method: 'GET'
      })
      const goods = res.data?.data
      if (!goods) return
      this.setData({
        form: {
          title: goods.title || '',
          description: goods.description || '',
          price: goods.price || '',
          conditionLevel: goods.conditionLevel || CONDITION_OPTIONS[1],
          campusLocation: goods.campusLocation || LOCATION_OPTIONS[0],
          categoryId: goods.category?.id || '',
          photos: (goods.imageUrls || []).map((url) => ({
            url,
            filename: url
          }))
        }
      })
    } catch (err) {
      this.setData({ info: '商品信息加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 价格输入
  onPriceInput(e) {
    this.setData({ 'form.price': e.detail.value })
  },

  // 成色选择
  chooseCondition(e) {
    this.setData({ 'form.conditionLevel': e.currentTarget.dataset.value })
  },

  // 地点选择
  chooseLocation(e) {
    this.setData({ 'form.campusLocation': e.currentTarget.dataset.value })
  },

  // 分类选择 → 自动把分类名赋值给 title + description
  chooseCategory(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name || ''
    this.setData({
      'form.categoryId': id || '',
      'form.title': name,
      'form.description': name
    })
  },

  // 图片上传
  chooseImages() {
    const remain = 9 - this.data.form.photos.length
    if (remain <= 0) return
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const files = res.tempFiles || []
        for (const file of files) {
          try {
            const upload = await this.uploadImage(file.tempFilePath)
            const photos = this.data.form.photos.concat({
              url: upload.url,
              filename: upload.filename
            })
            this.setData({ 'form.photos': photos, info: '' })
          } catch (err) {
            this.setData({ info: '图片上传失败' })
            break
          }
        }
      }
    })
  },

  // 删除图片
  removePhoto(e) {
    const index = e.currentTarget.dataset.index
    const photos = this.data.form.photos.slice()
    photos.splice(index, 1)
    this.setData({ 'form.photos': photos })
  },

  // 上传图片接口
  uploadImage(filePath) {
    return uploadImage(filePath)
  },

  // 表单验证
  validateForm() {
    const form = this.data.form
    const errors = {}
    if (!form.photos.length) errors.photos = '请至少上传一张图片'
    if (!form.price || Number(form.price) <= 0) errors.price = '请输入正确价格'
    if (!form.categoryId) errors.categoryId = '请选择商品分类'
    this.setData({ errors })
    return !Object.keys(errors).length
  },

  // 重置表单
  resetForm() {
    this.setData({
      goodsId: '',
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

  // 提交：保留 title/description 适配后端，值为分类名
  async submit() {
    if (this.data.submitting) return
    if (!this.validateForm()) return

    const form = this.data.form
    const payload = {
      title: form.title,
      description: form.description,
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
      wx.showToast({ title: this.data.goodsId ? '更新成功，等待审核' : '提交成功，等待审核', icon: 'success' })
      this.resetForm()
      setTimeout(() => {
        wx.switchTab({ url: '/pages/profile/profile' })
      }, 1500)
    } catch (err) {
      this.setData({ info: '提交失败' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})