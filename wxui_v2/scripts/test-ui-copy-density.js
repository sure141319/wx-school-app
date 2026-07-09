const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const indexWxml = fs.readFileSync(path.resolve(__dirname, '../pages/index/index.wxml'), 'utf8')
const authWxml = fs.readFileSync(path.resolve(__dirname, '../pages/auth/auth.wxml'), 'utf8')
const detailWxml = fs.readFileSync(path.resolve(__dirname, '../pages/goods/detail.wxml'), 'utf8')
const globalCss = fs.readFileSync(path.resolve(__dirname, '../styles/global.css'), 'utf8')

function assertCssBlock(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = globalCss.match(new RegExp(`${escaped}\\s*\\{([\\s\\S]*?)\\n\\}`, 'm'))
  assert.ok(match, `${selector} style block should exist`)
  return match[1]
}

assert.doesNotMatch(indexWxml, /Campus Trade/, 'home hero should remove placeholder English branding')
assert.doesNotMatch(indexWxml, /认证同学发布/, 'home hero should remove invalid verified-student copy')
assert.match(indexWxml, /找同校闲置，QQ\/微信联系，建议校内当面交易。/, 'home hero should use project-specific contact and trade copy')
assert.match(indexWxml, /图片审核后展示，联系方式由卖家填写/, 'home hero should show practical publishing note')
assert.match(indexWxml, /<view class="home-hero-copy">/, 'home hero copy should have a dedicated centered layout class')
assert.match(indexWxml, /<view class="trust-badge">\s*<view class="trust-dot"><\/view>\s*<text class="helper-text hero-note">图片审核后展示，联系方式由卖家填写<\/text>\s*<\/view>\s*<view class="btn btn-secondary btn-sm" bindtap="goToPublish">去发布<\/view>/, 'home footer should keep left note with green dot and right publish button')
assert.doesNotMatch(indexWxml, /home-hero-action/, 'home footer should not use centered publish action')
assert.match(indexWxml, /<view class="home-search-section">[\s\S]*class="search-bar home-search-bar"/, 'home search should use a standalone full-width row')
assert.match(indexWxml, /class="search-refresh-inline" bindtap="loadGoods"/, 'home search should keep refresh action inside the full-width search row')
assert.ok(
  indexWxml.indexOf('class="home-hero"') < indexWxml.indexOf('class="home-search-section"') &&
    indexWxml.indexOf('class="home-search-section"') < indexWxml.indexOf('<scroll-view class="flex whitespace-nowrap"'),
  'home search row should sit between hero and category tabs'
)
const heroEnd = indexWxml.indexOf('<view class="home-search-section">')
const heroMarkup = indexWxml.slice(indexWxml.indexOf('<view class="home-hero">'), heroEnd)
assert.doesNotMatch(heroMarkup, /search-bar|搜索你想买的闲置|aria-label="刷新"/, 'home hero should not contain the search box or refresh button')

assert.match(authWxml, /QQ 邮箱登录，或使用微信快捷进入/, 'login mode should explain both login methods')
assert.match(authWxml, /仅支持 QQ 邮箱注册，便于校内联系/, 'register mode should explain QQ email registration')
assert.match(authWxml, /通过 QQ 邮箱验证码重置密码/, 'reset mode should explain reset flow')
assert.match(authWxml, /<view class="auth-title-block text-center">/, 'auth title block should use a dedicated compact layout class')
assert.match(authWxml, /<view wx:if="{{mode === 'login'}}" class="login-wechat-section">/, 'wechat login should be a standalone section above QQ login')
assert.match(authWxml, /wechat-login-primary/, 'wechat login button should use a stronger primary style')
assert.match(authWxml, /微信一键登录/, 'wechat login button should use one-tap login copy')
assert.ok(
  authWxml.indexOf('class="login-wechat-section"') < authWxml.indexOf('class="auth-form auth-form-login"'),
  'wechat login section should appear before QQ login form'
)
const loginFormStart = authWxml.indexOf('class="auth-form auth-form-login"')
const registerBlockStart = authWxml.indexOf('<block wx:elif="{{mode === \'register\'}}"')
const loginFormMarkup = authWxml.slice(loginFormStart, registerBlockStart)
assert.doesNotMatch(loginFormMarkup, /wechat-login-btn/, 'wechat login should not live inside the QQ login form')

assert.match(detailWxml, /<view class="detail-summary-card">/, 'detail summary should use a compact summary card')
assert.match(detailWxml, /发布于 {{displayCreatedAt}}/, 'detail summary should show formatted createdAt when available')
assert.match(detailWxml, /class="seller-contact-list"/, 'seller contact rows should be grouped in a compact list')
assert.match(detailWxml, /wx:if="{{goods\.seller\.wechatId \|\| goods\.seller\.qq}}"/, 'seller contacts should render only when at least one contact exists')
assert.match(detailWxml, /卖家暂未填写联系方式/, 'seller section should show empty contact fallback')

assert.match(assertCssBlock('.home-hero-copy'), /text-align:\s*center/, 'home hero copy should be centered')
assert.match(assertCssBlock('.home-hero'), /padding:\s*24rpx 28rpx 24rpx/, 'home hero should use moderate top padding after search moved out')
assert.match(assertCssBlock('.home-hero-copy'), /margin-top:\s*0/, 'home hero title should not keep old search-row spacing above it')
assert.match(assertCssBlock('.hero-footer'), /justify-content:\s*space-between/, 'home hero footer should keep left-right layout')
assert.match(assertCssBlock('.trust-dot'), /background:\s*var\(--color-success\)/, 'home hero note should keep the green dot')
assert.match(assertCssBlock('.home-search-section'), /width:\s*100%/, 'home search section should occupy a full row')
assert.match(assertCssBlock('.home-search-bar'), /width:\s*100%/, 'home search bar should occupy the full row')
assert.match(assertCssBlock('.detail-swiper'), /height:\s*680rpx/, 'detail image area should be tightened')
assert.match(assertCssBlock('.detail-summary-card'), /padding:\s*26rpx 32rpx 24rpx/, 'detail summary should use tighter padding')
assert.match(assertCssBlock('.seller-contact-row'), /min-height:\s*64rpx/, 'seller contact rows should be compact')
assert.match(assertCssBlock('.auth-eyebrow'), /font-size:\s*50rpx/, 'auth platform title should be smaller to reduce empty space')
assert.match(assertCssBlock('.auth-title-block'), /margin-top:\s*24rpx/, 'auth title block should reduce top spacing')
assert.match(assertCssBlock('.auth-title-main'), /font-size:\s*44rpx/, 'auth main title should be compact')
assert.match(assertCssBlock('.auth-title-main'), /margin-top:\s*10rpx/, 'auth title gap should be tight')
assert.match(assertCssBlock('.auth-title-main'), /margin-bottom:\s*6rpx/, 'auth subtitle gap should be tight')
assert.match(assertCssBlock('.auth-form'), /margin-top:\s*36rpx/, 'auth form should sit closer to title')
assert.match(assertCssBlock('.login-wechat-section'), /margin-top:\s*18rpx/, 'standalone wechat login section should sit closer to compact title')
assert.match(assertCssBlock('.wechat-login-primary'), /min-height:\s*104rpx/, 'primary wechat login button should be more prominent')

const detailTs = fs.readFileSync(path.resolve(__dirname, '../pages/goods/detail.ts'), 'utf8')
assert.match(detailTs, /displayCreatedAt:\s*string/, 'detail page data should include formatted created time')
assert.match(detailTs, /formatCreatedAt\(createdAt\?: string\)/, 'detail page should format createdAt locally without backend changes')

console.log('ui copy density tests passed')
