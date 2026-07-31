const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadTsModule } = require('./test-support/runtime')

function createUploadHarness(options = {}) {
  const redirects = []
  const requestCalls = []
  const wx = {
    ...options.wx
  }
  const upload = loadTsModule('utils/upload.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx
    },
    mocks: {
      './request': {
        getToken: () => options.token,
        redirectToLogin: (...args) => redirects.push(args),
        request: async requestOptions => {
          requestCalls.push(requestOptions)
          return options.deleteResponse || {
            statusCode: 200,
            data: { success: true }
          }
        }
      }
    }
  })
  return { redirects, requestCalls, upload }
}

test('头像上传按 1024px 压缩并传递 avatar 用途', async () => {
  let compressOptions
  let uploadOptions
  const harness = createUploadHarness({
    token: 'token-1',
    wx: {
      getImageInfo: options => options.success({ width: 3000, height: 2000 }),
      compressImage(options) {
        compressOptions = options
        options.success({ tempFilePath: 'compressed-avatar.jpg' })
      },
      uploadFile(options) {
        uploadOptions = options
        options.success({
          statusCode: 200,
          data: JSON.stringify({
            success: true,
            data: { url: 'https://cdn.example/avatar.jpg', filename: 'avatar-key' }
          })
        })
      }
    }
  })

  const result = await harness.upload.uploadImage('avatar.jpg', 'avatar')

  assert.equal(compressOptions.src, 'avatar.jpg')
  assert.equal(compressOptions.quality, 65)
  assert.equal(compressOptions.compressedWidth, 1024)
  assert.equal(compressOptions.compressedHeight, undefined)
  assert.equal(uploadOptions.filePath, 'compressed-avatar.jpg')
  assert.equal(uploadOptions.formData.usage, 'avatar')
  assert.equal(uploadOptions.header.Authorization, 'Bearer token-1')
  assert.equal(result.filename, 'avatar-key')
})

test('商品竖图按 2048px 高度压缩并传递 goods 用途', async () => {
  let compressOptions
  let uploadOptions
  const harness = createUploadHarness({
    token: 'token-1',
    wx: {
      getImageInfo: options => options.success({ width: 1200, height: 2600 }),
      compressImage(options) {
        compressOptions = options
        options.success({ tempFilePath: 'compressed-goods.jpg' })
      },
      uploadFile(options) {
        uploadOptions = options
        options.success({
          statusCode: 200,
          data: JSON.stringify({
            success: true,
            data: { url: 'https://cdn.example/goods.jpg', filename: 'goods-key' }
          })
        })
      }
    }
  })

  await harness.upload.uploadImage('goods.jpg')

  assert.equal(compressOptions.quality, 70)
  assert.equal(compressOptions.compressedHeight, 2048)
  assert.equal(compressOptions.compressedWidth, undefined)
  assert.equal(uploadOptions.formData.usage, 'goods')
})

test('未登录和上传 401 都通过共享登录流程返回明确错误', async () => {
  const anonymous = createUploadHarness({ token: undefined, wx: {} })
  await assert.rejects(
    anonymous.upload.uploadImage('goods.jpg'),
    /图片上传失败，请先登录/
  )
  assert.equal(anonymous.redirects.length, 1)
  assert.equal(anonymous.redirects[0][0], undefined)

  const expired = createUploadHarness({
    token: 'expired-token',
    wx: {
      uploadFile: options => options.success({
        statusCode: 401,
        data: JSON.stringify({ success: false, message: '登录已过期，请重新登录' })
      })
    }
  })
  await assert.rejects(
    expired.upload.uploadImage('goods.jpg'),
    /登录已过期，请重新登录/
  )
  assert.deepEqual(expired.redirects[0], [
    'expired-token',
    undefined,
    '登录已过期，请重新登录'
  ])
})

test('暂存图片删除会编码对象键并检查服务端结果', async () => {
  const success = createUploadHarness({ token: 'token-1', wx: {} })
  await success.upload.deleteStagedImage('goods/a b.jpg')
  assert.equal(success.requestCalls[0].method, 'DELETE')
  assert.equal(
    success.requestCalls[0].url,
    'https://api.example.test/uploads/image?objectKey=goods%2Fa%20b.jpg'
  )

  const failure = createUploadHarness({
    token: 'token-1',
    wx: {},
    deleteResponse: {
      statusCode: 500,
      data: { success: false, message: '删除失败' }
    }
  })
  await assert.rejects(
    failure.upload.deleteStagedImage('goods/key.jpg'),
    /删除失败/
  )
})
