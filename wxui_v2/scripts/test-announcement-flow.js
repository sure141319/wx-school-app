const assert = require('node:assert/strict')
const { test } = require('node:test')
const {
  flushPromises,
  loadComponent,
  loadTsModule
} = require('./test-support/runtime')

test('公告组件打开后展示内容，忽略操作只回调一次', () => {
  const component = loadComponent('components/announcement-popup/index.ts')
  const instance = component.createInstance()
  let ignored = 0

  instance.open({
    title: '维护通知',
    content: '今晚进行维护',
    onIgnore: () => { ignored += 1 },
    onRead: () => {}
  })
  assert.deepEqual(
    { visible: instance.data.visible, title: instance.data.title, content: instance.data.content },
    { visible: true, title: '维护通知', content: '今晚进行维护' }
  )

  instance.handleIgnore()
  instance.handleIgnore()
  assert.equal(instance.data.visible, false)
  assert.equal(ignored, 1)
})

test('点击已读记录北京时间日期和公告版本', async () => {
  const storageWrites = []
  let appDefinition
  let popupOptions
  class FakeDate extends Date {
    constructor(...args) {
      super(args.length ? args[0] : '2025-12-31T16:00:00Z')
    }
  }

  loadTsModule('app.ts', {
    globals: {
      App(value) {
        appDefinition = value
      },
      Date: FakeDate,
      getCurrentPages: () => [{
        selectComponent: () => ({
          open(options) {
            popupOptions = options
          }
        })
      }],
      wx: {
        getStorageSync: () => undefined,
        setStorageSync: (key, value) => storageWrites.push({ key, value })
      }
    },
    mocks: {
      './config/env': { getBaseUrl: () => 'https://api.example.test' },
      './utils/request': {
        request: async () => ({
          statusCode: 200,
          data: {
            success: true,
            data: { title: '维护通知', content: '今晚进行维护', revision: 7 }
          }
        })
      }
    }
  })

  appDefinition.onShow.call(appDefinition)
  await flushPromises()
  assert.equal(popupOptions.title, '维护通知')
  assert.equal(popupOptions.content, '今晚进行维护')

  popupOptions.onRead()
  assert.deepEqual(JSON.parse(JSON.stringify(storageWrites)), [{
    key: 'announcementReadState',
    value: { date: '2026-01-01', revision: 7 }
  }])
})

test('当天已读同版本公告不会再次打开', async () => {
  let appDefinition
  let opened = false
  class FakeDate extends Date {
    constructor(...args) {
      super(args.length ? args[0] : '2025-12-31T16:00:00Z')
    }
  }

  loadTsModule('app.ts', {
    globals: {
      App(value) {
        appDefinition = value
      },
      Date: FakeDate,
      getCurrentPages: () => [{
        selectComponent: () => ({ open: () => { opened = true } })
      }],
      wx: {
        getStorageSync: () => ({ date: '2026-01-01', revision: 7 }),
        setStorageSync: () => {}
      }
    },
    mocks: {
      './config/env': { getBaseUrl: () => 'https://api.example.test' },
      './utils/request': {
        request: async () => ({
          statusCode: 200,
          data: {
            success: true,
            data: { title: '维护通知', content: '今晚进行维护', revision: 7 }
          }
        })
      }
    }
  })

  appDefinition.onShow.call(appDefinition)
  await flushPromises()
  assert.equal(opened, false)
})

test('公告请求失败后会解除检查锁并允许下次重试', async () => {
  let appDefinition
  let opened = false
  let requestCount = 0

  loadTsModule('app.ts', {
    globals: {
      App(value) {
        appDefinition = value
      },
      getCurrentPages: () => [{
        selectComponent: () => ({
          open() {
            opened = true
          }
        })
      }],
      wx: {
        getStorageSync: () => undefined,
        setStorageSync: () => {}
      }
    },
    mocks: {
      './config/env': { getBaseUrl: () => 'https://api.example.test' },
      './utils/request': {
        request: async () => {
          requestCount += 1
          if (requestCount === 1) {
            throw new Error('network unavailable')
          }
          return {
            statusCode: 200,
            data: {
              success: true,
              data: { title: '恢复通知', content: '服务已恢复', revision: 8 }
            }
          }
        }
      }
    }
  })

  appDefinition.onShow.call(appDefinition)
  await flushPromises()
  assert.equal(opened, false)

  appDefinition.onShow.call(appDefinition)
  await flushPromises()
  assert.equal(requestCount, 2)
  assert.equal(opened, true)
})
