const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@qq\.com$/
const CODE_REGEX = /^\d{6}$/
const MIN_PASSWORD_LENGTH = 6
const MIN_NICKNAME_LENGTH = 2
const MAX_NICKNAME_LENGTH = 20

Page({
  data: {
    loading: false,
    message: '',
    mode: 'login',
    sendCodeCooldown: 0,
    resetCodeCooldown: 0,
    sendCodeTimer: null,
    resetCodeTimer: null,
    registerForm: { email: '', code: '', password: '', nickname: '' },
    loginForm: { email: '', password: '' },
    resetForm: { email: '', code: '', newPassword: '' },
    loginEmailValid: true,
    loginPasswordValid: true,
    registerEmailValid: true,
    registerCodeValid: true,
    registerNicknameValid: true,
    registerPasswordValid: true,
    resetEmailValid: true,
    resetCodeValid: true,
    resetPasswordValid: true,
    isLoginFormValid: false,
    isRegisterFormValid: false,
    isResetFormValid: false,
    redirect: '',
    showFeedbackModal: false
  },

  onLoad(options) {
    if (options.redirect) {
      this.setData({ redirect: options.redirect })
    }
  },

  onUnload() {
    if (this.data.sendCodeTimer) clearInterval(this.data.sendCodeTimer)
    if (this.data.resetCodeTimer) clearInterval(this.data.resetCodeTimer)
  },

  checkLoginFormValid() {
    const { email, password } = this.data.loginForm
    const valid = email && password && EMAIL_REGEX.test(email) && password.length >= MIN_PASSWORD_LENGTH
    this.setData({ isLoginFormValid: valid })
  },

  checkRegisterFormValid() {
    const { email, code, password, nickname } = this.data.registerForm
    const valid = email && code && password && nickname &&
           EMAIL_REGEX.test(email) && 
           CODE_REGEX.test(code) && 
           password.length >= MIN_PASSWORD_LENGTH &&
           nickname.length >= MIN_NICKNAME_LENGTH &&
           nickname.length <= MAX_NICKNAME_LENGTH
    this.setData({ isRegisterFormValid: valid })
  },

  checkResetFormValid() {
    const { email, code, newPassword } = this.data.resetForm
    const valid = email && code && newPassword &&
           EMAIL_REGEX.test(email) && 
           CODE_REGEX.test(code) && 
           newPassword.length >= MIN_PASSWORD_LENGTH
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
      resetPasswordValid: true
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

  onLoginEmailInput(e) {
    const email = e.detail.value
    this.setData({ 
      'loginForm.email': email,
      loginEmailValid: !email || EMAIL_REGEX.test(email)
    })
    this.checkLoginFormValid()
  },

  onLoginPasswordInput(e) {
    const password = e.detail.value
    this.setData({ 
      'loginForm.password': password,
      loginPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
    })
    this.checkLoginFormValid()
  },

  onRegisterEmailInput(e) {
    const email = e.detail.value
    this.setData({ 
      'registerForm.email': email,
      registerEmailValid: !email || EMAIL_REGEX.test(email)
    })
    this.checkRegisterFormValid()
  },

  onRegisterCodeInput(e) {
    const code = e.detail.value
    this.setData({ 
      'registerForm.code': code,
      registerCodeValid: !code || CODE_REGEX.test(code)
    })
    this.checkRegisterFormValid()
  },

  onRegisterNicknameInput(e) {
    const nickname = e.detail.value
    this.setData({ 
      'registerForm.nickname': nickname,
      registerNicknameValid: !nickname || (nickname.length >= MIN_NICKNAME_LENGTH && nickname.length <= MAX_NICKNAME_LENGTH)
    })
    this.checkRegisterFormValid()
  },

  onRegisterPasswordInput(e) {
    const password = e.detail.value
    this.setData({ 
      'registerForm.password': password,
      registerPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
    })
    this.checkRegisterFormValid()
  },

  onResetEmailInput(e) {
    const email = e.detail.value
    this.setData({ 
      'resetForm.email': email,
      resetEmailValid: !email || EMAIL_REGEX.test(email)
    })
    this.checkResetFormValid()
  },

  onResetCodeInput(e) {
    const code = e.detail.value
    this.setData({ 
      'resetForm.code': code,
      resetCodeValid: !code || CODE_REGEX.test(code)
    })
    this.checkResetFormValid()
  },

  onResetPasswordInput(e) {
    const password = e.detail.value
    this.setData({ 
      'resetForm.newPassword': password,
      resetPasswordValid: !password || password.length >= MIN_PASSWORD_LENGTH
    })
    this.checkResetFormValid()
  },

  startCooldown(type) {
    if (type === 'register') {
      this.setData({ sendCodeCooldown: 60 })
      const timer = setInterval(() => {
        if (this.data.sendCodeCooldown <= 0) {
          clearInterval(timer)
          this.setData({ sendCodeTimer: null })
        } else {
          this.setData({ sendCodeCooldown: this.data.sendCodeCooldown - 1 })
        }
      }, 1000)
      this.setData({ sendCodeTimer: timer })
    } else {
      this.setData({ resetCodeCooldown: 60 })
      const timer = setInterval(() => {
        if (this.data.resetCodeCooldown <= 0) {
          clearInterval(timer)
          this.setData({ resetCodeTimer: null })
        } else {
          this.setData({ resetCodeCooldown: this.data.resetCodeCooldown - 1 })
        }
      }, 1000)
      this.setData({ resetCodeTimer: timer })
    }
  },

  handleSendCode(e) {
    const { purpose, type } = e.currentTarget.dataset
    const email = type === 'register' ? this.data.registerForm.email : this.data.resetForm.email
    this.setData({ message: '' })

    if (!email) {
      this.setData({ message: '请先输入邮箱地址' })
      return
    }
    if (!EMAIL_REGEX.test(email)) {
      this.setData({ message: '请输入正确的 qq.com 邮箱' })
      return
    }

    const app = getApp()
    wx.request({
      url: `${app.globalData.baseUrl}/auth/email-code`,
      method: 'POST',
      data: { email, purpose },
      success: (res) => {
        if (res.data?.success) {
          this.startCooldown(type)
          this.setData({
            message: '验证码已发送，请查收邮箱'
          })
        } else {
          this.setData({ message: res.data?.message || '验证码发送失败' })
        }
      },
      fail: () => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      }
    })
  },

  handleLogin() {
    if (!this.data.isLoginFormValid) {
      this.setData({ message: '请正确填写邮箱和密码' })
      return
    }
    this.setData({ loading: true, message: '' })
    const app = getApp()
    wx.request({
      url: `${app.globalData.baseUrl}/auth/login`,
      method: 'POST',
      data: this.data.loginForm,
      success: (res) => {
        if (res.data?.data) {
          wx.setStorageSync('token', res.data.data.token)
          wx.setStorageSync('user', JSON.stringify(res.data.data.user))
          wx.showToast({ title: '登录成功', icon: 'success' })
          setTimeout(() => {
            if (this.data.redirect) {
              wx.redirectTo({ url: this.data.redirect })
            } else {
              wx.switchTab({ url: '/pages/index/index' })
            }
          }, 500)
        } else {
          this.setData({ message: res.data?.message || '邮箱或密码错误' })
        }
      },
      fail: () => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
    })
  },

  handleRegister() {
    if (!this.data.isRegisterFormValid) {
      const { email, code, password, nickname } = this.data.registerForm
      if (!email) {
        this.setData({ message: '请输入邮箱' })
      } else if (!EMAIL_REGEX.test(email)) {
        this.setData({ message: '邮箱格式不正确，需为 xxx@qq.com' })
      } else if (!code) {
        this.setData({ message: '请输入验证码' })
      } else if (!CODE_REGEX.test(code)) {
        this.setData({ message: '验证码为6位数字' })
      } else if (!nickname || nickname.length < MIN_NICKNAME_LENGTH) {
        this.setData({ message: `昵称至少${MIN_NICKNAME_LENGTH}个字符` })
      } else if (nickname.length > MAX_NICKNAME_LENGTH) {
        this.setData({ message: `昵称最多${MAX_NICKNAME_LENGTH}个字符` })
      } else if (!password) {
        this.setData({ message: '请输入密码' })
      } else if (password.length < MIN_PASSWORD_LENGTH) {
        this.setData({ message: `密码至少${MIN_PASSWORD_LENGTH}位` })
      }
      return
    }
    this.setData({ loading: true, message: '' })
    const app = getApp()
    wx.request({
      url: `${app.globalData.baseUrl}/auth/register`,
      method: 'POST',
      data: this.data.registerForm,
      success: (res) => {
        if (res.data?.data) {
          wx.setStorageSync('token', res.data.data.token)
          wx.setStorageSync('user', JSON.stringify(res.data.data.user))
          wx.showToast({ title: '注册成功', icon: 'success' })
          setTimeout(() => {
            if (this.data.redirect) {
              wx.redirectTo({ url: this.data.redirect })
            } else {
              wx.switchTab({ url: '/pages/index/index' })
            }
          }, 500)
        } else {
          this.setData({ message: res.data?.message || '注册失败，请重试' })
        }
      },
      fail: () => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
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
  },

  handleReset() {
    if (!this.data.isResetFormValid) {
      const { email, code, newPassword } = this.data.resetForm
      if (!email) {
        this.setData({ message: '请输入邮箱' })
      } else if (!EMAIL_REGEX.test(email)) {
        this.setData({ message: '邮箱格式不正确，需为 xxx@qq.com' })
      } else if (!code) {
        this.setData({ message: '请输入验证码' })
      } else if (!CODE_REGEX.test(code)) {
        this.setData({ message: '验证码为6位数字' })
      } else if (!newPassword) {
        this.setData({ message: '请输入新密码' })
      } else if (newPassword.length < MIN_PASSWORD_LENGTH) {
        this.setData({ message: `密码至少${MIN_PASSWORD_LENGTH}位` })
      }
      return
    }
    this.setData({ loading: true, message: '' })
    const app = getApp()
    wx.request({
      url: `${app.globalData.baseUrl}/auth/reset-password`,
      method: 'POST',
      data: this.data.resetForm,
      success: (res) => {
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
      },
      fail: () => {
        this.setData({ message: '网络请求失败，请检查网络连接' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
    })
  }
})
