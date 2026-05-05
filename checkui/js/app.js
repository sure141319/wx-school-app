const STORAGE_KEYS = {
  apiBaseUrl: 'checkui:apiBaseUrl',
  token: 'checkui:token',
  reviewer: 'checkui:reviewer'
}

const STATUS_META = {
  PENDING: { label: '待审核', className: 'pending' },
  APPROVED: { label: '已通过', className: 'approved' },
  REJECTED: { label: '已驳回', className: 'rejected' }
}

const DEFAULT_API_BASE_URL = 'https://www.ahut-campus.site/api/v1'

const state = {
  apiBaseUrl: normalizeBaseUrl(localStorage.getItem(STORAGE_KEYS.apiBaseUrl) || DEFAULT_API_BASE_URL),
  token: localStorage.getItem(STORAGE_KEYS.token) || '',
  reviewer: parseJson(localStorage.getItem(STORAGE_KEYS.reviewer)),
  items: [],
  total: 0,
  page: 0,
  size: 10,
  status: 'PENDING',
  currentTab: 'goods',
  selectedImageId: null,
  loading: false,
  actionLoading: false
}

const els = {
  apiBaseUrl: document.querySelector('#apiBaseUrl'),
  saveConfigBtn: document.querySelector('#saveConfigBtn'),
  originNotice: document.querySelector('#originNotice'),
  loginForm: document.querySelector('#loginForm'),
  emailInput: document.querySelector('#emailInput'),
  passwordInput: document.querySelector('#passwordInput'),
  logoutBtn: document.querySelector('#logoutBtn'),
  sessionDot: document.querySelector('#sessionDot'),
  sessionTitle: document.querySelector('#sessionTitle'),
  typeFilters: document.querySelector('#typeFilters'),
  statusFilters: document.querySelector('#statusFilters'),
  refreshBtn: document.querySelector('#refreshBtn'),
  pageSizeSelect: document.querySelector('#pageSizeSelect'),
  queueMeta: document.querySelector('#queueMeta'),
  queueList: document.querySelector('#queueList'),
  prevPageBtn: document.querySelector('#prevPageBtn'),
  nextPageBtn: document.querySelector('#nextPageBtn'),
  pageIndicator: document.querySelector('#pageIndicator'),
  summaryStatus: document.querySelector('#summaryStatus'),
  summaryPage: document.querySelector('#summaryPage'),
  summaryTotal: document.querySelector('#summaryTotal'),
  detailContent: document.querySelector('#detailContent'),
  toast: document.querySelector('#toast')
}

let toastTimer = null

boot()

function boot() {
  els.apiBaseUrl.value = state.apiBaseUrl
  els.pageSizeSelect.value = String(state.size)
  renderOriginNotice()

  els.saveConfigBtn.addEventListener('click', handleSaveConfig)
  els.loginForm.addEventListener('submit', handleLogin)
  els.logoutBtn.addEventListener('click', handleLogout)
  els.refreshBtn.addEventListener('click', () => loadQueue({ keepSelection: true }))
  els.pageSizeSelect.addEventListener('change', handlePageSizeChange)
  els.prevPageBtn.addEventListener('click', handlePrevPage)
  els.nextPageBtn.addEventListener('click', handleNextPage)
  els.typeFilters.addEventListener('click', handleTypeFilter)
  els.statusFilters.addEventListener('click', handleStatusFilter)

  renderFilterState()
  renderTypeFilterState()
  renderSession()
  renderQueue()
  renderDetail()

  if (state.token) {
    hydrateSession()
  }
}

function renderOriginNotice() {
  const allowedOrigins = new Set([
    'http://localhost:5173',
    'http://127.0.0.1:5173'
  ])
  const currentOrigin = window.location.origin
  const shouldWarn = !allowedOrigins.has(currentOrigin)

  els.originNotice.hidden = !shouldWarn
  if (!shouldWarn) {
    els.originNotice.textContent = ''
    return
  }

  els.originNotice.textContent = `当前来源 ${currentOrigin} 不在 CORS 白名单，请用 localhost:5173 打开`
}

async function hydrateSession() {
  try {
    const reviewer = await request('/auth/me')
    state.reviewer = reviewer
    persistReviewer(reviewer)
    renderSession()
    await loadQueue()
    showToast('会话已恢复', 'success')
  } catch (error) {
    clearSession()
    renderSession()
    renderQueue()
    renderDetail()
    showToast(error.message || '登录已失效，请重新登录', 'error')
  }
}

async function handleLogin(event) {
  event.preventDefault()

  const email = els.emailInput.value.trim()
  const password = els.passwordInput.value

  if (!email || !password) {
    showToast('请输入账号和密码', 'error')
    return
  }

  setLoadingState(true)

  try {
    const response = await request('/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false
    })

    state.token = response.token
    state.reviewer = response.user
    localStorage.setItem(STORAGE_KEYS.token, response.token)
    persistReviewer(response.user)

    els.passwordInput.value = ''
    renderSession()
    await loadQueue()
    showToast('登录成功', 'success')
  } catch (error) {
    showToast(error.message || '登录失败', 'error')
  } finally {
    setLoadingState(false)
  }
}

function handleLogout() {
  clearSession()
  renderSession()
  renderQueue()
  renderDetail()
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

function handlePageSizeChange(event) {
  state.size = Number(event.target.value)
  state.page = 0
  loadQueue()
}

function handlePrevPage() {
  if (state.page === 0 || state.loading) {
    return
  }
  state.page -= 1
  loadQueue()
}

function handleNextPage() {
  if (state.loading) {
    return
  }
  const totalPages = getTotalPages()
  if (state.page >= totalPages - 1) {
    return
  }
  state.page += 1
  loadQueue()
}

function handleTypeFilter(event) {
  const button = event.target.closest('[data-type]')
  if (!button || state.loading) {
    return
  }
  const nextType = button.dataset.type
  if (state.currentTab === nextType) {
    return
  }
  state.currentTab = nextType
  state.page = 0
  state.selectedImageId = null
  renderTypeFilterState()
  loadQueue()
}

function handleStatusFilter(event) {
  const button = event.target.closest('[data-status]')
  if (!button || state.loading) {
    return
  }
  const nextStatus = button.dataset.status
  if (!STATUS_META[nextStatus] || state.status === nextStatus) {
    return
  }
  state.status = nextStatus
  state.page = 0
  state.selectedImageId = null
  renderFilterState()
  loadQueue()
}

async function loadQueue(options = {}) {
  if (!state.token) {
    state.items = []
    state.total = 0
    state.selectedImageId = null
    renderQueue()
    renderDetail()
    return
  }

  const keepSelection = Boolean(options.keepSelection)
  const previousSelection = state.selectedImageId
  let errorMessage = null

  state.loading = true
  renderQueue()

  try {
    const query = new URLSearchParams({
      status: state.status,
      page: String(state.page),
      size: String(state.size)
    })
    const apiPath = state.currentTab === 'goods'
      ? `/audit/images?${query.toString()}`
      : `/audit/images/avatars?${query.toString()}`
    const pageData = await request(apiPath)

    state.items = Array.isArray(pageData.items) ? pageData.items : []
    state.total = pageData.total || 0
    state.page = pageData.page || 0
    state.size = pageData.size || state.size

    const getItemId = (item) => state.currentTab === 'goods' ? item.imageId : item.userId
    const hasPreviousSelection = keepSelection && state.items.some(item => getItemId(item) === previousSelection)
    state.selectedImageId = hasPreviousSelection ? previousSelection : (state.items.length > 0 ? getItemId(state.items[0]) : null)
  } catch (error) {
    state.items = []
    state.total = 0
    state.selectedImageId = null
    errorMessage = error.message || '加载失败'
    showToast(errorMessage, 'error')
  } finally {
    state.loading = false
    renderQueue(errorMessage)
    renderDetail()
  }
}

async function approveSelected() {
  const selected = getSelectedItem()
  if (!selected || state.actionLoading) {
    return
  }

  state.actionLoading = true
  renderDetail()

  try {
    const apiPath = state.currentTab === 'goods'
      ? `/audit/images/${selected.imageId}/approve`
      : `/audit/images/avatars/${selected.userId}/approve`
    await request(apiPath, {
      method: 'POST',
      body: null
    })
    const label = state.currentTab === 'goods' ? `图片 #${selected.imageId}` : `用户 #${selected.userId} 头像`
    showToast(`${label} 已通过`, 'success')
    await loadQueue()
  } catch (error) {
    showToast(error.message || '操作失败', 'error')
  } finally {
    state.actionLoading = false
    renderDetail()
  }
}

async function rejectSelected() {
  const selected = getSelectedItem()
  if (!selected || state.actionLoading) {
    return
  }

  const remarkInput = document.querySelector('#remarkInput')
  const remark = remarkInput ? remarkInput.value.trim() : ''

  state.actionLoading = true
  renderDetail()

  try {
    const apiPath = state.currentTab === 'goods'
      ? `/audit/images/${selected.imageId}/reject`
      : `/audit/images/avatars/${selected.userId}/reject`
    await request(apiPath, {
      method: 'POST',
      body: remark ? { remark } : {}
    })
    const label = state.currentTab === 'goods' ? `图片 #${selected.imageId}` : `用户 #${selected.userId} 头像`
    showToast(`${label} 已驳回`, 'success')
    await loadQueue()
  } catch (error) {
    showToast(error.message || '操作失败', 'error')
  } finally {
    state.actionLoading = false
    renderDetail()
  }
}

function selectImage(imageId) {
  state.selectedImageId = imageId
  renderQueue()
  renderDetail()
}

function renderSession() {
  const reviewer = state.reviewer
  const online = Boolean(state.token && reviewer)

  els.sessionDot.classList.toggle('is-online', online)
  els.sessionTitle.textContent = online
    ? (reviewer.nickname || reviewer.email || '审核员')
    : '未登录'

  els.logoutBtn.disabled = !online
  els.emailInput.disabled = state.loading
  els.passwordInput.disabled = state.loading
}

function renderTypeFilterState() {
  Array.from(els.typeFilters.querySelectorAll('[data-type]')).forEach(button => {
    button.classList.toggle('is-active', button.dataset.type === state.currentTab)
  })
  const titleMap = {
    goods: '商品图片审核台',
    avatar: '头像审核台'
  }
  document.querySelector('#pageTitle').textContent = titleMap[state.currentTab] || '图片审核台'
}

function renderFilterState() {
  Array.from(els.statusFilters.querySelectorAll('[data-status]')).forEach(button => {
    button.classList.toggle('is-active', button.dataset.status === state.status)
  })
  els.summaryStatus.textContent = getStatusMeta(state.status).label
}

function renderQueue(message) {
  renderFilterState()

  const totalPages = getTotalPages()
  els.summaryPage.textContent = `${Math.min(state.page + 1, totalPages)}/${totalPages}`
  els.summaryTotal.textContent = String(state.total)
  els.pageIndicator.textContent = `第 ${Math.min(state.page + 1, totalPages)} 页`
  els.prevPageBtn.disabled = state.loading || state.page === 0
  els.nextPageBtn.disabled = state.loading || state.page >= totalPages - 1

  if (!state.token) {
    els.queueMeta.textContent = '请先登录'
    els.queueList.innerHTML = `
      <div class="empty-queue">
        <h4>等待登录</h4>
        <p>登录后自动加载待审核图片</p>
      </div>
    `
    return
  }

  const start = state.total === 0 ? 0 : state.page * state.size + 1
  const end = Math.min((state.page + 1) * state.size, state.total)
  els.queueMeta.textContent = message || `${start}-${end} / 共 ${state.total} 条`

  if (state.loading) {
    els.queueList.innerHTML = `
      <div class="loading-state">
        <h4>加载中...</h4>
      </div>
    `
    return
  }

  if (!state.items.length) {
    els.queueList.innerHTML = `
      <div class="empty-queue">
        <h4>暂无数据</h4>
        <p>可切换筛选条件或刷新</p>
      </div>
    `
    return
  }

  els.queueList.innerHTML = state.items.map(item => {
    const meta = getStatusMeta(item.auditStatus)
    const itemId = state.currentTab === 'goods' ? item.imageId : item.userId
    const selectedClass = itemId === state.selectedImageId ? 'is-selected' : ''
    
    if (state.currentTab === 'goods') {
      return `
        <button class="queue-item ${selectedClass}" type="button" data-item-id="${itemId}">
          <div class="queue-item-top">
            <div>
              <div class="queue-title">${escapeHtml(item.goodsTitle || `商品 #${item.goodsId}`)}</div>
              <div class="queue-subtitle">${escapeHtml(item.sellerNickname || `用户 ${item.sellerId}`)}</div>
            </div>
            <span class="audit-badge ${meta.className}">${meta.label}</span>
          </div>
          <div class="queue-item-bottom">
            <span>#${item.imageId} · 第 ${Number(item.sortOrder || 0) + 1} 张</span>
            <span>${formatDate(item.createdAt)}</span>
          </div>
        </button>
      `
    }
    
    return `
      <button class="queue-item ${selectedClass}" type="button" data-item-id="${itemId}">
        <div class="queue-item-top">
          <div>
            <div class="queue-title">${escapeHtml(item.nickname || `用户 #${item.userId}`)}</div>
            <div class="queue-subtitle">用户ID: ${item.userId}</div>
          </div>
          <span class="audit-badge ${meta.className}">${meta.label}</span>
        </div>
        <div class="queue-item-bottom">
          <span>头像审核</span>
          <span>${formatDate(item.updatedAt)}</span>
        </div>
      </button>
    `
  }).join('')

  Array.from(els.queueList.querySelectorAll('[data-item-id]')).forEach(button => {
    button.addEventListener('click', () => {
      selectImage(Number(button.dataset.itemId))
    })
  })
}

function renderDetail() {
  const selected = getSelectedItem()

  if (!state.token) {
    els.detailContent.innerHTML = `
      <div class="empty-state">
        <h4>请先登录</h4>
        <p>登录后可查看图片详情</p>
      </div>
    `
    return
  }

  if (!selected) {
    els.detailContent.innerHTML = `
      <div class="empty-state">
        <h4>暂无选中记录</h4>
        <p>从左侧列表选择一条记录</p>
      </div>
    `
    return
  }

  const statusMeta = getStatusMeta(selected.auditStatus)
  const remark = selected.auditRemark || ''
  const auditedAt = selected.auditedAt ? formatDate(selected.auditedAt) : '-'
  const reviewerText = selected.auditedBy ? `审核员 #${selected.auditedBy}` : '-'

  const isGoods = state.currentTab === 'goods'
  const itemId = isGoods ? selected.imageId : selected.userId
  const imageUrl = isGoods ? selected.originalImageUrl : selected.avatarUrl
  const title = isGoods
    ? escapeHtml(selected.goodsTitle || `商品 #${selected.goodsId}`)
    : escapeHtml(selected.nickname || `用户 #${selected.userId}`)
  const metaItems = isGoods ? `
    <div>
      <span>商品ID</span>
      <strong>${selected.goodsId ?? '-'}</strong>
    </div>
    <div>
      <span>卖家</span>
      <strong>${escapeHtml(selected.sellerNickname || `用户 ${selected.sellerId}`)}</strong>
    </div>
    <div>
      <span>图片序号</span>
      <strong>第 ${Number(selected.sortOrder || 0) + 1} 张</strong>
    </div>
    <div>
      <span>提交时间</span>
      <strong>${formatDate(selected.createdAt)}</strong>
    </div>
  ` : `
    <div>
      <span>用户ID</span>
      <strong>${selected.userId ?? '-'}</strong>
    </div>
    <div>
      <span>昵称</span>
      <strong>${escapeHtml(selected.nickname || '-')}</strong>
    </div>
    <div>
      <span>更新时间</span>
      <strong>${formatDate(selected.updatedAt)}</strong>
    </div>
  `

  els.detailContent.innerHTML = `
    <div class="detail-grid">
      <div class="image-stage">
        <div class="preview-frame">
          <img src="${escapeHtml(imageUrl || '')}" alt="待审核图片" referrerpolicy="no-referrer">
          <div class="preview-overlay">
            <span>#${itemId}</span>
            <span>${statusMeta.label}</span>
          </div>
        </div>
      </div>
      <div class="info-stage">
        <section class="detail-card">
          <div class="detail-header">
            <div>
              <h4 class="detail-title">${title}</h4>
            </div>
            <span class="audit-badge ${statusMeta.className}">${statusMeta.label}</span>
          </div>
          ${isGoods && selected.goodsDescription ? `<p class="detail-desc">${escapeHtml(selected.goodsDescription)}</p>` : ''}
          <a class="detail-link" href="${escapeHtml(imageUrl || '#')}" target="_blank" rel="noreferrer">查看原图</a>
          <div class="detail-meta">
            ${metaItems}
            <div>
              <span>审核时间</span>
              <strong>${auditedAt}</strong>
            </div>
            <div>
              <span>审核人</span>
              <strong>${reviewerText}</strong>
            </div>
          </div>
        </section>
        <section class="detail-card">
          <h4>审核操作</h4>
          <div class="detail-actions">
            <textarea id="remarkInput" placeholder="驳回原因（选填）">${escapeHtml(remark)}</textarea>
            <div class="action-row">
              <button id="approveBtn" class="button primary" type="button" ${state.actionLoading ? 'disabled' : ''}>${state.actionLoading ? '处理中...' : '通过'}</button>
              <button id="rejectBtn" class="button secondary" type="button" ${state.actionLoading ? 'disabled' : ''}>${state.actionLoading ? '处理中...' : '驳回'}</button>
            </div>
          </div>
        </section>
      </div>
    </div>
  `

  const image = els.detailContent.querySelector('img')
  if (image) {
    image.addEventListener('error', () => {
      image.alt = '加载失败'
      image.src = 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(`
        <svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
          <rect width="400" height="300" fill="#1f2937"/>
          <text x="200" y="150" font-size="16" fill="#9ca3af" text-anchor="middle" font-family="Arial">图片加载失败</text>
        </svg>
      `)
    }, { once: true })
  }

  document.querySelector('#approveBtn')?.addEventListener('click', approveSelected)
  document.querySelector('#rejectBtn')?.addEventListener('click', rejectSelected)
}

async function request(path, options = {}) {
  const auth = options.auth !== false
  const url = state.apiBaseUrl + path
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }

  if (auth && state.token) {
    headers.Authorization = `Bearer ${state.token}`
  }

  const response = await fetch(url, {
    method: options.method || 'GET',
    headers,
    body: options.body === null || options.body === undefined ? undefined : JSON.stringify(options.body)
  })

  let payload = null
  try {
    payload = await response.json()
  } catch (error) {
    payload = null
  }

  if (response.status === 401) {
    clearSession()
  }

  if (!response.ok || !payload?.success) {
    const message = payload?.message || `请求失败（${response.status}）`
    throw new Error(message)
  }

  return payload.data
}

function clearSession() {
  state.token = ''
  state.reviewer = null
  state.items = []
  state.total = 0
  state.page = 0
  state.selectedImageId = null

  localStorage.removeItem(STORAGE_KEYS.token)
  localStorage.removeItem(STORAGE_KEYS.reviewer)
}

function persistReviewer(reviewer) {
  localStorage.setItem(STORAGE_KEYS.reviewer, JSON.stringify(reviewer))
}

function setLoadingState(loading) {
  state.loading = loading
  els.emailInput.disabled = loading
  els.passwordInput.disabled = loading
  els.saveConfigBtn.disabled = loading
}

function getSelectedItem() {
  if (state.currentTab === 'goods') {
    return state.items.find(item => item.imageId === state.selectedImageId) || null
  }
  return state.items.find(item => item.userId === state.selectedImageId) || null
}

function getTotalPages() {
  return Math.max(1, Math.ceil(state.total / state.size))
}

function getStatusMeta(status) {
  return STATUS_META[status] || STATUS_META.PENDING
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function normalizeBaseUrl(value) {
  return (value || '').trim().replace(/\/+$/, '')
}

function parseJson(value) {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch (error) {
    return null
  }
}

function showToast(message, type = 'success') {
  if (!message) {
    return
  }

  els.toast.textContent = message
  els.toast.classList.add('is-visible')
  els.toast.classList.toggle('is-error', type === 'error')
  els.toast.classList.toggle('is-success', type === 'success')

  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    els.toast.classList.remove('is-visible')
  }, 2000)
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

