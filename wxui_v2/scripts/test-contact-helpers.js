const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadTsModule } = require('./test-support/runtime')

const {
  hasContactMethod,
  validateContactDraft
} = loadTsModule('utils/contact.ts')

test('微信号或 QQ 任一有效即可形成联系方式', () => {
  assert.equal(hasContactMethod({ wechatId: 'wx_123', qq: '' }), true)
  assert.equal(hasContactMethod({ wechatId: '', qq: '123456' }), true)
  assert.equal(hasContactMethod({ wechatId: '   ', qq: '   ' }), false)
  assert.equal(hasContactMethod(undefined), false)
})

test('联系方式草稿返回面向用户的校验结果', () => {
  assert.deepEqual(
    JSON.parse(JSON.stringify(validateContactDraft({ wechatId: '', qq: '' }))),
    { ok: false, message: '请至少填写微信号或QQ号' }
  )
  assert.deepEqual(
    JSON.parse(JSON.stringify(validateContactDraft({ wechatId: 'wx_123', qq: '' }))),
    { ok: true, message: '' }
  )
  assert.deepEqual(
    JSON.parse(JSON.stringify(validateContactDraft({ wechatId: '', qq: '1234' }))),
    { ok: false, message: 'QQ号需为5-12位数字' }
  )
  assert.deepEqual(
    JSON.parse(JSON.stringify(validateContactDraft({ wechatId: '', qq: '123456' }))),
    { ok: true, message: '' }
  )
})
