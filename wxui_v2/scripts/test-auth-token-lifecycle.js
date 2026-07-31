const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent, loadTsModule } = require('./test-support/runtime')

function createRequestHarness(initialStorage = {}) {
  const storage = new Map(Object.entries(initialStorage))
  const calls = {
    requests: [],
    redirects: [],
    removed: [],
    storageWrites: [],
    toasts: []
  }
  const timers = []
  const wx = {
    getStorageSync: key => storage.get(key),
    setStorageSync(key, value) {
      storage.set(key, value)
      calls.storageWrites.push({ key, value })
    },
    removeStorageSync(key) {
      storage.delete(key)
      calls.removed.push(key)
    },
    request(options) {
      calls.requests.push(options)
    },
    showToast(options) {
      calls.toasts.push(options)
    },
    redirectTo(options) {
      calls.redirects.push(options)
      if (options.success) options.success()
    }
  }
  const api = loadTsModule('utils/request.ts', {
    globals: {
      wx,
      getCurrentPages: () => [],
      setTimeout(callback, delay) {
        timers.push({ callback, delay })
        return timers.length
      }
    }
  })

  return {
    api,
    calls,
    storage,
    runTimer(delay) {
      const index = timers.findIndex(timer => timer.delay === delay)
      assert.notEqual(index, -1, `expected a ${delay}ms timer`)
      const [timer] = timers.splice(index, 1)
      timer.callback()
    }
  }
}

test('令牌读写和退出会同步内存与本地存储', () => {
  const harness = createRequestHarness({ token: 'stored-token', user: 'profile' })

  assert.equal(harness.api.getToken(), 'stored-token')
  harness.api.setToken('new-token')
  assert.equal(harness.storage.get('token'), 'new-token')
  assert.equal(harness.api.getToken(), 'new-token')

  harness.api.clearToken()
  assert.equal(harness.api.getToken(), undefined)
  assert.equal(harness.storage.has('token'), false)
  assert.equal(harness.storage.has('user'), false)
})

test('普通请求携带 Bearer 令牌并返回成功响应', async () => {
  const harness = createRequestHarness({ token: 'token-1' })
  const pending = harness.api.request({
    url: 'https://api.example.test/goods',
    header: { 'X-Trace': 'trace-1' }
  })
  const request = harness.calls.requests[0]

  assert.equal(request.method, 'GET')
  assert.equal(request.header.Authorization, 'Bearer token-1')
  assert.equal(request.header['X-Trace'], 'trace-1')

  request.success({ statusCode: 200, data: { success: true } })
  assert.equal((await pending).statusCode, 200)
})

test('非认证接口 401 使用后端消息、清理会话并延迟跳转', async () => {
  const harness = createRequestHarness({ token: 'expired-token', user: 'profile' })
  const pending = harness.api.request({ url: 'https://api.example.test/goods' })
  const rejection = assert.rejects(pending, /账号已在其他设备登录/)

  harness.calls.requests[0].success({
    statusCode: 401,
    data: { code: 'AUTH_TOKEN_INVALID', message: '账号已在其他设备登录' }
  })
  await rejection

  assert.deepEqual(harness.calls.removed, ['token', 'user'])
  assert.equal(harness.calls.toasts[0].title, '账号已在其他设备登录')
  assert.equal(harness.calls.redirects.length, 0)

  harness.runTimer(800)
  assert.equal(harness.calls.redirects[0].url, '/pages/auth/auth')
})

test('旧请求迟到的 401 不会清除新登录令牌', async () => {
  const harness = createRequestHarness({ token: 'old-token', user: 'profile' })
  const pending = harness.api.request({ url: 'https://api.example.test/goods' })
  const rejection = assert.rejects(pending)
  harness.api.setToken('new-token')

  harness.calls.requests[0].success({
    statusCode: 401,
    data: { code: 'AUTH_TOKEN_EXPIRED' }
  })
  await rejection

  assert.equal(harness.storage.get('token'), 'new-token')
  assert.equal(harness.calls.toasts.length, 0)
  assert.equal(harness.calls.redirects.length, 0)
})

test('认证接口自行处理 401，普通 403 不会被误判为登录过期', async () => {
  const harness = createRequestHarness({ token: 'token-1' })

  const authPending = harness.api.request({ url: 'https://api.example.test/auth/login' })
  harness.calls.requests[0].success({ statusCode: 401, data: { message: '邮箱或密码错误' } })
  assert.equal((await authPending).statusCode, 401)

  const forbiddenPending = harness.api.request({ url: 'https://api.example.test/goods/1' })
  harness.calls.requests[1].success({ statusCode: 403, data: { message: '无权操作' } })
  assert.equal((await forbiddenPending).statusCode, 403)
  assert.equal(harness.calls.toasts.length, 0)
})

test('邮箱登录、注册和微信登录成功后都写入共享令牌并返回原页面', async () => {
  const setTokenCalls = []
  const redirects = []
  const storage = new Map()
  const responseByPath = {
    '/auth/login': { token: 'login-token', user: { id: 1, nickname: '登录用户' } },
    '/auth/register': { token: 'register-token', user: { id: 2, nickname: '注册用户' } },
    '/auth/wechat-login': { token: 'wechat-token', user: { id: 3, nickname: '微信用户' } }
  }
  const component = loadComponent('pages/auth/auth.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      setTimeout(callback) {
        callback()
        return 1
      },
      clearTimeout: () => {},
      wx: {
        getStorageSync: key => storage.get(key),
        setStorageSync: (key, value) => storage.set(key, value),
        showToast: () => {},
        redirectTo: options => redirects.push(options.url),
        switchTab: options => redirects.push(options.url),
        login: options => options.success({ code: 'wx-code' })
      }
    },
    mocks: {
      '../../utils/request': {
        setToken: token => setTokenCalls.push(token),
        request: async options => {
          const path = Object.keys(responseByPath).find(candidate => options.url.endsWith(candidate))
          assert.ok(path, `unexpected auth request ${options.url}`)
          return {
            statusCode: 200,
            data: { success: true, data: responseByPath[path] }
          }
        }
      }
    }
  })
  const instance = component.createInstance({
    redirect: '/pages/goods/detail?id=9',
    isLoginFormValid: true,
    isRegisterFormValid: true,
    loginForm: { email: 'student@qq.com', password: '123456' },
    registerForm: {
      email: 'new@qq.com',
      code: '123456',
      password: '123456',
      nickname: '新用户'
    }
  })

  instance.handleLogin()
  await new Promise(resolve => setImmediate(resolve))
  instance.handleRegister()
  await new Promise(resolve => setImmediate(resolve))
  await instance.handleWechatLogin()

  assert.deepEqual(setTokenCalls, ['login-token', 'register-token', 'wechat-token'])
  assert.deepEqual(redirects, [
    '/pages/goods/detail?id=9',
    '/pages/goods/detail?id=9',
    '/pages/goods/detail?id=9'
  ])
  assert.equal(JSON.parse(storage.get('user')).nickname, '微信用户')
})
