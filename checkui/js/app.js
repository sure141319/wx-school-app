const STORAGE_KEYS = {
  remarkHistory: 'checkui:remarkHistory'
}

const STATUS_META = {
  PENDING: { label: '待审核', className: 'pending' },
  APPROVED: { label: '已通过', className: 'approved' },
  REJECTED: { label: '已驳回', className: 'rejected' }
}

const BEIJING_TIME_ZONE = 'Asia/Shanghai'

const state = {
  items: [],
  filteredItems: [],
  total: 0,
  page: 0,
  size: 10,
  status: 'PENDING',
  currentTab: 'goods',
  selectedImageId: null,
  loading: false,
  actionLoading: false,
  searchQuery: '',
  remarkHistory: parseJson(localStorage.getItem(STORAGE_KEYS.remarkHistory)) || [],
  sellerContactCache: new Map()
}

const els = {
  typeFilters: document.querySelector('#typeFilters'),
  statusFilters: document.querySelector('#statusFilters'),
  refreshBtn: document.querySelector('#refreshBtn'),
  pageSizeSelect: document.querySelector('#pageSizeSelect'),
  approveAllBtn: document.querySelector('#approveAllBtn'),
  rejectAllBtn: document.querySelector('#rejectAllBtn'),
  searchInput: document.querySelector('#searchInput'),
  clearSearchBtn: document.querySelector('#clearSearchBtn'),
  queueMeta: document.querySelector('#queueMeta'),
  queueList: document.querySelector('#queueList'),
  prevPageBtn: document.querySelector('#prevPageBtn'),
  nextPageBtn: document.querySelector('#nextPageBtn'),
  pageIndicator: document.querySelector('#pageIndicator'),
  detailContent: document.querySelector('#detailContent'),
  imagePreviewModal: document.querySelector('#imagePreviewModal'),
  previewImage: document.querySelector('#previewImage'),
  closePreviewBtn: document.querySelector('#closePreviewBtn')
}

boot()

function boot() {
  els.pageSizeSelect.value = String(state.size)

  els.refreshBtn.addEventListener('click', () => loadQueue({ keepSelection: true }))
  els.pageSizeSelect.addEventListener('change', handlePageSizeChange)
  els.approveAllBtn.addEventListener('click', handleApproveAll)
  els.rejectAllBtn.addEventListener('click', handleRejectAll)
  els.prevPageBtn.addEventListener('click', handlePrevPage)
  els.nextPageBtn.addEventListener('click', handleNextPage)
  els.typeFilters.addEventListener('click', handleTypeFilter)
  els.statusFilters.addEventListener('click', handleStatusFilter)
  els.searchInput.addEventListener('input', handleSearchInput)
  els.clearSearchBtn.addEventListener('click', clearSearch)
  els.closePreviewBtn.addEventListener('click', closeImagePreview)
  els.imagePreviewModal.querySelector('.modal-backdrop').addEventListener('click', closeImagePreview)
  document.addEventListener('keydown', handleKeyboardShortcut)

  initAuth({
    onLoginSuccess: async () => {
      await loadQueue()
    },
    onLogout: () => {
      state.items = []
      state.filteredItems = []
      state.total = 0
      state.selectedImageId = null
      renderQueue()
      renderDetail()
    }
  })

  renderFilterState()
  renderTypeFilterState()
  renderQueue()
  renderDetail()

  if (authState.token) {
    hydrateAuthSession().then((ok) => {
      if (ok) loadQueue()
    })
  }
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

function handleSearchInput() {
  state.searchQuery = els.searchInput.value.trim().toLowerCase()
  els.clearSearchBtn.hidden = !state.searchQuery
  filterAndRenderItems()
}

function clearSearch() {
  els.searchInput.value = ''
  state.searchQuery = ''
  els.clearSearchBtn.hidden = true
  filterAndRenderItems()
}

function filterAndRenderItems() {
  if (!state.searchQuery) {
    state.filteredItems = [...state.items]
  } else {
    state.filteredItems = state.items.filter(item => {
      if (state.currentTab === 'goods') {
        const title = (item.goodsTitle || '').toLowerCase()
        const goodsId = String(item.goodsId || '')
        const seller = (item.sellerNickname || '').toLowerCase()
        const sellerEmail = (item.sellerEmail || '').toLowerCase()
        const imageId = String(item.imageId || '')
        return title.includes(state.searchQuery) ||
               goodsId.includes(state.searchQuery) ||
               seller.includes(state.searchQuery) ||
               sellerEmail.includes(state.searchQuery) ||
               imageId.includes(state.searchQuery)
      } else {
        const nickname = (item.nickname || '').toLowerCase()
        const userId = String(item.userId || '')
        return nickname.includes(state.searchQuery) ||
               userId.includes(state.searchQuery)
      }
    })
  }
  renderQueue()
}

function handleKeyboardShortcut(event) {
  if (!authState.token || state.actionLoading) return

  const activeElement = document.activeElement
  const isInputFocused = activeElement.tagName === 'INPUT' ||
                         activeElement.tagName === 'TEXTAREA' ||
                         activeElement.tagName === 'SELECT'

  if (isInputFocused) return

  const previewOpen = !els.imagePreviewModal.hidden
  if (previewOpen) {
    if (event.key === 'Escape') {
      closeImagePreview()
      event.preventDefault()
    }
    return
  }

  switch (event.key) {
    case 'ArrowLeft':
      navigateItem(-1)
      event.preventDefault()
      break
    case 'ArrowRight':
      navigateItem(1)
      event.preventDefault()
      break
    case 'a':
    case 'A':
      approveSelected()
      event.preventDefault()
      break
    case 'r':
    case 'R':
      const remarkInput = document.querySelector('#remarkInput')
      if (remarkInput) {
        remarkInput.focus()
        remarkInput.select()
      }
      event.preventDefault()
      break
    case ' ':
      openImagePreview()
      event.preventDefault()
      break
    case 'Escape':
      const remark = document.querySelector('#remarkInput')
      if (remark === activeElement) {
        remark.blur()
        event.preventDefault()
      }
      break
  }
}

function navigateItem(direction) {
  const items = state.filteredItems
  if (!items.length) return

  const getItemId = (item) => state.currentTab === 'goods' ? item.imageId : item.userId
  const currentIndex = items.findIndex(item => getItemId(item) === state.selectedImageId)

  let newIndex = currentIndex + direction
  if (newIndex < 0) newIndex = items.length - 1
  if (newIndex >= items.length) newIndex = 0

  state.selectedImageId = getItemId(items[newIndex])
  renderQueue()
  renderDetail()
}

function openImagePreview() {
  const selected = getSelectedItem()
  if (!selected) return

  const imageUrl = state.currentTab === 'goods'
    ? (selected.previewImageUrl || selected.originalImageUrl)
    : selected.avatarUrl
  if (!imageUrl) return

  els.previewImage.src = imageUrl
  els.imagePreviewModal.hidden = false
  document.body.style.overflow = 'hidden'
}

function closeImagePreview() {
  els.imagePreviewModal.hidden = true
  document.body.style.overflow = ''
}

function saveRemarkToHistory(remark) {
  if (!remark || !remark.trim()) return

  const history = state.remarkHistory.filter(r => r !== remark)
  history.unshift(remark.trim())
  state.remarkHistory = history.slice(0, 10)
  localStorage.setItem(STORAGE_KEYS.remarkHistory, JSON.stringify(state.remarkHistory))
}

async function handleApproveAll() {
  if (!authState.token || state.actionLoading) {
    return
  }

  const confirmed = confirm(`确定要通过全部 ${state.total} 张已驳回图片吗？此操作将重新审核全部已驳回记录。`)
  if (!confirmed) {
    return
  }

  state.actionLoading = true
  els.approveAllBtn.disabled = true
  els.approveAllBtn.textContent = '处理中...'

  try {
    const result = await request('/audit/images/approve-all-rejected', {
      method: 'POST',
      body: {
        confirmation: 'APPROVE_ALL_REJECTED'
      }
    })
    showToast(`已通过 ${result} 张图片`, 'success')
    state.page = 0
    await loadQueue()
  } catch (error) {
    showToast(error.message || UI_MESSAGES.ACTION_FAILED, 'error')
  } finally {
    state.actionLoading = false
    els.approveAllBtn.disabled = false
    els.approveAllBtn.textContent = '全部通过'
  }
}

async function handleRejectAll() {
  if (!authState.token || state.actionLoading) {
    return
  }

  const confirmed = confirm(`确定要驳回全部 ${state.total} 张已通过的图片吗？此操作不可撤销。`)
  if (!confirmed) {
    return
  }

  state.actionLoading = true
  els.rejectAllBtn.disabled = true
  els.rejectAllBtn.textContent = '处理中...'

  try {
    const result = await request('/audit/images/reject-all-approved', {
      method: 'POST',
      body: {
        confirmation: 'REJECT_ALL_APPROVED'
      }
    })
    showToast(`已驳回 ${result} 张图片`, 'success')
    state.page = 0
    await loadQueue()
  } catch (error) {
    showToast(error.message || UI_MESSAGES.ACTION_FAILED, 'error')
  } finally {
    state.actionLoading = false
    els.rejectAllBtn.disabled = false
    els.rejectAllBtn.textContent = '全部驳回'
  }
}

async function loadQueue(options = {}) {
  if (!authState.token) {
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

    await hydrateSellerContacts()
    filterAndRenderItems()

    const getItemId = (item) => state.currentTab === 'goods' ? item.imageId : item.userId
    const hasPreviousSelection = keepSelection && state.filteredItems.some(item => getItemId(item) === previousSelection)
    state.selectedImageId = hasPreviousSelection ? previousSelection : (state.filteredItems.length > 0 ? getItemId(state.filteredItems[0]) : null)
  } catch (error) {
    state.items = []
    state.filteredItems = []
    state.total = 0
    state.selectedImageId = null
    errorMessage = error.message || UI_MESSAGES.LOAD_FAILED
    showToast(errorMessage, 'error')
  } finally {
    state.loading = false
    renderQueue(errorMessage)
    renderDetail()
  }
}

async function hydrateSellerContacts() {
  if (state.currentTab !== 'goods' || !state.items.length) {
    return
  }

  const goodsIds = Array.from(new Set(
    state.items
      .map(item => item.goodsId)
      .filter(goodsId => goodsId !== null && goodsId !== undefined)
      .map(goodsId => String(goodsId))
  ))

  await Promise.all(goodsIds.map(async goodsId => {
    const existingContact = sellerContactFromAuditItem(
      state.items.find(item => String(item.goodsId) === goodsId)
    )
    let sellerContact = mergeSellerContact(state.sellerContactCache.get(goodsId), existingContact)

    if (!hasCompleteSellerContact(sellerContact)) {
      try {
        const detail = await request(`/goods/${encodeURIComponent(goodsId)}`)
        sellerContact = mergeSellerContact(sellerContact, sellerContactFromGoodsDetail(detail))
      } catch (_error) {
        // Keep any contact fields already returned by the audit list; retry details on refresh.
      }
    }

    if (hasAnySellerContact(sellerContact)) {
      state.sellerContactCache.set(goodsId, sellerContact)
      applySellerContact(goodsId, sellerContact)
    } else {
      state.sellerContactCache.delete(goodsId)
    }
  }))
}

function sellerContactFromAuditItem(item) {
  return {
    email: normalizeContactField(item?.sellerEmail),
    wechatId: normalizeContactField(item?.sellerWechatId),
    qq: normalizeContactField(item?.sellerQq)
  }
}

function sellerContactFromGoodsDetail(detail) {
  const seller = detail?.seller || {}
  const email = normalizeContactField(seller.email || detail?.sellerEmail)
  const qq = normalizeContactField(seller.qq || detail?.sellerQq) || deriveQqFromEmail(email)
  return {
    email,
    wechatId: normalizeContactField(seller.wechatId || detail?.sellerWechatId),
    qq
  }
}

function mergeSellerContact(base = {}, next = {}) {
  return {
    email: normalizeContactField(next.email) || normalizeContactField(base.email),
    wechatId: normalizeContactField(next.wechatId) || normalizeContactField(base.wechatId),
    qq: normalizeContactField(next.qq) || normalizeContactField(base.qq)
  }
}

function applySellerContact(goodsId, contact) {
  state.items.forEach(item => {
    if (String(item.goodsId) !== goodsId) {
      return
    }
    if (contact.email) {
      item.sellerEmail = contact.email
    }
    if (contact.wechatId) {
      item.sellerWechatId = contact.wechatId
    }
    if (contact.qq) {
      item.sellerQq = contact.qq
    }
  })
}

function hasAnySellerContact(contact) {
  return Boolean(contact?.email || contact?.wechatId || contact?.qq)
}

function hasCompleteSellerContact(contact) {
  return Boolean(contact?.email && contact?.wechatId && contact?.qq)
}

function normalizeContactField(value) {
  return String(value ?? '').trim()
}

function deriveQqFromEmail(email) {
  const match = normalizeContactField(email).match(/^(\d{5,12})@qq\.com$/i)
  return match ? match[1] : ''
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
    showToast(error.message || UI_MESSAGES.ACTION_FAILED, 'error')
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
    if (remark) {
      saveRemarkToHistory(remark)
    }
    const label = state.currentTab === 'goods' ? `图片 #${selected.imageId}` : `用户 #${selected.userId} 头像`
    showToast(`${label} 已驳回`, 'success')
    await loadQueue()
  } catch (error) {
    showToast(error.message || UI_MESSAGES.ACTION_FAILED, 'error')
  } finally {
    state.actionLoading = false
    renderDetail()
  }
}

function selectImage(imageId) {
  state.selectedImageId = imageId
  renderQueue()
  renderDetail()
  // 移动端选中后自动滚动到详情面板，避免详情被挤到屏幕外
  if (window.matchMedia('(max-width: 640px)').matches) {
    const detailPanel = document.querySelector('.detail-panel')
    if (detailPanel) {
      detailPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
}

function renderTypeFilterState() {
  Array.from(els.typeFilters.querySelectorAll('[data-type]')).forEach(button => {
    const active = button.dataset.type === state.currentTab
    button.classList.toggle('is-active', active)
    button.setAttribute('aria-pressed', String(active))
  })
  const titleMap = {
    goods: '商品图片审核台',
    avatar: '头像审核台'
  }
  document.querySelector('#pageTitle').textContent = titleMap[state.currentTab] || '图片审核台'
}

function renderFilterState() {
  Array.from(els.statusFilters.querySelectorAll('[data-status]')).forEach(button => {
    const active = button.dataset.status === state.status
    button.classList.toggle('is-active', active)
    button.setAttribute('aria-pressed', String(active))
  })
  els.approveAllBtn.hidden = state.currentTab !== 'goods' || state.status !== 'REJECTED'
  els.rejectAllBtn.hidden = state.currentTab !== 'goods' || state.status !== 'APPROVED'
}

function renderQueue(message) {
  renderFilterState()

  const totalPages = getTotalPages()
  els.pageIndicator.textContent = `第 ${Math.min(state.page + 1, totalPages)} 页`
  els.prevPageBtn.disabled = state.loading || state.page === 0
  els.nextPageBtn.disabled = state.loading || state.page >= totalPages - 1

  if (!authState.token) {
    els.queueMeta.textContent = UI_MESSAGES.LOGIN_REQUIRED
    els.queueList.innerHTML = `
      <div class="empty-queue">
        <h4>等待登录</h4>
        <p>登录后自动加载待审核图片</p>
      </div>
    `
    return
  }

  const filteredCount = state.filteredItems.length
  const totalCount = state.items.length
  const start = state.total === 0 ? 0 : state.page * state.size + 1
  const end = Math.min((state.page + 1) * state.size, state.total)
  const metaText = state.searchQuery
    ? `筛选 ${filteredCount}/${totalCount} 条`
    : (message || `${start}-${end} / 共 ${state.total} 条`)
  els.queueMeta.textContent = metaText

  if (state.loading) {
    els.queueList.innerHTML = `
      <div class="loading-state">
        <h4>加载中...</h4>
      </div>
    `
    return
  }

  if (!state.filteredItems.length) {
    els.queueList.innerHTML = `
      <div class="empty-queue">
        <h4>${state.searchQuery ? '无匹配结果' : '暂无数据'}</h4>
        <p>${state.searchQuery ? '尝试其他搜索词' : '可切换筛选条件或刷新'}</p>
      </div>
    `
    return
  }

  els.queueList.innerHTML = state.filteredItems.map(item => {
    const meta = getStatusMeta(item.auditStatus)
    const itemId = state.currentTab === 'goods' ? item.imageId : item.userId
    const selectedClass = itemId === state.selectedImageId ? 'is-selected' : ''
    
    if (state.currentTab === 'goods') {
      return `
        <button class="queue-item ${selectedClass}" type="button" data-item-id="${itemId}" aria-current="${itemId === state.selectedImageId ? 'true' : 'false'}">
          <div class="queue-item-top">
            <div>
              <div class="queue-title">${escapeHtml(item.goodsTitle || `商品 #${item.goodsId}`)}</div>
              <div class="queue-subtitle">${escapeHtml(item.sellerEmail || item.sellerNickname || `用户 ${item.sellerId}`)}</div>
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
      <button class="queue-item ${selectedClass}" type="button" data-item-id="${itemId}" aria-current="${itemId === state.selectedImageId ? 'true' : 'false'}">
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

  if (!authState.token) {
    els.detailContent.innerHTML = `
      <div class="empty-state">
        <h4>${UI_MESSAGES.LOGIN_REQUIRED}</h4>
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

  const isGoods = state.currentTab === 'goods'
  const itemId = isGoods ? selected.imageId : selected.userId
  const imageUrl = isGoods
    ? (selected.previewImageUrl || selected.originalImageUrl)
    : selected.avatarUrl
  const originalImageUrl = isGoods ? selected.originalImageUrl : selected.avatarUrl
  const originalImageHref = escapeHtml(originalImageUrl || '#')
  const qq = isGoods ? selected.sellerQq : selected.qq
  const wechatId = isGoods ? selected.sellerWechatId : selected.wechatId
  const title = isGoods
    ? escapeHtml(selected.goodsTitle || `商品 #${selected.goodsId}`)
    : escapeHtml(selected.nickname || `用户 #${selected.userId}`)
  const metaItems = isGoods ? `
    <div>
      <span>商品ID</span>
      <div class="meta-value-row">
        <strong>${selected.goodsId ?? '-'}</strong>
        <a class="detail-link meta-link" href="${originalImageHref}" target="_blank" rel="noreferrer">查看原图</a>
      </div>
    </div>
    <div>
      <span>卖家邮箱</span>
      <strong>${escapeHtml(selected.sellerEmail || selected.sellerNickname || `用户 ${selected.sellerId}`)}</strong>
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
      <div class="meta-value-row">
        <strong>${selected.userId ?? '-'}</strong>
        <a class="detail-link meta-link" href="${originalImageHref}" target="_blank" rel="noreferrer">查看原图</a>
      </div>
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
          <img src="${escapeHtml(imageUrl || '')}" alt="待审核图片" loading="lazy" decoding="async" referrerpolicy="no-referrer">
          <div class="preview-overlay">
            <span>#${itemId}</span>
            <span>${statusMeta.label}</span>
          </div>
        </div>
      </div>
      <div class="info-stage">
        <section class="detail-card info-card">
          <div class="detail-header">
            <div>
              <h4 class="detail-title">${title}</h4>
            </div>
            <span class="audit-badge ${statusMeta.className}">${statusMeta.label}</span>
          </div>
          ${isGoods && selected.goodsDescription ? `<p class="detail-desc">${escapeHtml(selected.goodsDescription)}</p>` : ''}
          <div class="detail-meta">
            ${metaItems}
            <div>
              <span>QQ</span>
              <strong>${formatContactValue(qq)}</strong>
            </div>
            <div>
              <span>微信号</span>
              <strong>${formatContactValue(wechatId)}</strong>
            </div>
          </div>
        </section>
        <section class="detail-card action-card">
          <h4>审核操作 <kbd class="hint-kbd">A</kbd> <kbd class="hint-kbd">R</kbd></h4>
          <div class="detail-actions">
            <div class="remark-area">
              <textarea id="remarkInput" placeholder="驳回原因（选填）">${escapeHtml(remark)}</textarea>
              ${state.remarkHistory.length ? `
                <div class="remark-history">
                  ${state.remarkHistory.map(r => `<button class="remark-history-item" type="button" data-remark="${escapeHtml(r)}">${escapeHtml(r)}</button>`).join('')}
                </div>
              ` : ''}
            </div>
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
      image.alt = '图片加载失败'
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

  document.querySelectorAll('.remark-history-item').forEach(btn => {
    btn.addEventListener('click', () => {
      const remarkInput = document.querySelector('#remarkInput')
      if (remarkInput) {
        remarkInput.value = btn.dataset.remark
        remarkInput.focus()
      }
    })
  })

  const previewFrame = els.detailContent.querySelector('.preview-frame')
  if (previewFrame) {
    previewFrame.style.cursor = 'pointer'
    previewFrame.addEventListener('click', openImagePreview)
  }
}

function request(path, options = {}) {
  return authRequest(path, options)
}

function getSelectedItem() {
  if (state.currentTab === 'goods') {
    return state.filteredItems.find(item => item.imageId === state.selectedImageId) || null
  }
  return state.filteredItems.find(item => item.userId === state.selectedImageId) || null
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

  const text = String(value).trim()
  const localDateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/)
  if (localDateTime) {
    const [, , month, day, hour, minute] = localDateTime
    return `${month}/${day} ${hour}:${minute}`
  }

  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: BEIJING_TIME_ZONE,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(parsed)
  return `${getDatePart(parts, 'month')}/${getDatePart(parts, 'day')} ${getDatePart(parts, 'hour')}:${getDatePart(parts, 'minute')}`
}

function getDatePart(parts, type) {
  const part = parts.find(item => item.type === type)
  return part ? part.value : ''
}

function formatContactValue(value) {
  const normalized = String(value ?? '').trim()
  return escapeHtml(normalized || '未填写')
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

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
