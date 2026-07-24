const AUTH_STORAGE_KEYS = {
  token: 'checkui:token',
  reviewer: 'checkui:reviewer'
}

const DEFAULT_API_BASE_URL = 'https://www.ahut-campus.site/api/v1'
let loginModalReturnFocus = null

window.authState = {
  apiBaseUrl: DEFAULT_API_BASE_URL,
  token: localStorage.getItem(AUTH_STORAGE_KEYS.token) || '',
  reviewer: parseJson(localStorage.getItem(AUTH_STORAGE_KEYS.reviewer)),
  callbacks: {}
}

function initAuth(options = {}) {
  window.authState.callbacks = options

  const modal = document.querySelector('#loginModal')
  const form = document.querySelector('#loginForm')
  const showBtn = document.querySelector('#showLoginBtn')
  const logoutBtn = document.querySelector('#logoutBtn')
  const closeBtn = document.querySelector('#closeLoginBtn')

  if (showBtn) showBtn.addEventListener('click', openLoginModal)
  if (logoutBtn) logoutBtn.addEventListener('click', handleLogout)
  if (closeBtn) closeBtn.addEventListener('click', () => closeLoginModal())
  if (form) form.addEventListener('submit', handleLogin)
  if (modal) {
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeLoginModal()
    })
  }
  document.addEventListener('keydown', handleLoginModalKeydown)

  renderAuthSession()
}

function openLoginModal() {
  const modal = document.querySelector('#loginModal')
  if (!modal) return

  loginModalReturnFocus = document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null
  modal.setAttribute('aria-hidden', 'false')
  modal.classList.add('is-visible')
  document.body.classList.add('modal-open')

  const email = document.querySelector('#emailInput')
  window.requestAnimationFrame(() => {
    if (modal.classList.contains('is-visible')) {
      email?.focus()
    }
  })
}

function closeLoginModal(restoreFocus = true) {
  const modal = document.querySelector('#loginModal')
  if (!modal) return

  modal.classList.remove('is-visible')
  modal.setAttribute('aria-hidden', 'true')
  document.body.classList.remove('modal-open')

  const password = document.querySelector('#passwordInput')
  if (password) password.value = ''

  if (restoreFocus && loginModalReturnFocus?.isConnected) {
    loginModalReturnFocus.focus()
  }
  loginModalReturnFocus = null
}

function handleLoginModalKeydown(event) {
  const modal = document.querySelector('#loginModal')
  if (!modal?.classList.contains('is-visible')) return

  if (event.key === 'Escape') {
    closeLoginModal()
    event.preventDefault()
    return
  }

  if (event.key !== 'Tab') return

  const focusableElements = Array.from(modal.querySelectorAll(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]'
  )).filter(element => !element.hidden)
  if (!focusableElements.length) return

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]

  if (event.shiftKey && document.activeElement === firstElement) {
    lastElement.focus()
    event.preventDefault()
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    firstElement.focus()
    event.preventDefault()
  }
}

async function handleLogin(event) {
  event.preventDefault()

  const emailInput = document.querySelector('#emailInput')
  const passwordInput = document.querySelector('#passwordInput')
  const email = emailInput?.value.trim() || ''
  const password = passwordInput?.value || ''

  if (!email || !password) {
    showToast('请输入账号和密码', 'error')
    return
  }

  const loginBtn = document.querySelector('#loginBtn')
  if (loginBtn) {
    loginBtn.disabled = true
    loginBtn.textContent = '登录中...'
  }

  try {
    const response = await authRequest('/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false
    })

    window.authState.token = response.token
    window.authState.reviewer = response.user
    localStorage.setItem(AUTH_STORAGE_KEYS.token, response.token)
    localStorage.setItem(AUTH_STORAGE_KEYS.reviewer, JSON.stringify(response.user))

    closeLoginModal(false)
    renderAuthSession()

    if (window.authState.callbacks.onLoginSuccess) {
      await window.authState.callbacks.onLoginSuccess()
    }

    showToast('登录成功', 'success')
    document.querySelector('#mainContent')?.focus()
  } catch (error) {
    showToast(error.message || '登录失败', 'error')
  } finally {
    if (loginBtn) {
      loginBtn.disabled = false
      loginBtn.textContent = '登录'
    }
  }
}

function handleLogout() {
  window.authState.token = ''
  window.authState.reviewer = null
  localStorage.removeItem(AUTH_STORAGE_KEYS.token)
  localStorage.removeItem(AUTH_STORAGE_KEYS.reviewer)

  renderAuthSession()

  if (window.authState.callbacks.onLogout) {
    window.authState.callbacks.onLogout()
  }

  showToast('已退出', 'success')
}

function renderAuthSession() {
  const online = Boolean(window.authState.token && window.authState.reviewer)
  const sessionDot = document.querySelector('#sessionDot')
  const sessionTitle = document.querySelector('#sessionTitle')
  const showLoginBtn = document.querySelector('#showLoginBtn')
  const logoutBtn = document.querySelector('#logoutBtn')

  if (sessionDot) {
    sessionDot.classList.toggle('is-online', online)
    sessionDot.setAttribute('aria-hidden', 'true')
  }
  if (sessionTitle) {
    sessionTitle.textContent = online
      ? (window.authState.reviewer.nickname || window.authState.reviewer.email || '审核员')
      : '未登录'
  }

  if (showLoginBtn) showLoginBtn.hidden = online
  if (logoutBtn) logoutBtn.hidden = !online
}

async function hydrateAuthSession() {
  if (!window.authState.token) {
    renderAuthSession()
    return false
  }

  try {
    const reviewer = await authRequest('/auth/me')
    window.authState.reviewer = reviewer
    localStorage.setItem(AUTH_STORAGE_KEYS.reviewer, JSON.stringify(reviewer))
    renderAuthSession()
    return true
  } catch (error) {
    clearAuthSession()
    renderAuthSession()
    showToast(error.message || UI_MESSAGES.SESSION_EXPIRED, 'error')
    return false
  }
}

function clearAuthSession() {
  window.authState.token = ''
  window.authState.reviewer = null
  localStorage.removeItem(AUTH_STORAGE_KEYS.token)
  localStorage.removeItem(AUTH_STORAGE_KEYS.reviewer)
}

async function authRequest(path, options = {}) {
  const auth = options.auth !== false
  const url = window.authState.apiBaseUrl + path
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }

  if (auth && window.authState.token) {
    headers.Authorization = `Bearer ${window.authState.token}`
  }

  let response
  try {
    response = await fetch(url, {
      method: options.method || 'GET',
      headers,
      body: options.body === null || options.body === undefined ? undefined : JSON.stringify(options.body)
    })
  } catch (_error) {
    throw new Error(UI_MESSAGES.NETWORK_ERROR)
  }

  let payload = null
  try {
    payload = await response.json()
  } catch (_error) {
    payload = null
  }

  if (response.status === 401) {
    clearAuthSession()
    renderAuthSession()
  }

  if (!response.ok || !payload?.success) {
    const message = payload?.message || (response.status === 401
      ? UI_MESSAGES.LOGIN_REQUIRED
      : UI_MESSAGES.REQUEST_FAILED)
    throw new Error(message)
  }

  return payload.data
}

function parseJson(value) {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch (_error) {
    return null
  }
}
