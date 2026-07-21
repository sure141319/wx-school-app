const STORAGE_KEYS = {
  apiBaseUrl: 'checkui:apiBaseUrl',
  token: 'checkui:token',
  reviewer: 'checkui:reviewer'
}

const DEFAULT_API_BASE_URL = 'https://www.ahut-campus.site/api/v1'
const BEIJING_TIME_ZONE = 'Asia/Shanghai'

const state = {
  apiBaseUrl: normalizeBaseUrl(localStorage.getItem(STORAGE_KEYS.apiBaseUrl) || DEFAULT_API_BASE_URL),
  token: localStorage.getItem(STORAGE_KEYS.token) || '',
  reviewer: parseJson(localStorage.getItem(STORAGE_KEYS.reviewer)),
  loading: false
}

const els = {
  sessionDot: document.querySelector('#sessionDot'),
  sessionTitle: document.querySelector('#sessionTitle'),
  loginInfo: document.querySelector('#loginInfo'),
  loginPrompt: document.querySelector('#loginPrompt'),
  logoutBtn: document.querySelector('#logoutBtn'),
  apiBaseUrl: document.querySelector('#apiBaseUrl'),
  saveConfigBtn: document.querySelector('#saveConfigBtn'),
  form: document.querySelector('#announcementForm'),
  titleInput: document.querySelector('#titleInput'),
  contentInput: document.querySelector('#contentInput'),
  enabledInput: document.querySelector('#enabledInput'),
  titleCount: document.querySelector('#titleCount'),
  contentCount: document.querySelector('#contentCount'),
  revisionText: document.querySelector('#revisionText'),
  updatedAtText: document.querySelector('#updatedAtText'),
  reloadBtn: document.querySelector('#reloadBtn'),
  saveBtn: document.querySelector('#saveBtn'),
  toast: document.querySelector('#toast')
}

let toastTimer = null

boot()

function boot() {
  els.apiBaseUrl.value = state.apiBaseUrl
  els.saveConfigBtn.addEventListener('click', handleSaveConfig)
  els.logoutBtn.addEventListener('click', handleLogout)
  els.form.addEventListener('submit', handleSubmit)
  els.reloadBtn.addEventListener('click', loadAnnouncement)
  els.titleInput.addEventListener('input', updateCharacterCounts)
  els.contentInput.addEventListener('input', updateCharacterCounts)
  renderSession()
  setFormEnabled(false)

  if (state.token) {
    hydrateSession()
  }
}

async function hydrateSession() {
  try {
    const reviewer = await request('/auth/me')
    state.reviewer = reviewer
    localStorage.setItem(STORAGE_KEYS.reviewer, JSON.stringify(reviewer))
    renderSession()
    setFormEnabled(true)
    await loadAnnouncement()
  } catch (error) {
    clearSession()
    renderSession()
    setFormEnabled(false)
    showToast(error.message || UI_MESSAGES.SESSION_EXPIRED, 'error')
  }
}

async function loadAnnouncement() {
  if (!state.token || state.loading) return
  setLoading(true)
  try {
    const announcement = await request('/audit/announcement')
    els.titleInput.value = announcement.title || ''
    els.contentInput.value = announcement.content || ''
    els.enabledInput.checked = Boolean(announcement.enabled)
    els.revisionText.textContent = String(announcement.revision || '-')
    els.updatedAtText.textContent = formatDate(announcement.updatedAt)
    updateCharacterCounts()
  } catch (error) {
    showToast(error.message || UI_MESSAGES.LOAD_FAILED, 'error')
  } finally {
    setLoading(false)
  }
}

async function handleSubmit(event) {
  event.preventDefault()
  if (!state.token || state.loading) return

  const title = els.titleInput.value.trim()
  const content = els.contentInput.value.trim()
  if (!title) {
    showToast('请输入公告标题', 'error')
    els.titleInput.focus()
    return
  }
  if (!content) {
    showToast('请输入公告正文', 'error')
    els.contentInput.focus()
    return
  }

  setLoading(true)
  try {
    const announcement = await request('/audit/announcement', {
      method: 'PUT',
      body: {
        title,
        content,
        enabled: els.enabledInput.checked
      }
    })
    els.titleInput.value = announcement.title
    els.contentInput.value = announcement.content
    els.enabledInput.checked = Boolean(announcement.enabled)
    els.revisionText.textContent = String(announcement.revision)
    els.updatedAtText.textContent = formatDate(announcement.updatedAt)
    updateCharacterCounts()
    showToast('公告已保存', 'success')
  } catch (error) {
    showToast(error.message || UI_MESSAGES.ACTION_FAILED, 'error')
  } finally {
    setLoading(false)
  }
}

function handleLogout() {
  clearSession()
  renderSession()
  setFormEnabled(false)
  showToast('已退出', 'success')
}

function handleSaveConfig() {
  const nextBaseUrl = normalizeBaseUrl(els.apiBaseUrl.value)
  if (!nextBaseUrl) {
    showToast('请输入有效的 API 地址', 'error')
    return
  }
  state.apiBaseUrl = nextBaseUrl
  localStorage.setItem(STORAGE_KEYS.apiBaseUrl, nextBaseUrl)
  showToast('地址已保存', 'success')
}

function renderSession() {
  const loggedIn = Boolean(state.token && state.reviewer)
  els.sessionDot.classList.toggle('is-online', loggedIn)
  els.sessionTitle.textContent = loggedIn
    ? (state.reviewer.nickname || state.reviewer.email || '已登录')
    : '未登录'
  els.loginInfo.style.display = loggedIn ? '' : 'none'
  els.loginPrompt.style.display = loggedIn ? 'none' : ''
}

function setFormEnabled(enabled) {
  els.titleInput.disabled = !enabled
  els.contentInput.disabled = !enabled
  els.enabledInput.disabled = !enabled
  els.reloadBtn.disabled = !enabled
  els.saveBtn.disabled = !enabled
}

function setLoading(loading) {
  state.loading = loading
  setFormEnabled(!loading && Boolean(state.token))
  els.saveBtn.textContent = loading ? '处理中...' : '保存公告'
}

function updateCharacterCounts() {
  els.titleCount.textContent = `${els.titleInput.value.length}/50`
  els.contentCount.textContent = `${els.contentInput.value.length}/1000`
}

async function request(path, options = {}) {
  const url = state.apiBaseUrl + path
  const headers = {
    'Content-Type': 'application/json'
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`
  }

  let response
  try {
    response = await fetch(url, {
      method: options.method || 'GET',
      headers,
      body: options.body === null || options.body === undefined
        ? undefined
        : JSON.stringify(options.body)
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
    clearSession()
    renderSession()
    setFormEnabled(false)
  }

  if (!response.ok || !payload || !payload.success) {
    const message = payload && payload.message
      ? payload.message
      : (response.status === 401 ? UI_MESSAGES.LOGIN_REQUIRED : UI_MESSAGES.REQUEST_FAILED)
    throw new Error(message)
  }

  return payload.data
}

function clearSession() {
  state.token = ''
  state.reviewer = null
  localStorage.removeItem(STORAGE_KEYS.token)
  localStorage.removeItem(STORAGE_KEYS.reviewer)
}

function formatDate(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).format(parsed)
}

function normalizeBaseUrl(value) {
  return (value || '').trim().replace(/\/+$/, '')
}

function parseJson(value) {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch (_error) {
    return null
  }
}

function showToast(message, type = 'success') {
  if (!message) return
  els.toast.textContent = message
  els.toast.classList.add('is-visible')
  els.toast.classList.toggle('is-error', type === 'error')
  els.toast.classList.toggle('is-success', type === 'success')
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    els.toast.classList.remove('is-visible')
  }, 2200)
}
