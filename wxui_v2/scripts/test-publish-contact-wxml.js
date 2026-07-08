const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/publish/publish.wxml'), 'utf8')

assert.match(
  wxml,
  /wx:if="{{showContactModal}}"/,
  'publish page should render a contact completion modal when contact info is missing'
)

assert.match(
  wxml,
  /bindtap="saveContactBeforePublish"/,
  'contact modal should save contact info before allowing publishing'
)

assert.match(
  wxml,
  /请先填写联系方式/,
  'contact modal should clearly explain that contact info is required before publishing'
)

console.log('publish contact wxml tests passed')
