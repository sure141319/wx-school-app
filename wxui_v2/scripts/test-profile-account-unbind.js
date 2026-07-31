const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadComponent } = require('./test-support/runtime')

function createProfileHarness() {
  const calls = {
    cleared: 0,
    modals: [],
    relaunched: [],
    storedProfiles: [],
    toasts: [],
    unbindEmail: 0,
    unbindWechat: 0
  }
  const component = loadComponent('pages/profile/profile.ts', {
    globals: {
      getApp: () => ({ globalData: { baseUrl: 'https://api.example.test' } }),
      wx: {
        showModal: options => calls.modals.push(options),
        showToast: options => calls.toasts.push(options),
        reLaunch: options => calls.relaunched.push(options.url)
      }
    },
    mocks: {
      '../../utils/request': {
        clearToken: () => { calls.cleared += 1 }
      },
      '../../utils/upload': {
        uploadImage: async () => ({}),
        deleteStagedImage: async () => {}
      },
      '../../utils/storage': {
        updateStoredUser: profile => calls.storedProfiles.push(profile)
      },
      '../../services/profile-api': {
        unbindWechatAccount: async () => {
          calls.unbindWechat += 1
          return {
            id: 1,
            nickname: '同学',
            email: 'student@qq.com',
            wechatOpenid: '',
            avatarUrl: '',
            wechatId: '',
            qq: '123456'
          }
        },
        unbindEmailAccount: async () => {
          calls.unbindEmail += 1
          return {
            id: 1,
            nickname: '同学',
            email: '',
            wechatOpenid: 'openid-1',
            avatarUrl: '',
            wechatId: '',
            qq: '123456'
          }
        }
      }
    }
  })
  return { calls, component }
}

test('解绑微信前必须保留邮箱登录方式', () => {
  const harness = createProfileHarness()
  const instance = harness.component.createInstance({
    profile: {
      nickname: '同学',
      email: '',
      wechatOpenid: 'openid-1',
      avatarUrl: '',
      wechatId: '',
      qq: ''
    }
  })

  instance.confirmUnbindWechat()

  assert.equal(harness.calls.modals[0].title, '暂不可解绑')
  assert.match(harness.calls.modals[0].content, /请先绑定QQ邮箱/)
  assert.equal(harness.calls.unbindWechat, 0)
})

test('解绑邮箱前必须保留微信登录方式', () => {
  const harness = createProfileHarness()
  const instance = harness.component.createInstance({
    profile: {
      nickname: '同学',
      email: 'student@qq.com',
      wechatOpenid: '',
      avatarUrl: '',
      wechatId: '',
      qq: ''
    }
  })

  instance.confirmUnbindEmail()

  assert.equal(harness.calls.modals[0].title, '暂不可解绑')
  assert.match(harness.calls.modals[0].content, /请先绑定微信/)
  assert.equal(harness.calls.unbindEmail, 0)
})

test('用户确认后才执行解绑，成功结果同步页面与本地资料', async () => {
  const harness = createProfileHarness()
  const instance = harness.component.createInstance({
    profile: {
      nickname: '同学',
      email: 'student@qq.com',
      wechatOpenid: 'openid-1',
      avatarUrl: '',
      wechatId: '',
      qq: '123456'
    },
    showBindEmailForm: true
  })

  instance.confirmUnbindWechat()
  harness.calls.modals[0].success({ confirm: false })
  assert.equal(harness.calls.unbindWechat, 0)
  harness.calls.modals[0].success({ confirm: true })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(harness.calls.unbindWechat, 1)
  assert.equal(instance.data.profile.wechatOpenid, '')
  assert.equal(instance.data.unbindingWechat, false)

  const emailInstance = harness.component.createInstance({
    profile: {
      nickname: '同学',
      email: 'student@qq.com',
      wechatOpenid: 'openid-1',
      avatarUrl: '',
      wechatId: '',
      qq: '123456'
    },
    showBindEmailForm: true
  })
  emailInstance.confirmUnbindEmail()
  harness.calls.modals[1].success({ confirm: true })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(harness.calls.unbindEmail, 1)
  assert.equal(emailInstance.data.profile.email, '')
  assert.equal(emailInstance.data.showBindEmailForm, false)
  assert.equal(emailInstance.data.unbindingEmail, false)
  assert.equal(harness.calls.storedProfiles.length, 2)
})

test('退出登录调用共享清理并返回首页', () => {
  const harness = createProfileHarness()
  const instance = harness.component.createInstance()

  instance.logout()

  assert.equal(harness.calls.cleared, 1)
  assert.deepEqual(harness.calls.relaunched, ['/pages/index/index'])
})
