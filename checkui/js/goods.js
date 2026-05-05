const STORAGE_KEYS = {
  apiBaseUrl: 'checkui:apiBaseUrl',
  token: 'checkui:token',
  reviewer: 'checkui:reviewer'
}

const DEFAULT_API_BASE_URL = 'http://YOUR_SERVER_IP/api/v1'

const state = {
  apiBaseUrl: normalizeBaseUrl(localStorage.getItem(STORAGE_KEYS.apiBaseUrl) || DEFAULT_API_BASE_URL),
  token: localStorage.getItem(STORAGE_KEYS.token) || '',
  reviewer: parseJson(localStorage.getItem(STORAGE_KEYS.reviewer)),
  items: [],
  total: 0,
  page: 0,
  size: 10,
  keyword: '',
  categoryId: '',
  status: '',
  loading: false,
  selectedIds: new Set(),
  deletingIds: new Set(),
  categories: [],
  modalCallback: null
}

const els = {
  sessionDot: document.querySelector('#sessionDot'),
  sessionTitle: document.querySelector('#sessionTitle'),
  loginInfo: document.querySelector('#loginInfo'),
  loginPrompt: document.querySelector('#loginPrompt'),
  logoutBtn: document.querySelector('#logoutBtn'),
  apiBaseUrl: document.querySelector('#apiBaseUrl'),
  saveConfigBtn: document.querySelector('#saveConfigBtn'),
  keywordInput: document.querySelector('#keywordInput'),
  categorySelect: document.querySelector('#categorySelect'),
  statusSelect: document.querySelector('#statusSelect'),
  searchBtn: document.querySelector('#searchBtn'),
  resetBtn: document.querySelector('#resetBtn'),
  totalCount: document.querySelector('#totalCount'),
  batchBar: document.querySelector('#batchBar'),
  batchCount: document.querySelector('#batchCount'),
  batchDeleteBtn: document.querySelector('#batchDeleteBtn'),
  clearSelectionBtn: document.querySelector('#clearSelectionBtn'),
  selectAllCheckbox: document.querySelector('#selectAllCheckbox'),
  goodsTableBody: document.querySelector('#goodsTableBody'),
  prevPageBtn: document.querySelector('#prevPageBtn'),
  nextPageBtn: document.querySelector('#nextPageBtn'),
  pageIndicator: document.querySelector('#pageIndicator'),
  confirmModal: document.querySelector('#confirmModal'),
  modalBody: document.querySelector('#modalBody'),
  modalCancel: document.querySelector('#modalCancel'),
  modalConfirm: document.querySelector('#modalConfirm'),
  toast: document.querySelector('#toast')
}

let toastTimer = null

boot()

function boot() {
  els.apiBaseUrl.value = state.apiBaseUrl

  els.saveConfigBtn.addEventListener('click', handleSaveConfig)
  els.logoutBtn.addEventListener('click', handleLogout)
  els.searchBtn.addEventListener('click', handleSearch)
  els.resetBtn.addEventListener('click', handleReset)
  els.prevPageBtn.addEventListener('click', handlePrevPage)
  els.nextPageBtn.addEventListener('click', handleNextPage)
  els.selectAllCheckbox.addEventListener('change', handleSelectAll)
  els.batchDeleteBtn.addEventListener('click', () => showBatchDeleteModal())
  els.clearSelectionBtn.addEventListener('click', clearSelection)
  els.modalCancel.addEventListener('click', hideModal)
  els.modalConfirm.addEventListener('click', handleModalConfirm)
  els.keywordInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') handleSearch() })

  renderSession()

  if (state.token) {
    hydrateSession()
  } else {
    renderTable()
  }
}

async function hydrateSession() {
  try {
    const reviewer = await request('/auth/me')
    state.reviewer = reviewer
    localStorage.setItem(STORAGE_KEYS.reviewer, JSON.stringify(reviewer))
    renderSession()
    await Promise.all([loadCategories(), loadGoods()])
  } catch (error) {
    clearSession()
    renderSession()
    renderTable()
    showToast(error.message || '登录已失效，请重新登录', 'error')
  }
}

function handleLogout() {
  clearSession()
  renderSession()
  state.items = []
  state.total = 0
  state.categories = []
  state.selectedIds.clear()
  renderTable()
  renderBatchBar()
  renderCategorySelect()
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

function handleSearch() {
  state.keyword = els.keywordInput.value.trim()
  state.categoryId = els.categorySelect.value
  state.status = els.statusSelect.value
  state.page = 0
  loadGoods()
}

function handleReset() {
  els.keywordInput.value = ''
  els.categorySelect.value = ''
  els.statusSelect.value = ''
  state.keyword = ''
  state.categoryId = ''
  state.status = ''
  state.page = 0
  loadGoods()
}

function handlePrevPage() {
  if (state.page === 0 || state.loading) return
  state.page -= 1
  loadGoods()
}

function handleNextPage() {
  if (state.loading) return
  const totalPages = getTotalPages()
  if (state.page >= totalPages - 1) return
  state.page += 1
  loadGoods()
}

function handleSelectAll(event) {
  if (event.target.checked) {
    state.items.forEach(item => state.selectedIds.add(item.id))
  } else {
    state.items.forEach(item => state.selectedIds.delete(item.id))
  }
  renderTable()
  renderBatchBar()
}

function handleItemCheckboxChange(id, checked) {
  if (checked) {
    state.selectedIds.add(id)
  } else {
    state.selectedIds.delete(id)
  }
  renderTable()
  renderBatchBar()
}

function clearSelection() {
  state.selectedIds.clear()
  renderTable()
  renderBatchBar()
}

function showDeleteModal(goodsId, title) {
  state.modalCallback = () => deleteSingle(goodsId)
  els.modalBody.textContent = `确定要删除商品「${title || `#${goodsId}`}」吗？此操作不可撤销。`
  showModal()
}

function showBatchDeleteModal() {
  if (state.selectedIds.size === 0) return
  state.modalCallback = () => deleteBatch()
  els.modalBody.textContent = `确定要删除选中的 ${state.selectedIds.size} 件商品吗？此操作不可撤销。`
  showModal()
}

function showModal() {
  els.confirmModal.classList.add('is-visible')
}

function hideModal() {
  els.confirmModal.classList.remove('is-visible')
  state.modalCallback = null
}

async function handleModalConfirm() {
  if (!state.modalCallback) return
  const callback = state.modalCallback
  hideModal()
  await callback()
}

async function deleteSingle(goodsId) {
  if (!state.token) {
    showToast('请先登录', 'error')
    return
  }

  state.deletingIds.add(goodsId)
  renderTable()

  try {
    await request(`/goods/${goodsId}`, { method: 'DELETE', body: null })
    // 静默移除
    const index = state.items.findIndex(item => item.id === goodsId)
    if (index !== -1) {
      state.items.splice(index, 1)
      state.total -= 1
      state.selectedIds.delete(goodsId)
    }
    showToast('删除成功', 'success')
    renderTable()
    renderBatchBar()
    updatePagination()
  } catch (error) {
    showToast(error.message || '删除失败', 'error')
  } finally {
    state.deletingIds.delete(goodsId)
    renderTable()
  }
}

async function deleteBatch() {
  if (!state.token || state.selectedIds.size === 0) return

  const idsToDelete = Array.from(state.selectedIds)
  idsToDelete.forEach(id => state.deletingIds.add(id))
  renderTable()

  let successCount = 0
  let failCount = 0

  for (const goodsId of idsToDelete) {
    try {
      await request(`/goods/${goodsId}`, { method: 'DELETE', body: null })
      const index = state.items.findIndex(item => item.id === goodsId)
      if (index !== -1) {
        state.items.splice(index, 1)
        state.total -= 1
      }
      state.selectedIds.delete(goodsId)
      successCount++
    } catch (error) {
      failCount++
      state.deletingIds.delete(goodsId)
    }
  }

  state.deletingIds.clear()
  renderTable()
  renderBatchBar()
  updatePagination()

  if (failCount === 0) {
    showToast(`成功删除 ${successCount} 件商品`, 'success')
  } else {
    showToast(`删除完成：成功 ${successCount} 件，失败 ${failCount} 件`, 'error')
  }
}

async function loadCategories() {
  try {
    const categories = await request('/categories', { auth: false })
    state.categories = Array.isArray(categories) ? categories : []
    renderCategorySelect()
  } catch (error) {
    state.categories = []
  }
}

async function loadGoods() {
  if (!state.token) {
    state.items = []
    state.total = 0
    renderTable()
    return
  }

  state.loading = true
  renderTable()

  try {
    const params = new URLSearchParams({
      page: String(state.page),
      size: String(state.size)
    })
    if (state.keyword) params.set('keyword', state.keyword)
    if (state.categoryId) params.set('categoryId', state.categoryId)
    if (state.status) params.set('status', state.status)

    const pageData = await request(`/goods?${params.toString()}`, { auth: false })
    state.items = Array.isArray(pageData.items) ? pageData.items : []
    state.total = pageData.total || 0
    state.page = pageData.page || 0
    state.size = pageData.size || state.size
    renderTable()
    updatePagination()
  } catch (error) {
    state.items = []
    state.total = 0
    renderTable(error.message || '加载失败')
    showToast(error.message || '加载失败', 'error')
  } finally {
    state.loading = false
    renderTable()
  }
}

function renderSession() {
  const online = Boolean(state.token && state.reviewer)
  els.sessionDot.classList.toggle('is-online', online)
  els.sessionTitle.textContent = online
    ? (state.reviewer.nickname || state.reviewer.email || '管理员')
    : '未登录'

  els.loginInfo.style.display = online ? 'flex' : 'none'
  els.loginPrompt.style.display = online ? 'none' : 'flex'
}

function renderCategorySelect() {
  const currentValue = els.categorySelect.value
  const options = state.categories.map(cat =>
    `<option value="${cat.id}">${escapeHtml(cat.name)}</option>`
  ).join('')
  els.categorySelect.innerHTML = `<option value="">全部分类</option>${options}`
  els.categorySelect.value = currentValue || state.categoryId || ''
}

function renderTable(message) {
  els.totalCount.textContent = String(state.total)
  els.selectAllCheckbox.checked = state.items.length > 0 && state.items.every(item => state.selectedIds.has(item.id))
  els.selectAllCheckbox.indeterminate = state.items.some(item => state.selectedIds.has(item.id)) && !state.items.every(item => state.selectedIds.has(item.id))

  if (!state.token) {
    els.goodsTableBody.innerHTML = `
      <tr>
        <td colspan="9" class="empty-table">
          <div style="padding:24px;">
            <h4 style="margin:0 0 8px;">请先登录</h4>
            <p style="margin:0;">登录后可查看商品列表</p>
          </div>
        </td>
      </tr>
    `
    return
  }

  if (state.loading && state.items.length === 0) {
    els.goodsTableBody.innerHTML = `
      <tr>
        <td colspan="9" class="empty-table">
          <div style="padding:24px;">
            <h4 style="margin:0 0 8px;">加载中...</h4>
          </div>
        </td>
      </tr>
    `
    return
  }

  if (!state.items.length) {
    els.goodsTableBody.innerHTML = `
      <tr>
        <td colspan="9" class="empty-table">
          <div style="padding:24px;">
            <h4 style="margin:0 0 8px;">${message || '暂无数据'}</h4>
            <p style="margin:0;">可切换筛选条件或刷新</p>
          </div>
        </td>
      </tr>
    `
    return
  }

  els.goodsTableBody.innerHTML = state.items.map(item => {
    const isSelected = state.selectedIds.has(item.id)
    const isDeleting = state.deletingIds.has(item.id)
    const statusMeta = getStatusMeta(item.status)
    const imageUrl = item.imageUrls && item.imageUrls.length > 0 ? item.imageUrls[0] : ''
    const categoryName = item.category ? item.category.name : '-'
    const seller = item.seller || {}

    return `
      <tr class="${isDeleting ? 'is-deleting' : ''}" data-goods-id="${item.id}">
        <td class="col-checkbox">
          <input type="checkbox" ${isSelected ? 'checked' : ''} ${isDeleting ? 'disabled' : ''} data-checkbox-id="${item.id}">
        </td>
        <td class="col-id">${item.id}</td>
        <td>
          <div class="goods-title-cell">
            ${imageUrl ? `<img src="${escapeHtml(imageUrl)}" alt="" class="goods-image-thumb" onerror="this.style.display='none'">` : '<div class="goods-image-thumb" style="background:#f3f4f6;"></div>'}
            <span class="goods-title-text" title="${escapeHtml(item.title)}">${escapeHtml(item.title)}</span>
          </div>
        </td>
        <td class="col-price">¥${formatPrice(item.price)}</td>
        <td class="col-status">
          <span class="status-badge ${statusMeta.className}">${statusMeta.label}</span>
        </td>
        <td class="col-category">${escapeHtml(categoryName)}</td>
        <td>
          <div class="seller-info">
            ${seller.avatarUrl ? `<img src="${escapeHtml(seller.avatarUrl)}" alt="" class="seller-avatar" onerror="this.style.display='none'">` : '<div class="seller-avatar" style="background:#e5e7eb;"></div>'}
            <span>${escapeHtml(seller.nickname || seller.email || `用户 #${seller.id}`)}</span>
          </div>
        </td>
        <td class="col-date">${formatDate(item.createdAt)}</td>
        <td class="col-actions">
          <button class="btn-icon danger" type="button" ${isDeleting ? 'disabled' : ''} data-delete-id="${item.id}" data-delete-title="${escapeHtml(item.title)}">
            ${isDeleting ? '删除中...' : '删除'}
          </button>
        </td>
      </tr>
    `
  }).join('')

  // 绑定事件
  els.goodsTableBody.querySelectorAll('[data-checkbox-id]').forEach(cb => {
    cb.addEventListener('change', (e) => {
      handleItemCheckboxChange(Number(e.target.dataset.checkboxId), e.target.checked)
    })
  })

  els.goodsTableBody.querySelectorAll('[data-delete-id]').forEach(btn => {
    btn.addEventListener('click', () => {
      showDeleteModal(Number(btn.dataset.deleteId), btn.dataset.deleteTitle)
    })
  })
}

function renderBatchBar() {
  const count = state.selectedIds.size
  els.batchBar.classList.toggle('hidden', count === 0)
  els.batchCount.textContent = String(count)
  els.batchDeleteBtn.disabled = state.deletingIds.size > 0
}

function updatePagination() {
  const totalPages = getTotalPages()
  els.pageIndicator.textContent = `第 ${Math.min(state.page + 1, totalPages)} 页`
  els.prevPageBtn.disabled = state.loading || state.page === 0
  els.nextPageBtn.disabled = state.loading || state.page >= totalPages - 1 || state.items.length === 0
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
    renderSession()
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
  localStorage.removeItem(STORAGE_KEYS.token)
  localStorage.removeItem(STORAGE_KEYS.reviewer)
}

function getTotalPages() {
  return Math.max(1, Math.ceil(state.total / state.size))
}

function getStatusMeta(status) {
  const map = {
    ON_SALE: { label: '在售中', className: 'on-sale' },
    OFF_SHELF: { label: '已下架', className: 'off-shelf' }
  }
  return map[status] || { label: status || '-', className: '' }
}

function formatPrice(value) {
  if (value == null) return '-'
  return Number(value).toFixed(2)
}

function formatDate(value) {
  if (!value) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
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
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch (error) {
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
