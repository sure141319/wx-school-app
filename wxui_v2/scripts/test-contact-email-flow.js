const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent } = require('./test-support/runtime')

const rewardStorageKey = 'contactEmailAdReward:adunit-6fbfdd44c8cbdc8b'

function createDetailHarness(options = {}) {
  const storage = new Map(Object.entries(options.storage || {}))
  const calls = {
    modals: [],
    navigations: [],
    requests: [],
    toasts: []
  }
  const component = loadComponent('pages/goods/detail.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      getCurrentPages: () => [{}],
      wx: {
        getStorageSync: key => storage.get(key),
        setStorageSync(key, value) {
          storage.set(key, value)
        },
        showModal: modal => calls.modals.push(modal),
        showToast: toast => calls.toasts.push(toast),
        showLoading: () => {},
        hideLoading: () => {},
        switchTab: navigation => calls.navigations.push(navigation),
        navigateTo: navigation => calls.navigations.push(navigation)
      }
    },
    mocks: {
      '../../utils/request': {
        request: async requestOptions => {
          calls.requests.push(requestOptions)
          return options.request
            ? options.request(requestOptions)
            : { statusCode: 200, data: { success: true } }
        }
      }
    }
  })
  const instance = component.createInstance({
    goodsId: '42',
    isOwnGoods: Boolean(options.isOwnGoods)
  })
  return { calls, instance }
}

test('自己的商品直接进入管理页，不请求联系接口', async () => {
  const harness = createDetailHarness({
    isOwnGoods: true,
    storage: { token: 'token-1' }
  })

  await harness.instance.contactSellerByEmail()

  assert.deepEqual(
    JSON.parse(JSON.stringify(harness.calls.navigations)),
    [{ url: '/pages/profile/profile' }]
  )
  assert.equal(harness.calls.requests.length, 0)
})

test('匿名访客收到登录说明，不会请求邮箱资格', async () => {
  const harness = createDetailHarness()

  await harness.instance.contactSellerByEmail()

  assert.equal(harness.calls.requests.length, 0)
  assert.equal(harness.calls.modals[0].title, '请先登录')
  assert.match(harness.calls.modals[0].content, /登录并绑定邮箱/)
})

test('买家或卖家未绑定邮箱时给出对应提示', async (t) => {
  await t.test('买家未绑定', async () => {
    const harness = createDetailHarness({
      storage: { token: 'token-1' },
      request: async () => ({
        statusCode: 200,
        data: {
          success: true,
          data: { ownGoods: false, buyerEmailBound: false, sellerEmailBound: true }
        }
      })
    })
    await harness.instance.contactSellerByEmail()
    assert.equal(harness.calls.modals[0].title, '请绑定邮箱')
  })

  await t.test('卖家未绑定', async () => {
    const harness = createDetailHarness({
      storage: { token: 'token-1' },
      request: async () => ({
        statusCode: 200,
        data: {
          success: true,
          data: { ownGoods: false, buyerEmailBound: true, sellerEmailBound: false }
        }
      })
    })
    await harness.instance.contactSellerByEmail()
    assert.equal(harness.calls.modals[0].content, '卖家未绑定邮箱，无法发送')
  })
})

test('有效广告奖励按当前账号复用并直接发送邮件', async () => {
  const harness = createDetailHarness({
    storage: {
      token: 'token-1',
      user: JSON.stringify({ id: 9 }),
      [rewardStorageKey]: {
        userId: '9',
        validUntil: Date.now() + 60_000
      }
    },
    request: async requestOptions => {
      if (requestOptions.method === 'POST') {
        return { statusCode: 200, data: { success: true } }
      }
      return {
        statusCode: 200,
        data: {
          success: true,
          data: { ownGoods: false, buyerEmailBound: true, sellerEmailBound: true }
        }
      }
    }
  })

  await harness.instance.contactSellerByEmail()
  await new Promise(resolve => setImmediate(resolve))

  assert.equal(harness.calls.requests.length, 2)
  assert.equal(harness.calls.requests[1].method, 'POST')
  assert.equal(
    harness.calls.requests[1].url,
    'https://api.example.test/goods/42/contact-email'
  )
  assert.equal(harness.calls.modals.length, 0)
  assert.deepEqual(
    JSON.parse(JSON.stringify(harness.calls.toasts.at(-1))),
    { title: '已通知卖家', icon: 'success' }
  )
})

test('其他账号的广告奖励不会被复用', async () => {
  const harness = createDetailHarness({
    storage: {
      token: 'token-1',
      user: JSON.stringify({ id: 9 }),
      [rewardStorageKey]: {
        userId: '8',
        validUntil: Date.now() + 60_000
      }
    },
    request: async () => ({
      statusCode: 200,
      data: {
        success: true,
        data: { ownGoods: false, buyerEmailBound: true, sellerEmailBound: true }
      }
    })
  })

  await harness.instance.contactSellerByEmail()

  assert.equal(harness.calls.requests.length, 1)
  assert.match(harness.calls.modals[0].content, /观看广告后可开启此功能/)
})

test('服务端判定为自己的商品时更新页面状态并阻止发送', async () => {
  const harness = createDetailHarness({
    storage: { token: 'token-1' },
    request: async () => ({
      statusCode: 200,
      data: {
        success: true,
        data: { ownGoods: true, buyerEmailBound: true, sellerEmailBound: true }
      }
    })
  })

  await harness.instance.contactSellerByEmail()

  assert.equal(harness.instance.data.isOwnGoods, true)
  assert.equal(harness.calls.requests.length, 1)
  assert.equal(harness.calls.toasts[0].title, '这是你发布的商品')
})
