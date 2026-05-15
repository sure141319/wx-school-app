import { request, clearTokenCache } from '../../utils/request'

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@qq\.com$/
const CODE_REGEX = /^\d{6}$/
const MIN_PASSWORD_LENGTH = 6
const MIN_NICKNAME_LENGTH = 2
const MAX_NICKNAME_LENGTH = 20
const TAB_PAGES = ['/pages/index/index', '/pages/publish/publish', '/pages/profile/profile']

interface AuthPageData {
  loading: boolean
  message: string
  mode: 'login' | 'register' | 'reset'
  sendCodeCooldown: number
  resetCodeCooldown: number
  sendCodeTimer: number | null
  resetCodeTimer: number | null
  sendingCode: boolean
  registerForm: RegisterForm
  loginForm: AuthForm
  resetForm: ResetForm
  loginEmailValid: boolean
  loginPasswordValid: boolean
  registerEmailValid: boolean
  registerCodeValid: boolean
  registerNicknameValid: boolean
  registerPasswordValid: boolean
  resetEmailValid: boolean
  resetCodeValid: boolean
  resetPasswordValid: boolean
  resetConfirmPasswordValid: boolean
  isLoginFormValid: boolean
  isRegisterFormValid: boolean
  isResetFormValid: boolean
  redirect: string
  showFeedbackModal: boolean
  savedEmails: string[]
  showEmailHistory: boolean
}

Component({
  data: {
    loading: false,
    message: '',
    mode: 'login',
    sendCodeCooldown: 0,
    resetCodeCooldown: 0,
    sendCodeTimer: null,
    resetCodeTimer: null,
    sendingCode: false,
    registerForm: { email: '', code: '', password: '', nickname: '' },
    loginForm: { email: '', password: '' },
    resetForm: { email: '', code: '', newPassword: '', confirmPassword: '' },
    loginEmailValid: true,
    loginPasswordValid: true,
    registerEmailValid: true,
    registerCodeValid: true,
    registerNicknameValid: true,
    registerPasswordValid: true,
    resetEmailValid: true,
    resetCodeValid: true,
    resetPasswordValid: true,
    resetConfirmPasswordValid: true,
    isLoginFormValid: false,
    isRegisterFormValid: false,
    isResetFormValid: false,
    redirect: '',
    showFeedbackModal: false,
    savedEmails: [] as string[],
    showEmailHistory: false
  } as AuthPageData,

  methods: {
    onLoad(options: Record<string, string | undefined>) {
      if (options?.redirect) {
        this.setData({ redirect: decodeURIComponent(options.redirect) })
      }
      const savedEmails = wx.getStorageSync('savedEmails') || []
      this.setData({ savedEmails: Array.isArray(savedEmails) ? savedEmails : [] })
    },

    onUnload() {
      if (this.data.sendCodeTimer) clearTimeout(this.data.sendCodeTimer)
      if (this.data.resetCodeTimer) clearTimeout(this.data.resetCodeTimer)
    },

    checkLoginFormValid() {
      const { email, password } = this.data.loginForm
      const valid = !!(email && password && EMAIL_REGEX.test(email) && password.length >= MIN_PASSWORD_LENGTH)
      this.setData({ isLoginFormValid: valid })
    },

    checkRegisterFormValid() {
      const { email, code, password, nickname } = this.data.registerForm
      const valid = !!(email && code && password && nickname &&
        EMAIL_REGEX.test(email) &&
        CODE_REGEX.test(code) &&
        password.length >= MIN_PASSWORD_LENGTH &&
        nickname.length >= MIN_NICKNAME_LENGTH &&
        nickname.length <= MAX_NICKNAME_LENGTH)
      this.setData({ isRegisterFormValid: valid })
    },

    checkResetFormValid() {
      const { email, code, newPassword, confirmPassword } = this.data.resetForm
      const valid = !!(email && code && newPassword && confirmPassword &&
        EMAIL_REGEX.test(email) &&
        CODE_REGEX.test(code) &&
        newPassword.length >= MIN_PASSWORD_LENGTH &&
        confirmPassword === newPassword)
      this.setData({ isResetFormValid: valid })
    },

    goBack() {
      wx.switchTab({ url: '/pages/index/index' })
    },

    switchMode() {
      this.setData({
        mode: this.data.mode === 'login' ? 'register' : 'login',
        message: '',
        loginEmailValid: true,
        loginPasswordValid: true,
        registerEmailValid: true,
        registerCodeValid: true,
        registerNicknameValid: true,
        registerPasswordValid: true
      })
    },

    goToReset() {
      this.setData({
        mode: 'reset',
        message: '',
        resetEmailValid: true,
        resetCodeValid: true,
        resetPasswordValid: true,
        resetConfirmPasswordValid: true
      })
    },

    goToLogin() {
      this.setData({
        mode: 'login',
        message: '',
        loginEmailValid: true,
        loginPasswordValid: true
      })
    },

    goAfterAuth() {
      const redirect = this.data.redirect || '/pages/index/index'
      if (TAB_PAGES.includes(redirect)) {
        wx.switchTab({ url: redirect })
      } else {
        wx.redirectTo({ url: redirect })
      }
    },

    showEmailHistory() {
      if (this.data.savedEmails.length) {
        this.setData({ showEmailHistory: true })
      }
    },

    hideEmailHistory() {
      this.setData({ showEmailHistory: false })
    },

    selectSavedEmail(e: WechatMiniprogram.TouchEvent) {
      const email = e.currentTarget.dataset.email as string
      this.setData({
        'loginForm.email': email,
        showEmailHistory: false,
        loginEmailValid: EMAIL_REGEX.test(email)
      })
      this.checkLoginFormValid()
    },

    onLoginEmailInput(e: WechatMiniprogram.InputEvent) {
      const email = e.detail.value
      this.setData({
        'loginForm.email': email,
        loginEmailValid: !email || EMAIL_REGEX.test(email)
      })
      this.checkLoginFormValid()
    },

    onLoginPasswordInput(e: WechatMiniprogram.InputEvent) {
      const password = e.detail.value
      this.setData({
        'loginForm.password': password,
        loginPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
      })
      this.checkLoginFormValid()
    },

    onRegisterEmailInput(e: WechatMiniprogram.InputEvent) {
      const email = e.detail.value
      this.setData({
        'registerForm.email': email,
        registerEmailValid: !email || EMAIL_REGEX.test(email)
      })
      this.checkRegisterFormValid()
    },

    onRegisterCodeInput(e: WechatMiniprogram.InputEvent) {
      const code = e.detail.value
      this.setData({
        'registerForm.code': code,
        registerCodeValid: !code || CODE_REGEX.test(code)
      })
      this.checkRegisterFormValid()
    },

    onRegisterNicknameInput(e: WechatMiniprogram.InputEvent) {
      const nickname = e.detail.value
      this.setData({
        'registerForm.nickname': nickname,
        registerNicknameValid: !nickname || (nickname.length >= MIN_NICKNAME_LENGTH && nickname.length <= MAX_NICKNAME_LENGTH)
      })
      this.checkRegisterFormValid()
    },

    onRegisterPasswordInput(e: WechatMiniprogram.InputEvent) {
      const password = e.detail.value
      this.setData({
        'registerForm.password': password,
        registerPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
      })
      this.checkRegisterFormValid()
    },

    onResetEmailInput(e: WechatMiniprogram.InputEvent) {
      const email = e.detail.value
      this.setData({
        'resetForm.email': email,
        resetEmailValid: !email || EMAIL_REGEX.test(email)
      })
      this.checkResetFormValid()
    },

    onResetCodeInput(e: WechatMiniprogram.InputEvent) {
      const code = e.detail.value
      this.setData({
        'resetForm.code': code,
        resetCodeValid: !code || CODE_REGEX.test(code)
      })
      this.checkResetFormValid()
    },

    onResetPasswordInput(e: WechatMiniprogram.InputEvent) {
      const password = e.detail.value
      this.setData({
        'resetForm.newPassword': password,
        resetPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
      })
      this.checkResetFormValid()
    },

    onResetConfirmPasswordInput(e: WechatMiniprogram.InputEvent) {
      const confirmPassword = e.detail.value
      this.setData({
        'resetForm.confirmPassword': confirmPassword,
        resetConfirmPasswordValid: !confirmPassword || confirmPassword === this.data.resetForm.newPassword
      })
      this.checkResetFormValid()
    },

    startCooldown(type: 'register' | 'reset') {
      const cooldownKey = type === 'register' ? 'sendCodeCooldown' : 'resetCodeCooldown'
      const timerKey = type === 'register' ? 'sendCodeTimer' : 'resetCodeTimer'
      this.setData({ [cooldownKey]: 60 })

      const tick = (remaining: number) => {
        if (remaining <= 0) {
          this.setData({ [timerKey]: null })
          return
        }
        this.setData({ [cooldownKey]: remaining - 1 })
        const timer = setTimeout(() => tick(remaining - 1), 1000)
        this.setData({ [timerKey]: timer as unknown as number })
      }
      tick(60)
    },

    handleSendCode(e: WechatMiniprogram.TouchEvent) {
      if (this.data.sendingCode) return
      const { purpose, type } = e.currentTarget.dataset
      const codeType = type as 'register' | 'reset'
      const email = codeType === 'register' ? this.data.registerForm.email : this.data.resetForm.email

      if (!email) {
        this.setData({ message: '请先输入 QQ 邮箱地址' })
        return
      }
      if (!EMAIL_REGEX.test(email)) {
        this.setData({ message: '请输入正确的 qq.com 邮箱' })
        return
      }

      this.setData({ sendingCode: true, message: '' })
      const app = getApp<{ globalData: { baseUrl: string } }>()
      request({
        url: `${app.globalData.baseUrl}/auth/email-code`,
        method: 'POST',
        data: { email, purpose: purpose as string }
      }).then((res: WxResponse<ApiResponse>) => {
        if (res.data?.success) {
          this.startCooldown(codeType)
          this.setData({ message: '验证码已发送，请查收邮箱' })
        } else {
          this.setData({ message: res.data?.message || '验证码发送失败' })
        }
      }).catch(() => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      }).finally(() => {
        this.setData({ sendingCode: false })
      })
    },

    handleLogin() {
      if (!this.data.isLoginFormValid) {
        this.setData({ message: '请正确填写邮箱和密码' })
        return
      }
      this.setData({ loading: true, message: '' })
      const app = getApp<{ globalData: { baseUrl: string } }>()
      request<ApiResponse<{ token: string; user: UserProfile }>>({
        url: `${app.globalData.baseUrl}/auth/login`,
        method: 'POST',
        data: this.data.loginForm as unknown as Record<string, unknown>
      }).then((res) => {
        if (res.data?.success && res.data?.data) {
          clearTokenCache()
          wx.setStorageSync('token', res.data.data.token)
          wx.setStorageSync('user', JSON.stringify(res.data.data.user))
          this.saveEmailHistory(this.data.loginForm.email)
          wx.showToast({ title: '登录成功', icon: 'success' })
          setTimeout(() => this.goAfterAuth(), 500)
        } else {
          this.setData({ message: res.data?.message || '邮箱或密码错误' })
        }
      }).catch(() => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      }).finally(() => {
        this.setData({ loading: false })
      })
    },

    saveEmailHistory(email: string) {
      if (!email) return
      let saved: string[] = wx.getStorageSync('savedEmails') || []
      if (!Array.isArray(saved)) saved = []
      saved = saved.filter(item => item !== email)
      saved.unshift(email)
      if (saved.length > 5) saved = saved.slice(0, 5)
      wx.setStorageSync('savedEmails', saved)
      this.setData({ savedEmails: saved })
    },

    handleRegister() {
      if (!this.data.isRegisterFormValid) {
        const { email, code, password, nickname } = this.data.registerForm
        if (!email) {
          this.setData({ message: '请输入邮箱' })
        } else if (!EMAIL_REGEX.test(email)) {
          this.setData({ message: '邮箱格式不正确，需要是 xxx@qq.com' })
        } else if (!code) {
          this.setData({ message: '请输入验证码' })
        } else if (!CODE_REGEX.test(code)) {
          this.setData({ message: '验证码为 6 位数字' })
        } else if (!nickname || nickname.length < MIN_NICKNAME_LENGTH) {
          this.setData({ message: `昵称至少 ${MIN_NICKNAME_LENGTH} 个字符` })
        } else if (nickname.length > MAX_NICKNAME_LENGTH) {
          this.setData({ message: `昵称最多 ${MAX_NICKNAME_LENGTH} 个字符` })
        } else if (!password) {
          this.setData({ message: '请输入密码' })
        } else if (password.length < MIN_PASSWORD_LENGTH) {
          this.setData({ message: `密码至少 ${MIN_PASSWORD_LENGTH} 位` })
        }
        return
      }
      this.setData({ loading: true, message: '' })
      const app = getApp<{ globalData: { baseUrl: string } }>()
      request<ApiResponse<{ token: string; user: UserProfile }>>({
        url: `${app.globalData.baseUrl}/auth/register`,
        method: 'POST',
        data: this.data.registerForm as unknown as Record<string, unknown>
      }).then((res) => {
        if (res.data?.success && res.data?.data) {
          clearTokenCache()
          wx.setStorageSync('token', res.data.data.token)
          wx.setStorageSync('user', JSON.stringify(res.data.data.user))
          this.saveEmailHistory(this.data.registerForm.email)
          wx.showToast({ title: '注册成功', icon: 'success' })
          setTimeout(() => this.goAfterAuth(), 500)
        } else {
          this.setData({ message: res.data?.message || '注册失败，请重试' })
        }
      }).catch(() => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      }).finally(() => {
        this.setData({ loading: false })
      })
    },

    handleReset() {
      if (!this.data.isResetFormValid) {
        const { email, code, newPassword, confirmPassword } = this.data.resetForm
        if (!email) {
          this.setData({ message: '请输入邮箱' })
        } else if (!EMAIL_REGEX.test(email)) {
          this.setData({ message: '邮箱格式不正确，需要是 xxx@qq.com' })
        } else if (!code) {
          this.setData({ message: '请输入验证码' })
        } else if (!CODE_REGEX.test(code)) {
          this.setData({ message: '验证码为 6 位数字' })
        } else if (!newPassword) {
          this.setData({ message: '请输入新密码' })
        } else if (newPassword.length < MIN_PASSWORD_LENGTH) {
          this.setData({ message: `密码至少 ${MIN_PASSWORD_LENGTH} 位` })
        } else if (!confirmPassword) {
          this.setData({ message: '请再次输入新密码' })
        } else if (confirmPassword !== newPassword) {
          this.setData({ message: '两次输入的密码不一致' })
        }
        return
      }
      this.setData({ loading: true, message: '' })
      const app = getApp<{ globalData: { baseUrl: string } }>()
      request<ApiResponse>({
        url: `${app.globalData.baseUrl}/auth/reset-password`,
        method: 'POST',
        data: this.data.resetForm as unknown as Record<string, unknown>
      }).then((res) => {
        if (res.data?.success) {
          this.setData({
            message: '密码重置成功，请返回登录',
            mode: 'login',
            'loginForm.email': this.data.resetForm.email
          })
          wx.showToast({ title: '重置成功', icon: 'success' })
        } else {
          this.setData({ message: res.data?.message || '重置失败，请重试' })
        }
      }).catch(() => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      }).finally(() => {
        this.setData({ loading: false })
      })
    },

    showFeedback() {
      this.setData({ showFeedbackModal: true })
    },

    closeFeedback() {
      this.setData({ showFeedbackModal: false })
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
