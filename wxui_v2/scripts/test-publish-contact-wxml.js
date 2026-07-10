const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/publish/publish.wxml'), 'utf8')

assert.match(
  wxml,
  /class="publish-step-no">01<[\s\S]*?class="publish-step-no">02<[\s\S]*?class="publish-step-no">03</,
  'publish page should present the form as three real completion steps'
)

assert.match(
  wxml,
  /class="bottom-bar publish-bottom-bar"[\s\S]*?disabled="{{submitting \|\| !form\.categoryId}}"/,
  'publish action should stay visible and explain why it is unavailable before category selection'
)

assert.match(
  wxml,
  /<textarea class="publish-textarea"[\s\S]*?bindinput="onDescriptionInput"><\/textarea>/,
  'publish description should use a multiline field'
)

assert.doesNotMatch(
  wxml,
  />\s*(?:\+|×)\s*</,
  'publish image actions should use styled marks instead of text glyph icons'
)

assert.doesNotMatch(
  wxml,
  />\s*(?:LIST AN ITEM|ITEM|MARKET)\s*</i,
  'publish page should not add decorative English copy'
)

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
