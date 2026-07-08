const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function loadTsModule(relativePath) {
  const filePath = path.resolve(__dirname, '..', relativePath)
  const source = fs.readFileSync(filePath, 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  })
  const module = { exports: {} }
  vm.runInNewContext(outputText, {
    module,
    exports: module.exports,
    require,
    console
  }, { filename: filePath })
  return module.exports
}

const {
  hasContactMethod,
  validateContactDraft
} = loadTsModule('utils/contact.ts')

assert.equal(hasContactMethod({ wechatId: 'wx_123', qq: '' }), true)
assert.equal(hasContactMethod({ wechatId: '', qq: '123456' }), true)
assert.equal(hasContactMethod({ wechatId: '   ', qq: '   ' }), false)
assert.equal(hasContactMethod(undefined), false)

let validation = validateContactDraft({ wechatId: '', qq: '' })
assert.equal(validation.ok, false)
assert.equal(validation.message, '请至少填写微信号或QQ号')

validation = validateContactDraft({ wechatId: 'wx_123', qq: '' })
assert.equal(validation.ok, true)
assert.equal(validation.message, '')

validation = validateContactDraft({ wechatId: '', qq: '1234' })
assert.equal(validation.ok, false)
assert.equal(validation.message, 'QQ号需为5-12位数字')

validation = validateContactDraft({ wechatId: '', qq: '123456' })
assert.equal(validation.ok, true)
assert.equal(validation.message, '')

console.log('contact helper tests passed')
