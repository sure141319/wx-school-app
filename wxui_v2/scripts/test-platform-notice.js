const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.join(__dirname, '..')
const appJson = fs.readFileSync(path.join(root, 'app.json'), 'utf8')
const aboutTs = fs.readFileSync(path.join(root, 'pages/about/about.ts'), 'utf8')
const aboutWxml = fs.readFileSync(path.join(root, 'pages/about/about.wxml'), 'utf8')
const aboutWxss = fs.readFileSync(path.join(root, 'pages/about/about.wxss'), 'utf8')
const indexWxml = fs.readFileSync(path.join(root, 'pages/index/index.wxml'), 'utf8')
const authWxml = fs.readFileSync(path.join(root, 'pages/auth/auth.wxml'), 'utf8')
const profileWxml = fs.readFileSync(path.join(root, 'pages/profile/profile.wxml'), 'utf8')
const publishWxml = fs.readFileSync(path.join(root, 'pages/publish/publish.wxml'), 'utf8')

assert.match(appJson, /"pages\/about\/about"/, 'platform notice page should be registered')
assert.match(aboutWxml, /平台不提供站内支付、担保、订单、物流、退款或纠纷处理/)
assert.match(aboutWxml, /不核验访问者或发布者的在校生身份/)
assert.match(aboutWxml, /对所有访问者公开/)
assert.match(aboutWxml, /当面核对商品状态/)
assert.match(aboutWxml, /class="about-boundary-stop">平台边界/)
assert.match(aboutWxml, /class="about-safety-number">1<[\s\S]*class="about-safety-number">2<[\s\S]*class="about-safety-number">3</)
assert.match(aboutWxml, /问题反馈 · QQ 群[\s\S]*1078739008/)
assert.match(aboutWxml, /bindtap="copyFeedbackQQ"/)
assert.match(aboutTs, /copyFeedbackQQ\(\)[\s\S]*wx\.setClipboardData/)
assert.match(aboutWxss, /\.about-boundary-grid::after[\s\S]*border-left:\s*2rpx dashed/)
assert.match(aboutWxml, /<announcement-popup id="announcementPopup" \/>/)

assert.match(profileWxml, /\/pages\/about\/about/, 'profile should link to the platform notice')
for (const [name, wxml] of [['home', indexWxml], ['auth', authWxml], ['publish', publishWxml]]) {
  assert.doesNotMatch(wxml, /\/pages\/about\/about/, `${name} should not link to the platform notice`)
}

assert.match(profileWxml, /微信号和 QQ 号会在商品详情中对所有访客公开/)
assert.doesNotMatch(publishWxml, /所有访客公开/)

console.log('platform notice checks passed')
