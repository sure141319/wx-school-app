const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const profileWxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')
const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')
const appWxss = fs.readFileSync(path.resolve(__dirname, '../app.wxss'), 'utf8')

function cssBlock(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = appWxss.match(new RegExp(`${escaped}\\s*\\{([\\s\\S]*?)\\n\\}`, 'm'))
  assert.ok(match, `${selector} style block should exist`)
  return match[1]
}

assert.match(
  profileWxml,
  /<view class="settings-code settings-code-account">\s*<image class="settings-code-icon" src="\/static\/icon-account\.svg"[\s\S]*?<text class="settings-label">绑定账号<\/text>/,
  'account settings row should use the account vector icon and label'
)

assert.match(
  profileWxml,
  /<view class="settings-code settings-code-feedback">\s*<image class="settings-code-icon" src="\/static\/icon-feedback\.svg"[\s\S]*?<text class="settings-label">意见反馈<\/text>/,
  'feedback settings row should use the feedback vector icon and label'
)

assert.match(
  profileWxml,
  /<view class="settings-code settings-code-campus">\s*<image class="settings-code-icon" src="\/static\/icon-miniprogram\.svg"[\s\S]*?<text class="settings-label">校内小程序<\/text>/,
  'campus mini program settings row should use a campus vector icon and label'
)

assert.doesNotMatch(
  profileWxml,
  />\s*(?:@|♥|\?|›)\s*</,
  'profile settings should not rely on text glyphs or emoji for icons'
)

assert.match(
  profileWxml,
  /bindtap="openAccountBindModal"[\s\S]*?icon-account\.svg[\s\S]*?<text class="settings-label">绑定账号<\/text>[\s\S]*?bindtap="showSupportAuthor"[\s\S]*?icon-support\.svg[\s\S]*?<text class="settings-label">支持作者<\/text>[\s\S]*?bindtap="showFeedback"[\s\S]*?icon-feedback\.svg[\s\S]*?<text class="settings-label">意见反馈<\/text>[\s\S]*?bindtap="showCampusOther"[\s\S]*?icon-miniprogram\.svg[\s\S]*?<text class="settings-label">校内小程序<\/text>/,
  'support author, feedback, and campus mini program rows should keep the requested settings order'
)

assert.match(
  profileTs,
  /const SUPPORT_AUTHOR_AD_UNIT_ID = 'adunit-f3d20d1b06422a8d'/,
  'support author rewarded video ad unit id should be configured in profile page'
)

assert.match(
  profileTs,
  /let supportAuthorVideoAd: WechatMiniprogram\.RewardedVideoAd \| null = null/,
  'support author rewarded video ad instance should be stored at module scope'
)

assert.match(
  profileTs,
  /onLoad\(\)[\s\S]*?this\.initSupportAuthorVideoAd\(\)/,
  'profile page should create the support author rewarded video ad on load'
)

assert.match(
  profileTs,
  /wx\.createRewardedVideoAd\(\{\s*adUnitId:\s*SUPPORT_AUTHOR_AD_UNIT_ID\s*\}\)/,
  'support author rewarded video ad should be created with the configured ad unit id'
)

assert.match(
  profileTs,
  /supportAuthorVideoAd\.onError\(\(err\) => \{[\s\S]*?console\.error\('激励视频广告加载失败', err\)/,
  'support author rewarded video ad should log loading errors'
)

assert.match(
  profileTs,
  /supportAuthorVideoAd\.onClose\(\(res\) => \{[\s\S]*?if \(!res \|\| res\.isEnded\)[\s\S]*?感谢支持/,
  'support author rewarded video ad close handler should thank users after complete viewing and tolerate old close results'
)

assert.match(
  profileTs,
  /showSupportAuthor\(\)[\s\S]*?const videoAd = supportAuthorVideoAd[\s\S]*?videoAd\.show\(\)\.catch\(\(\) => \{[\s\S]*?videoAd\.load\(\)[\s\S]*?\.then\(\(\) => videoAd\.show\(\)\)/,
  'support author tap handler should show the rewarded video ad and retry after loading'
)

assert.match(
  profileTs,
  /onUnload\(\)[\s\S]*?this\.destroySupportAuthorVideoAd\(\)/,
  'profile page should destroy the support author rewarded video ad on unload'
)

assert.match(
  profileTs,
  /const CAMPUS_MINIPROGRAM_CODE_IMAGES = \[\s*'\/static\/ahut-campus-miniprogram-code\.jpg',\s*'\/static\/ahut-other-miniprogram-code\.jpg'\s*\]/,
  'campus mini program code images should be configured as local static assets'
)

assert.match(
  profileTs,
  /showCampusOther\(\)[\s\S]*?showCampusOtherModal:\s*true/,
  'campus other tap handler should open its mini program code modal'
)

assert.match(
  profileWxml,
  /wx:if="{{showCampusOtherModal}}"[\s\S]*?<view class="modal-title">校内小程序<\/view>[\s\S]*?安工大校内小程序，长按可识别前往\/转发给校友[\s\S]*?src="\/static\/ahut-campus-miniprogram-code\.jpg"[\s\S]*?src="\/static\/ahut-other-miniprogram-code\.jpg"/,
  'campus mini program modal should display both mini program codes and the requested explanation'
)

assert.match(
  profileWxml,
  /show-menu-by-longpress="true"[\s\S]*?data-src="\/static\/ahut-campus-miniprogram-code\.jpg"[\s\S]*?show-menu-by-longpress="true"[\s\S]*?data-src="\/static\/ahut-other-miniprogram-code\.jpg"/,
  'both campus mini program codes should support long press recognition and sharing'
)

assert.doesNotMatch(
  cssBlock('.wechat-bind-row'),
  /border|background|border-radius/,
  'wechat binding row should not render as an extra nested card inside the account binding modal'
)

assert.match(
  cssBlock('.wechat-bind-status'),
  /line-height:\s*1/,
  'bound status text should use a tight line-height so it aligns with row titles'
)

assert.match(
  cssBlock('.campus-other-code'),
  /width:\s*260rpx/,
  'campus mini program codes should render side by side within the modal'
)

const campusCodeImageNames = [
  'ahut-campus-miniprogram-code.jpg',
  'ahut-other-miniprogram-code.jpg'
]
const campusCodeTotalBytes = campusCodeImageNames.reduce((total, fileName) => {
  const filePath = path.resolve(__dirname, '../static', fileName)
  assert.ok(fs.existsSync(filePath), `${fileName} should exist in static assets`)
  return total + fs.statSync(filePath).size
}, 0)

assert.ok(
  campusCodeTotalBytes < 200 * 1024,
  `campus mini program code images should stay under 200K total, got ${campusCodeTotalBytes} bytes`
)

console.log('profile account bind layout tests passed')
