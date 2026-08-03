const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { test } = require('node:test')

const root = path.resolve(__dirname, '..')

function read(...segments) {
  return fs.readFileSync(path.join(root, ...segments), 'utf8')
}

function readJson(...segments) {
  return JSON.parse(read(...segments))
}

function assertIncludesAll(source, fragments, label) {
  for (const fragment of fragments) {
    assert.ok(source.includes(fragment), `${label} should include: ${fragment}`)
  }
}

test('平台说明保留信息公告栏边界和线下交易提示', () => {
  const appJson = readJson('app.json')
  const aboutWxml = read('pages', 'about', 'about.wxml')
  const aboutTs = read('pages', 'about', 'about.ts')

  assert.ok(appJson.pages.includes('pages/about/about'))
  assertIncludesAll(aboutWxml, [
    '平台不提供站内支付、担保、订单、物流、退款或纠纷处理',
    '不核验访问者或发布者的在校生身份',
    '所有访问者公开',
    '当面核对商品状态',
    '问题反馈 · QQ 群',
    '1078739008',
    'bindtap="copyFeedbackQQ"'
  ], 'platform notice')
  assert.ok(aboutTs.includes("const FEEDBACK_QQ_GROUP = '1078739008'"))
})

test('卖家联系方式公开提示只出现在资料编辑和平台说明中', () => {
  const profileWxml = read('pages', 'profile', 'profile.wxml')
  const publishWxml = read('pages', 'publish', 'publish.wxml')

  assert.ok(profileWxml.includes('微信号和 QQ 号会在商品详情中对所有访客公开'))
  assert.ok(profileWxml.includes('url="/pages/about/about"'))
  assert.equal(publishWxml.includes('所有访客公开'), false)
})

test('公告组件由每个页面局部注册并挂载', () => {
  for (const page of [
    'index/index',
    'auth/auth',
    'goods/detail',
    'publish/publish',
    'profile/profile',
    'about/about'
  ]) {
    const pageJson = readJson('pages', `${page}.json`)
    const pageWxml = read('pages', `${page}.wxml`)
    assert.equal(
      pageJson.usingComponents?.['announcement-popup'],
      '/components/announcement-popup/index',
      `${page} should register announcement popup`
    )
    assert.ok(
      pageWxml.includes('<announcement-popup id="announcementPopup" />'),
      `${page} should mount announcement popup`
    )
  }
})

test('关键联系、账号绑定和发布操作保持可触达', () => {
  const detailWxml = read('pages', 'goods', 'detail.wxml')
  const profileWxml = read('pages', 'profile', 'profile.wxml')
  const publishWxml = read('pages', 'publish', 'publish.wxml')

  assertIncludesAll(detailWxml, [
    '{{goods.seller.qq}}',
    'bindtap="copyQQ"',
    'bindtap="goMyGoods"',
    'bindtap="contactSellerByEmail"'
  ], 'goods detail')
  assertIncludesAll(profileWxml, [
    'bindtap="openAccountBindModal"',
    'bindtap="bindWechat"',
    'bindtap="confirmUnbindWechat"',
    'bindtap="confirmUnbindEmail"'
  ], 'profile account binding')
  assertIncludesAll(publishWxml, [
    'wx:if="{{showContactModal}}"',
    'bindtap="saveContactBeforePublish"',
    'bindinput="onDescriptionInput"',
    'disabled="{{submitting || !form.categoryId}}"'
  ], 'publish flow')
})

test('发布页图片上传入口无需选择分类即可显示', () => {
  const publishWxml = read('pages', 'publish', 'publish.wxml')

  assert.ok(publishWxml.includes('<view class="publish-card publish-photo-card">'))
  assert.equal(
    publishWxml.includes('wx:if="{{form.categoryId}}" class="publish-card publish-photo-card"'),
    false
  )
})

test('分类和地点选择保留无障碍单选语义', () => {
  const indexWxml = read('pages', 'index', 'index.wxml')
  const publishWxml = read('pages', 'publish', 'publish.wxml')

  assertIncludesAll(indexWxml, [
    'aria-role="radiogroup"',
    'aria-role="radio"',
    'aria-checked="{{categoryId === item.id}}"'
  ], 'home category filter')
  assert.ok(
    publishWxml.split('aria-role="radiogroup"').length - 1 >= 2,
    'publish category and location should expose radio-group semantics'
  )
  assert.ok(publishWxml.includes('aria-checked="{{form.categoryId == item.id}}"'))
})

test('TDesign 控件注册和动态资源完整', () => {
  const publishJson = readJson('pages', 'publish', 'publish.json')
  const requiredComponents = {
    't-input': 'tdesign-miniprogram/input/input',
    't-check-tag': 'tdesign-miniprogram/check-tag/check-tag',
    't-segmented': 'tdesign-miniprogram/segmented/segmented',
    't-picker': 'tdesign-miniprogram/picker/picker',
    't-picker-item': 'tdesign-miniprogram/picker-item/picker-item'
  }
  for (const [name, componentPath] of Object.entries(requiredComponents)) {
    assert.equal(publishJson.usingComponents[name], componentPath)
  }

  const dynamicAssetPaths = [
    ...['recommend', 'books', 'daily', 'study', 'digital', 'accessories', 'sports', 'snacks', 'transport', 'other']
      .map(name => `static/category-icons/${name}.svg`),
    'static/ahut-campus-miniprogram-code.jpg',
    'static/ahut-other-miniprogram-code.jpg'
  ]
  for (const relativePath of dynamicAssetPaths) {
    assert.ok(fs.existsSync(path.join(root, relativePath)), `${relativePath} should exist`)
  }
})
