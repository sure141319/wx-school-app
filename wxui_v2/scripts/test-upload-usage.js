const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const uploadTs = fs.readFileSync(path.resolve(__dirname, '../utils/upload.ts'), 'utf8')
const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')
const publishTs = fs.readFileSync(path.resolve(__dirname, '../pages/publish/publish.ts'), 'utf8')

assert.match(
  uploadTs,
  /uploadImage\(filePath: string,\s*usage: 'avatar' \| 'goods' = 'goods'\)/,
  'uploadImage should accept a usage argument that defaults to goods'
)

assert.match(
  uploadTs,
  /formData:\s*\{\s*usage\s*\}/,
  'wx.uploadFile should submit usage as multipart form data'
)

assert.match(
  profileTs,
  /uploadImage\(filePath,\s*'avatar'\)/,
  'profile avatar upload should pass usage=avatar'
)

assert.match(
  publishTs,
  /uploadImage\(file\.tempFilePath,\s*'goods'\)/,
  'publish goods image upload should pass usage=goods'
)

console.log('upload usage tests passed')
