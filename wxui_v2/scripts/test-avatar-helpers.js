const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadTsModule } = require('./test-support/runtime')

const {
  buildQqAvatarUrl,
  resolveProfileDisplayAvatar,
  canUseQqAvatarPreview,
  resolveQqAvatarPreview
} = loadTsModule('utils/avatar.ts')

test('QQ 头像地址只接受有效 QQ 号并清理空白', () => {
  assert.equal(buildQqAvatarUrl('123456'), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
  assert.equal(buildQqAvatarUrl(' 123456 '), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
  assert.equal(buildQqAvatarUrl('abc'), '')
  assert.equal(buildQqAvatarUrl('1234'), '')
})

test('资料头像优先使用上传头像，再回退到 QQ 头像', () => {
  assert.equal(resolveProfileDisplayAvatar({
    avatarUrl: 'https://cdn.example.com/avatar.jpg',
    avatarSource: 'UPLOADED',
    qq: '123456'
  }), 'https://cdn.example.com/avatar.jpg')
  assert.equal(resolveProfileDisplayAvatar({
    avatarUrl: '',
    avatarSource: 'INITIAL',
    qq: '123456'
  }), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
  assert.equal(resolveProfileDisplayAvatar({
    avatarUrl: '',
    avatarSource: 'INITIAL',
    qq: ''
  }), '')
})

test('QQ 头像预览不会覆盖已上传头像或正在编辑的头像', () => {
  assert.equal(canUseQqAvatarPreview({ avatarSource: 'UPLOADED', avatarUrl: 'https://cdn.example.com/avatar.jpg' }, false), false)
  assert.equal(canUseQqAvatarPreview({ avatarSource: 'QQ', avatarUrl: 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640' }, false), true)
  assert.equal(canUseQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, true), false)
  assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '123456', false), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
  assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '1234', false), '')
  assert.equal(resolveQqAvatarPreview({ avatarSource: 'UPLOADED', avatarUrl: 'https://cdn.example.com/avatar.jpg' }, '123456', false), '')
  assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '123456', true), '')
})
