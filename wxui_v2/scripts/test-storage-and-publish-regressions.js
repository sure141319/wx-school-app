const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent, loadTsModule } = require('./test-support/runtime')

function loadStorage(valueOrError) {
  const writes = []
  const storage = loadTsModule('utils/storage.ts', {
    globals: {
      wx: {
        getStorageSync() {
          if (valueOrError instanceof Error) throw valueOrError
          return valueOrError
        },
        setStorageSync: (key, value) => writes.push({ key, value })
      }
    }
  })
  return { storage, writes }
}

test('损坏或异常的用户缓存安全降级为空资料', () => {
  assert.deepEqual(
    JSON.parse(JSON.stringify(loadStorage('{broken').storage.readStoredUser())),
    {}
  )
  assert.deepEqual(
    JSON.parse(JSON.stringify(loadStorage(['unexpected']).storage.readStoredUser())),
    {}
  )
  assert.deepEqual(
    JSON.parse(JSON.stringify(loadStorage(new Error('storage failed')).storage.readStoredUser())),
    {}
  )
})

test('更新用户缓存会合并已有字段，不覆盖未修改资料', () => {
  const harness = loadStorage(JSON.stringify({
    id: 7,
    nickname: '原昵称',
    qq: '123456'
  }))

  harness.storage.updateStoredUser({ nickname: '新昵称' })

  const written = JSON.parse(harness.writes[0].value)
  assert.deepEqual(written, {
    id: 7,
    nickname: '新昵称',
    qq: '123456'
  })
})

test('选择商品分类会将标题和描述补齐为分类名', () => {
  const component = loadComponent('pages/publish/publish.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx: {}
    },
    mocks: {
      '../../utils/request': {
        getToken: () => 'token-1',
        redirectToLogin: () => {},
        request: async () => ({ statusCode: 200, data: { success: true } })
      },
      '../../utils/upload': {
        uploadImage: async () => ({}),
        deleteStagedImage: async () => {}
      }
    }
  })
  const instance = component.createInstance()
  instance.data.categories = [{ id: 3, name: '二手书', icon: '/static/category-icons/books.svg' }]
  instance.data.form.title = '高等数学教材'
  instance.data.form.description = '同济版，上册'
  instance.data.errors = {
    categoryId: '请选择商品分类',
    title: '请输入标题',
    description: '请输入描述'
  }

  instance.chooseCategory({
    detail: { checked: true },
    currentTarget: { dataset: { id: '3' } }
  })

  assert.equal(instance.data.form.categoryId, '3')
  assert.equal(instance.data.form.title, '二手书')
  assert.equal(instance.data.form.description, '二手书')
  assert.equal(instance.data.errors.categoryId, '')
  assert.equal(instance.data.errors.title, '')
  assert.equal(instance.data.errors.description, '')
})
