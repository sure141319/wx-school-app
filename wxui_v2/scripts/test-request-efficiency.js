const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent } = require('./test-support/runtime')

function createIndexHarness(initialStorage = {}, request = async () => ({
  statusCode: 200,
  data: { success: true, data: { items: [], total: 0 } }
})) {
  const storage = new Map(Object.entries(initialStorage))
  const removed = []
  const component = loadComponent('pages/index/index.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx: {
        getStorageSync: key => storage.get(key),
        removeStorageSync(key) {
          storage.delete(key)
          removed.push(key)
        }
      }
    },
    mocks: {
      '../../utils/request': { request }
    }
  })
  return { component, removed, storage }
}

test('首页跳过首次 onShow，避免和 onLoad 重复请求', () => {
  const harness = createIndexHarness()
  const loads = []
  const instance = harness.component.createInstance({}, {
    loadGoods: (...args) => loads.push(args)
  })
  instance._skipNextOnShow = true

  instance.onShow()

  assert.equal(instance._skipNextOnShow, false)
  assert.equal(loads.length, 0)
})

test('首页只在缓存过期或商品列表被显式标脏时刷新', () => {
  const freshHarness = createIndexHarness()
  const freshLoads = []
  const fresh = freshHarness.component.createInstance({}, {
    loadGoods: (...args) => freshLoads.push(args)
  })
  fresh._lastLoadTime = Date.now()
  fresh.onShow()
  assert.equal(freshLoads.length, 0)

  const expiredHarness = createIndexHarness()
  const expiredLoads = []
  const expired = expiredHarness.component.createInstance({}, {
    loadGoods: (...args) => expiredLoads.push(args)
  })
  expired._lastLoadTime = Date.now() - 2 * 60 * 1000 - 1
  expired.onShow()
  assert.deepEqual(expiredLoads, [[true]])

  const dirtyHarness = createIndexHarness({ goodsListDirty: true })
  const dirtyLoads = []
  const dirty = dirtyHarness.component.createInstance({}, {
    loadGoods: (...args) => dirtyLoads.push(args)
  })
  dirty._lastLoadTime = Date.now()
  dirty.onShow()
  assert.deepEqual(dirtyLoads, [[true]])
  assert.deepEqual(dirtyHarness.removed, ['goodsListDirty'])
})

test('较早发出的商品请求晚返回时不会覆盖最新结果', async () => {
  const pending = []
  const harness = createIndexHarness({}, () => new Promise(resolve => pending.push(resolve)))
  const instance = harness.component.createInstance()

  const firstLoad = instance.loadGoods(true)
  const secondLoad = instance.loadGoods(true)
  assert.equal(pending.length, 2)

  pending[1]({
    statusCode: 200,
    data: {
      success: true,
      data: { items: [{ id: 2, title: '新结果' }], total: 1 }
    }
  })
  await secondLoad

  pending[0]({
    statusCode: 200,
    data: {
      success: true,
      data: { items: [{ id: 1, title: '旧结果' }], total: 1 }
    }
  })
  await firstLoad

  assert.equal(instance.data.goodsItems.length, 1)
  assert.equal(instance.data.goodsItems[0].id, 2)
  assert.equal(instance.data.total, 1)
})
