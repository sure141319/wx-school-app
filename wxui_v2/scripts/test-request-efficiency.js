const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const indexTs = fs.readFileSync(path.resolve(__dirname, '../pages/index/index.ts'), 'utf8')
const publishTs = fs.readFileSync(path.resolve(__dirname, '../pages/publish/publish.ts'), 'utf8')
const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')

assert.match(indexTs, /_skipNextOnShow\s*=\s*true/, 'index should mark its first onShow for skipping')
assert.match(indexTs, /if \(\(this as any\)\._skipNextOnShow\)/, 'index should skip the first onShow refresh')
assert.match(indexTs, /GOODS_LIST_CACHE_TTL_MS\s*=\s*2 \* 60 \* 1000/, 'index should use a short cache TTL')

assert.match(publishTs, /_skipNextOnShow\s*=\s*true/, 'publish should mark its first onShow for skipping')
assert.match(publishTs, /if \(\(this as any\)\._skipNextOnShow\)/, 'publish should skip duplicate first token validation')
assert.match(publishTs, /setStorageSync\(PROFILE_DATA_DIRTY_KEY, true\)/, 'publishing should invalidate profile data')

assert.match(profileTs, /PROFILE_CACHE_TTL_MS\s*=\s*2 \* 60 \* 1000/, 'profile should use a two-minute cache TTL')
assert.match(profileTs, /const dirty = Boolean\(wx\.getStorageSync\(PROFILE_DATA_DIRTY_KEY\)\)/, 'profile should honor explicit invalidation')
assert.doesNotMatch(profileTs, /imageUrls:\s*item\.imageUrls/, 'profile should not normalize unused image arrays')

console.log('request efficiency tests passed')
