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
  buildQqAvatarUrl,
  resolveProfileDisplayAvatar,
  canUseQqAvatarPreview,
  resolveQqAvatarPreview
} = loadTsModule('utils/avatar.ts')

assert.equal(buildQqAvatarUrl('123456'), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
assert.equal(buildQqAvatarUrl(' 123456 '), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
assert.equal(buildQqAvatarUrl('abc'), '')
assert.equal(buildQqAvatarUrl('1234'), '')

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

assert.equal(canUseQqAvatarPreview({ avatarSource: 'UPLOADED', avatarUrl: 'https://cdn.example.com/avatar.jpg' }, false), false)
assert.equal(canUseQqAvatarPreview({ avatarSource: 'QQ', avatarUrl: 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640' }, false), true)
assert.equal(canUseQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, true), false)

assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '123456', false), 'https://q1.qlogo.cn/g?b=qq&nk=123456&s=640')
assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '1234', false), '')
assert.equal(resolveQqAvatarPreview({ avatarSource: 'UPLOADED', avatarUrl: 'https://cdn.example.com/avatar.jpg' }, '123456', false), '')
assert.equal(resolveQqAvatarPreview({ avatarSource: 'INITIAL', avatarUrl: '' }, '123456', true), '')

console.log('avatar helper tests passed')
