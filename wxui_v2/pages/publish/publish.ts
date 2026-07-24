import { getToken, redirectToLogin, request } from '../../utils/request'
import { deleteStagedImage, uploadImage } from '../../utils/upload'
import { hasContactMethod, validateContactDraft } from '../../utils/contact'
import { COMMON_MESSAGES, actionFailed, loadFailed } from '../../utils/messages'

const app = getApp<{ globalData: { baseUrl: string } }>()

const CONDITION_OPTIONS = ['全新', '9成新', '8成新', '7成新', '6成新及以下']
const LOCATION_OPTIONS = ['本部', '东校区']
const PRICE_INPUT_MIN_WIDTH = 104
const PRICE_INPUT_MAX_WIDTH = 248
const PRICE_GLYPH_WIDTH = 27
const PROFILE_DATA_DIRTY_KEY = 'profileDataDirty'
const GOODS_LIST_DIRTY_KEY = 'goodsListDirty'

function getPriceInputWidth(value: string) {
  const displayValue = value || '0.00'
  return Math.min(
    PRICE_INPUT_MAX_WIDTH,
    Math.max(PRICE_INPUT_MIN_WIDTH, displayValue.length * PRICE_GLYPH_WIDTH)
  )
}

interface PublishPageData {
  goodsId: string
  loading: boolean
  submitting: boolean
  savingContact: boolean
  info: string
  contactError: string
  showContactModal: boolean
  userProfile: UserProfile
  contactDraft: {
    wechatId: string
    qq: string
  }
  categories: Category[]
  conditionOptions: string[]
  locationOptions: string[]
  priceInputWidth: number
  form: PublishForm
  errors: Record<string, string>
}

Component({
  data: {
    goodsId: '',
    loading: false,
    submitting: false,
    savingContact: false,
    info: '',
    contactError: '',
    showContactModal: false,
    userProfile: { nickname: '', avatarUrl: '', wechatId: '', qq: '' },
    contactDraft: {
      wechatId: '',
      qq: ''
    },
    categories: [],
    conditionOptions: CONDITION_OPTIONS,
    locationOptions: LOCATION_OPTIONS,
    priceInputWidth: getPriceInputWidth(''),
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
      ;(this as any)._skipNextOnShow = true
      this.checkToken().then(valid => {
        if (!valid) return
        this.setData({ goodsId: options.id || '' })
        this.loadCategories()
        if (options.id) {
          this.loadGoods(options.id)
        }
      })
    },

    onShow() {
      if ((this as any)._skipNextOnShow) {
        ;(this as any)._skipNextOnShow = false
        return
      }
      this.checkToken().then(valid => {
        if (!valid) return
        const editId = wx.getStorageSync('editGoodsId')
        if (editId) {
          wx.removeStorageSync('editGoodsId')
          this.setData({ goodsId: editId })
          this.loadGoods(editId)
        }
      })
    },

    onUnload() {
      this.cleanupStagedPhotos(this.data.form.photos)
    },

    checkToken(): Promise<boolean> {
      return new Promise((resolve) => {
        const token = getToken()
        if (!token) {
          redirectToLogin(undefined, '/pages/publish/publish', COMMON_MESSAGES.LOGIN_REQUIRED)
          resolve(false)
          return
        }
        wx.request({
          url: `${app.globalData.baseUrl}/users/me`,
          method: 'GET',
          header: { Authorization: `Bearer ${token}` },
          success: (res) => {
            const response = res.data as ApiResponse<UserProfile> | undefined
            if (res.statusCode === 401) {
              redirectToLogin(token, '/pages/publish/publish', response?.message)
              resolve(false)
              return
            }
            if (res.statusCode >= 200 && res.statusCode < 300 && response?.success && response.data) {
              const profile = response.data as UserProfile
              this.setData({
                userProfile: profile,
                contactDraft: {
                  wechatId: profile.wechatId || '',
                  qq: profile.qq || ''
                }
              })
              resolve(true)
              return
            }
            const message = response?.message || loadFailed('登录状态')
            this.setData({ info: message })
            wx.showToast({ title: message, icon: 'none' })
            resolve(false)
          },
          fail: () => {
            this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
            wx.showToast({ title: COMMON_MESSAGES.NETWORK_ERROR, icon: 'none' })
            resolve(false)
          }
        })
      })
    },

    async loadCategories() {
      try {
        const res = await request<ApiResponse<Category[]>>({
          url: `${app.globalData.baseUrl}/categories`,
          method: 'GET'
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || loadFailed('分类') })
          return
        }
        const categories = (res.data?.data as unknown as Category[]) || []
        this.setData({ categories })
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
      }
    },

    async loadGoods(id: string) {
      this.setData({ loading: true })
      try {
        const res = await request<ApiResponse<GoodsItem>>({
          url: `${app.globalData.baseUrl}/goods/${id}`,
          method: 'GET'
        })
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || loadFailed('商品信息') })
          return
        }
        const goods = res.data?.data as unknown as GoodsItem | undefined
        if (!goods) return
        const price = String(goods.price || '')
        this.setData({
          priceInputWidth: getPriceInputWidth(price),
          form: {
            title: goods.title || '',
            description: goods.description || '',
            price,
            conditionLevel: goods.conditionLevel || CONDITION_OPTIONS[1],
            campusLocation: goods.campusLocation || LOCATION_OPTIONS[0],
            categoryId: String(goods.category?.id || ''),
            photos: (goods.imageUrls || []).map((url, i) => ({
              url,
              filename: (goods.imageKeys || [])[i] || url,
              staged: false
            }))
          }
        })
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ loading: false })
      }
    },

    onPriceInput(e: WechatMiniprogram.InputEvent) {
      const price = e.detail.value
      this.setData({
        'form.price': price,
        priceInputWidth: getPriceInputWidth(price),
        'errors.price': ''
      })
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
      }, () => {
        wx.pageScrollTo({ scrollTop: 0, duration: 220 })
      })
    },

    chooseImages() {
      const remain = 3 - this.data.form.photos.length
      if (remain <= 0) return
      wx.chooseMedia({
        count: remain,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        sizeType: ['compressed'],
        success: async (res) => {
          const files = res.tempFiles || []
          wx.showLoading({ title: '上传中...', mask: true })
          try {
            const outcomes = await Promise.all(files.map(async file => {
              try {
                return { ok: true as const, result: await uploadImage(file.tempFilePath, 'goods') }
              } catch (error) {
                return { ok: false as const, error }
              }
            }))
            const results = outcomes.reduce<UploadResult[]>((uploaded, outcome) => {
              if (outcome.ok) uploaded.push(outcome.result)
              return uploaded
            }, [])
            const optimizedResults = results.map(result => ({
              ...result,
              url: result.displayUrl || result.thumbnailUrl || result.url,
              staged: true
            }))
            const photos = this.data.form.photos.concat(optimizedResults)
            const errors = outcomes.reduce<unknown[]>((failed, outcome) => {
              if (!outcome.ok) failed.push(outcome.error)
              return failed
            }, [])
            const firstError = errors[0]
            const info = errors.length
              ? (results.length
                  ? `${errors.length} 张图片上传失败，已保留成功图片`
                  : (firstError instanceof Error ? firstError.message : COMMON_MESSAGES.IMAGE_UPLOAD_FAILED))
              : ''
            this.setData({ 'form.photos': photos, 'errors.photos': '', info })
          } finally {
            wx.hideLoading()
          }
        }
      })
    },

    removePhoto(e: WechatMiniprogram.TouchEvent) {
      const index = e.currentTarget.dataset.index as number
      const photos = this.data.form.photos.slice()
      const removed = photos.splice(index, 1)[0]
      this.setData({ 'form.photos': photos })
      if (removed?.staged && removed.filename) {
        deleteStagedImage(removed.filename).catch(() => {
          this.setData({ info: '图片已从预览移除，存储清理将在后台重试' })
        })
      }
    },

    cleanupStagedPhotos(photos: UploadResult[]) {
      photos
        .filter(photo => photo.staged && photo.filename)
        .forEach(photo => {
          deleteStagedImage(photo.filename).catch(() => undefined)
        })
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

    openContactModal() {
      const profile = this.data.userProfile || { nickname: '', avatarUrl: '', wechatId: '', qq: '' }
      this.setData({
        showContactModal: true,
        contactDraft: {
          wechatId: profile.wechatId || '',
          qq: profile.qq || ''
        },
        contactError: '',
        info: ''
      })
    },

    closeContactModal() {
      if (this.data.savingContact || this.data.submitting) return
      this.setData({
        showContactModal: false,
        contactError: ''
      })
    },

    onContactWechatInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'contactDraft.wechatId': e.detail.value, contactError: '' })
    },

    onContactQqInput(e: WechatMiniprogram.InputEvent) {
      this.setData({ 'contactDraft.qq': e.detail.value, contactError: '' })
    },

    async saveContactBeforePublish() {
      if (this.data.savingContact || this.data.submitting) return

      const wechatId = (this.data.contactDraft.wechatId || '').trim()
      const qq = (this.data.contactDraft.qq || '').trim()
      const validation = validateContactDraft({ wechatId, qq })
      if (!validation.ok) {
        this.setData({ contactError: validation.message })
        return
      }

      const currentProfile = this.data.userProfile || { nickname: '', avatarUrl: '' }
      const nickname = (currentProfile.nickname || '微信用户').trim()

      this.setData({ savingContact: true, contactError: '', info: '' })
      try {
        const res = await request<ApiResponse<UserProfile>>({
          url: `${app.globalData.baseUrl}/users/me`,
          method: 'PUT',
          data: { nickname, wechatId, qq }
        })
        if (!res.data?.success) {
          this.setData({ contactError: res.data?.message || actionFailed('保存') })
          return
        }
        const profile = (res.data?.data as unknown as UserProfile) || {
          ...currentProfile,
          wechatId,
          qq
        }
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
          userProfile: profile,
          contactDraft: {
            wechatId: profile.wechatId || '',
            qq: profile.qq || ''
          },
          showContactModal: false,
          contactError: ''
        })
        await this.submitGoods()
      } catch (_err) {
        this.setData({ contactError: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ savingContact: false })
      }
    },

    resetForm() {
      this.setData({
        goodsId: '',
        info: '',
        priceInputWidth: getPriceInputWidth(''),
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
      if (!hasContactMethod(this.data.userProfile)) {
        this.openContactModal()
        return
      }

      await this.submitGoods()
    },

    async submitGoods() {
      const form = this.data.form
      const payload = {
        title: form.title.trim(),
        description: form.description.trim(),
        price: Number(form.price),
        conditionLevel: form.conditionLevel,
        campusLocation: form.campusLocation,
        categoryId: form.categoryId || null,
        imageUrls: form.photos.map(item => item.filename),
        imageThumbnailUrls: form.photos.map(item => item.thumbnailFilename || item.thumbnailUrl || ''),
        imageDisplayUrls: form.photos.map(item => item.displayFilename || item.displayUrl || ''),
        imageAuditThumbnailUrls: form.photos.map(item => item.auditThumbnailFilename || item.auditThumbnailUrl || '')
      }

      this.setData({ submitting: true, info: '' })
      try {
        let res: WxResponse<ApiResponse>
        if (this.data.goodsId) {
          res = await request<ApiResponse>({
            url: `${app.globalData.baseUrl}/goods/${this.data.goodsId}`,
            method: 'PUT',
            data: payload
          })
        } else {
          res = await request<ApiResponse>({
            url: `${app.globalData.baseUrl}/goods`,
            method: 'POST',
            data: payload
          })
        }
        if (!res.data?.success) {
          this.setData({ info: res.data?.message || actionFailed('提交') })
          return
        }
        wx.showToast({
          title: this.data.goodsId ? '已更新，等待审核' : '已提交，等待审核',
          icon: 'success'
        })
        wx.setStorageSync(PROFILE_DATA_DIRTY_KEY, true)
        wx.setStorageSync(GOODS_LIST_DIRTY_KEY, true)
        this.resetForm()
        setTimeout(() => {
          wx.switchTab({ url: '/pages/profile/profile' })
        }, 1200)
      } catch (_err) {
        this.setData({ info: COMMON_MESSAGES.NETWORK_ERROR })
      } finally {
        this.setData({ submitting: false })
      }
    }
  }
})
