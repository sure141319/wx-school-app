const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadTsModule } = require('./test-support/runtime')

const {
  beijingDateKey,
  formatBeijingDisplayTime
} = loadTsModule('utils/time.ts')

test('北京时间自然日以 UTC+8 为边界', () => {
  assert.equal(beijingDateKey(new Date('2025-12-31T15:59:59Z')), '2025-12-31')
  assert.equal(beijingDateKey(new Date('2025-12-31T16:00:00Z')), '2026-01-01')
})

test('无时区 LocalDateTime 按北京时间墙上时间展示', () => {
  assert.equal(formatBeijingDisplayTime('2026-01-02T03:04:05'), '01-02 03:04')
  assert.equal(formatBeijingDisplayTime('2026-01-02 03:04'), '01-02 03:04')
})

test('带时区绝对时刻转换为北京时间展示', () => {
  assert.equal(formatBeijingDisplayTime('2026-01-01T16:05:00Z'), '01-02 00:05')
  assert.equal(formatBeijingDisplayTime('2026-01-02T08:05:00+08:00'), '01-02 08:05')
})

test('空值与不可解析值安全降级', () => {
  assert.equal(formatBeijingDisplayTime(), '')
  assert.equal(formatBeijingDisplayTime('not-a-date'), 'not-a-date')
})
