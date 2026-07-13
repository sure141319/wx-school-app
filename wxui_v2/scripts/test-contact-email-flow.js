const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const detailTs = fs.readFileSync(path.resolve(__dirname, '../pages/goods/detail.ts'), 'utf8')
const detailWxml = fs.readFileSync(path.resolve(__dirname, '../pages/goods/detail.wxml'), 'utf8')

assert.match(
  detailTs,
  /\/goods\/\$\{this\.data\.goodsId\}\/contact-email-eligibility/,
  'detail page should check both email bindings before showing the ad'
)
assert.match(detailTs, /title: '请绑定邮箱'/, 'buyer without email should receive the requested bind email prompt')
assert.match(detailTs, /content: '卖家未绑定邮箱，无法发送'/, 'seller without email should block sending with the requested prompt')
assert.match(
  detailTs,
  /CONTACT_EMAIL_AD_REWARD_TTL = 24 \* 60 \* 60 \* 1000/,
  'completed ad reward should remain valid for exactly 24 hours'
)
assert.match(
  detailTs,
  /if \(!res \|\| res\.isEnded\) \{[\s\S]*?saveContactEmailAdReward\(\)[\s\S]*?sendContactEmail\(\)/,
  'only a completed rewarded video should grant the reward and trigger sending'
)
assert.match(
  detailTs,
  /hasValidContactEmailAdReward\(\)[\s\S]*?reward\.userId === userId[\s\S]*?Number\(reward\.validUntil\) > Date\.now\(\)/,
  'ad reward should be isolated by current account and expiry time'
)
assert.match(
  detailTs,
  /isCurrentUsersGoods\(goods\?: GoodsItem\)[\s\S]*?userId === String\(sellerId\)/,
  'detail page should identify goods published by the current account'
)
assert.match(
  detailTs,
  /if \(eligibility\.ownGoods\) \{[\s\S]*?isOwnGoods: true[\s\S]*?这是你发布的商品/,
  'server eligibility should provide a fallback own-goods guard'
)
assert.match(
  detailTs,
  /\/goods\/\$\{this\.data\.goodsId\}\/contact-email`[\s\S]*?method: 'POST'/,
  'completed flow should call the authenticated contact email endpoint'
)
assert.match(
  detailWxml,
  /class="detail-contact-dock"[\s\S]*?一键发邮件通知卖家[\s\S]*?bindtap="contactSellerByEmail"/,
  'persistent bottom action should be replaced with one-tap seller email'
)
assert.doesNotMatch(
  detailWxml,
  /<view wx:if="{{goods\.seller\.wechatId \|\| goods\.seller\.qq}}" class="detail-contact-dock">/,
  'email action should stay visible so missing seller email can be explained on tap'
)
assert.match(
  detailWxml,
  /wx:if="{{isOwnGoods}}"[\s\S]*?bindtap="goMyGoods"[\s\S]*?<text>去管理<\/text>[\s\S]*?wx:else[\s\S]*?bindtap="contactSellerByEmail"/,
  'own goods should replace the email action with a management action'
)

console.log('contact email flow tests passed')
