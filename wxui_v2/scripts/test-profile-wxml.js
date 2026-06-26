const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')

assert.match(
  wxml,
  /<view class="profile-summary" bindtap="openProfileEditor">/,
  'profile summary row should open the profile editor so the avatar, text, and arrow share one tap target'
)

assert.doesNotMatch(
  wxml,
  /<view class="avatar-btn profile-summary-avatar" bindtap="openProfileEditor">/,
  'profile summary avatar should not bind separately because the parent row owns the tap target'
)

console.log('profile wxml tests passed')
