window.UI_MESSAGES = Object.freeze({
  LOGIN_REQUIRED: '请先登录',
  SESSION_EXPIRED: '登录已失效，请重新登录',
  NETWORK_ERROR: '网络连接异常，请稍后重试',
  LOAD_FAILED: '加载失败，请稍后重试',
  ACTION_FAILED: '操作失败，请稍后重试',
  REQUEST_FAILED: '请求失败，请稍后重试'
})

window.actionFailedMessage = function actionFailedMessage(subject) {
  return `${subject}失败，请稍后重试`
}

window.showToast = (function createToast() {
  let toastTimer = null
  return function showToast(message, type = 'success') {
    const toast = document.querySelector('#toast')
    if (!toast || !message) return

    toast.textContent = message
    toast.classList.add('is-visible')
    toast.classList.toggle('is-error', type === 'error')
    toast.classList.toggle('is-success', type === 'success')

    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => {
      toast.classList.remove('is-visible')
    }, 2200)
  }
})()
