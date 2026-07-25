const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const miniProgramRoot = path.resolve(__dirname, '..')
const repositoryRoot = path.resolve(miniProgramRoot, '..')

const read = (...segments) => fs.readFileSync(path.join(...segments), 'utf8')
const publishTs = read(miniProgramRoot, 'pages', 'publish', 'publish.ts')
const indexTs = read(miniProgramRoot, 'pages', 'index', 'index.ts')
const profileTs = read(miniProgramRoot, 'pages', 'profile', 'profile.ts')
const storageTs = read(miniProgramRoot, 'utils', 'storage.ts')
const adminGoodsJs = read(repositoryRoot, 'checkui', 'js', 'goods.js')
const applicationYaml = read(repositoryRoot, 'v1', 'src', 'main', 'resources', 'application.yml')

const chooseCategoryBlock = publishTs.match(
  /chooseCategory\(e:[\s\S]*?\n\s*chooseImages\(\)/
)?.[0] || ''

assert.match(adminGoodsJs, /request\(`\/audit\/goods\?\$\{params\.toString\(\)\}`\)/)
assert.doesNotMatch(adminGoodsJs, /request\(`\/goods\?\$\{params\.toString\(\)\}`,\s*\{\s*auth:\s*false\s*\}\)/)

assert.ok(chooseCategoryBlock, 'publish page should keep a category selection handler')
assert.doesNotMatch(chooseCategoryBlock, /'form\.(title|description)'/)

assert.match(indexTs, /_goodsRequestSequence/)
assert.match(indexTs, /requestSequence !== \(this as any\)\._goodsRequestSequence/)
assert.match(profileTs, /_profileRequestSequence/)
assert.match(profileTs, /_goodsRequestSequence/)

assert.match(storageTs, /try\s*\{[\s\S]*JSON\.parse/)
assert.match(storageTs, /catch\s*\(_error\)\s*\{\s*return \{\}/)
assert.match(applicationYaml, /connection-init-sql:\s*"\$\{DB_CONNECTION_INIT_SQL:SET time_zone = '\+08:00'\}"/)

console.log('Medium-priority regression checks passed')
