const BEIJING_TIME_ZONE = 'Asia/Shanghai'

const state = {
  loading: false
}

const els = {
  form: document.querySelector('#announcementForm'),
  titleInput: document.querySelector('#titleInput'),
  contentInput: document.querySelector('#contentInput'),
  enabledInput: document.querySelector('#enabledInput'),
  titleCount: document.querySelector('#titleCount'),
  contentCount: document.querySelector('#contentCount'),
  revisionText: document.querySelector('#revisionText'),
  updatedAtText: document.querySelector('#updatedAtText'),
  reloadBtn: document.querySelector('#reloadBtn'),
  saveBtn: document.querySelector('#saveBtn')
}

boot()

function boot() {
  els.form.addEventListener('submit', handleSubmit)
  els.reloadBtn.addEventListener('click', loadAnnouncement)
  els.titleInput.addEventListener('input', updateCharacterCounts)
  els.contentInput.addEventListener('input', updateCharacterCounts)

  initAuth({
    onLoginSuccess: async () => {
      setFormEnabled(true)
      await loadAnnouncement()
    },
    onLogout: () => {
      setFormEnabled(false)
    }
  })

  setFormEnabled(false)

  if (authState.token) {
    hydrateAuthSession().then((ok) => {
      if (ok) {
        setFormEnabled(true)
        loadAnnouncement()
      }
    })
  }
}

async function loadAnnouncement() {
  if (!authState.token || state.loading) return
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
  if (!authState.token || state.loading) return

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

function setFormEnabled(enabled) {
  els.titleInput.disabled = !enabled
  els.contentInput.disabled = !enabled
  els.enabledInput.disabled = !enabled
  els.reloadBtn.disabled = !enabled
  els.saveBtn.disabled = !enabled
}

function setLoading(loading) {
  state.loading = loading
  setFormEnabled(!loading && Boolean(authState.token))
  els.saveBtn.textContent = loading ? '处理中...' : '保存公告'
}

function updateCharacterCounts() {
  els.titleCount.textContent = `${els.titleInput.value.length}/50`
  els.contentCount.textContent = `${els.contentInput.value.length}/1000`
}

function request(path, options = {}) {
  return authRequest(path, options)
}

function formatDate(value) {
  if (!value) return '-'

  const text = String(value).trim()
  const localDateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/)
  if (localDateTime) {
    const [, year, month, day, hour, minute] = localDateTime
    return `${year}/${month}/${day} ${hour}:${minute}`
  }

  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) return text
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
