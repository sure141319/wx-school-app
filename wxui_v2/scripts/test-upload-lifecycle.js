const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent } = require('./test-support/runtime')

function flushPromises() {
  return new Promise(resolve => setImmediate(resolve))
}

function loadPublishComponent(uploadImage, deleteStagedImage, wxOverrides = {}) {
  return loadComponent('pages/publish/publish.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx: {
        showLoading: () => {},
        hideLoading: () => {},
        ...wxOverrides
      }
    },
    mocks: {
      '../../utils/request': {
        getToken: () => 'token-1',
        redirectToLogin: () => {},
        request: async () => ({ statusCode: 200, data: { success: true } })
      },
      '../../utils/upload': {
        uploadImage,
        deleteStagedImage
      }
    }
  })
}

function loadProfileComponent(uploadImage, deleteStagedImage, wxOverrides = {}) {
  return loadComponent('pages/profile/profile.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx: {
        showLoading: () => {},
        hideLoading: () => {},
        ...wxOverrides
      }
    },
    mocks: {
      '../../utils/request': {
        clearToken: () => {}
      },
      '../../utils/upload': {
        uploadImage,
        deleteStagedImage
      },
      '../../services/profile-api': {}
    }
  })
}

test('多图上传保留成功结果并报告部分失败', async () => {
  const usages = []
  const component = loadPublishComponent(
    async (filePath, usage) => {
      usages.push({ filePath, usage })
      if (filePath === 'failed.jpg') throw new Error('图片过大')
      return {
        url: 'https://cdn.example/original.jpg',
        displayUrl: 'https://cdn.example/display.jpg',
        filename: 'goods-key'
      }
    },
    async () => {},
    {
      chooseMedia: options => options.success({
        tempFiles: [
          { tempFilePath: 'success.jpg' },
          { tempFilePath: 'failed.jpg' }
        ]
      })
    }
  )
  const instance = component.createInstance()

  instance.chooseImages()
  await flushPromises()

  assert.deepEqual(usages, [
    { filePath: 'success.jpg', usage: 'goods' },
    { filePath: 'failed.jpg', usage: 'goods' }
  ])
  assert.equal(instance.data.form.photos.length, 1)
  assert.equal(instance.data.form.photos[0].url, 'https://cdn.example/display.jpg')
  assert.equal(instance.data.form.photos[0].staged, true)
  assert.equal(instance.data.info, '1 张图片上传失败，已保留成功图片')
})

test('移除图片和离开发布页只清理暂存对象', async () => {
  const deleted = []
  const component = loadPublishComponent(
    async () => ({}),
    async filename => { deleted.push(filename) }
  )
  const photos = [
    { url: 'a', filename: 'staged-a', staged: true },
    { url: 'b', filename: 'saved-b', staged: false },
    { url: 'c', filename: 'staged-c', staged: true }
  ]
  const instance = component.createInstance()
  instance.data.form.photos = structuredClone(photos)

  instance.removePhoto({ currentTarget: { dataset: { index: 0 } } })
  await flushPromises()
  assert.deepEqual(deleted, ['staged-a'])
  assert.equal(instance.data.form.photos.length, 2)

  instance.onUnload()
  await flushPromises()
  assert.deepEqual(deleted, ['staged-a', 'staged-c'])
})

test('头像上传传递 avatar 用途并清理被替换的暂存头像', async () => {
  const uploadCalls = []
  const deleted = []
  const component = loadProfileComponent(
    async (filePath, usage) => {
      uploadCalls.push({ filePath, usage })
      return {
        url: 'https://cdn.example/new-avatar.jpg',
        filename: 'new-avatar-key'
      }
    },
    async filename => { deleted.push(filename) },
    {
      chooseMedia: options => options.success({
        tempFiles: [{ tempFilePath: 'avatar.jpg' }]
      })
    }
  )
  const instance = component.createInstance({
    avatarChanged: true,
    avatarValue: 'old-avatar-key'
  })

  instance.chooseAvatar()
  await flushPromises()

  assert.deepEqual(uploadCalls, [{ filePath: 'avatar.jpg', usage: 'avatar' }])
  assert.deepEqual(deleted, ['old-avatar-key'])
  assert.equal(instance.data.avatarValue, 'new-avatar-key')
  assert.equal(instance.data.avatarChanged, true)
})

test('取消资料编辑和页面卸载会删除未保存头像', async () => {
  const deleted = []
  const component = loadProfileComponent(
    async () => ({}),
    async filename => { deleted.push(filename) }
  )
  const closeInstance = component.createInstance({
    avatarChanged: true,
    avatarValue: 'draft-on-close',
    profile: { nickname: '同学', avatarUrl: '', wechatId: '', qq: '' }
  })
  closeInstance.closeProfileEditor()
  await flushPromises()

  const unloadInstance = component.createInstance({
    avatarChanged: true,
    avatarValue: 'draft-on-unload'
  })
  unloadInstance.onUnload()
  await flushPromises()

  assert.deepEqual(deleted, ['draft-on-close', 'draft-on-unload'])
})
