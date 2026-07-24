const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const requestTs = fs.readFileSync(path.resolve(__dirname, '../utils/request.ts'), 'utf8')
const messagesTs = fs.readFileSync(path.resolve(__dirname, '../utils/messages.ts'), 'utf8')
const authTs = fs.readFileSync(path.resolve(__dirname, '../pages/auth/auth.ts'), 'utf8')
const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')
const publishTs = fs.readFileSync(path.resolve(__dirname, '../pages/publish/publish.ts'), 'utf8')
const uploadTs = fs.readFileSync(path.resolve(__dirname, '../utils/upload.ts'), 'utf8')
const typingsTs = fs.readFileSync(path.resolve(__dirname, '../typings/index.d.ts'), 'utf8')

assert.match(requestTs, /export function setToken\(token: string\)/, 'request utility should own token writes')
assert.match(requestTs, /export function clearToken\(\)/, 'request utility should own token cleanup')
assert.match(requestTs, /cachedToken = null[\s\S]*removeStorageSync\('token'\)[\s\S]*removeStorageSync\('user'\)/,
  'clearToken should clear memory, token storage, and user storage')
assert.match(messagesTs, /SESSION_EXPIRED:\s*'登录已过期，请重新登录'/,
  'shared copy should explain that the login session expired')
assert.match(requestTs, /wx\.showToast\(\{[\s\S]*SESSION_EXPIRED[\s\S]*setTimeout\(\(\) => \{[\s\S]*wx\.redirectTo/,
  '401 handling should show a friendly message before redirecting')
assert.match(requestTs, /readApiError\(res\.data\)[\s\S]*authError\.message \|\| getAuthenticationFallback\(authError\.code, Boolean\(token\)\)[\s\S]*redirectToLogin\(token, undefined, message\)/,
  '401 handling should prefer the backend JSON message')
assert.match(requestTs, /let loginRedirectPending = false[\s\S]*if \(loginRedirectPending \|\| isAuthPageActive\(\)\) return/,
  'concurrent 401 responses should not trigger duplicate prompts and redirects')
assert.match(requestTs, /if \(getToken\(\) !== failedToken\) return/,
  'a late 401 from an old token must not clear a newly established login')
assert.doesNotMatch(requestTs, /statusCode === 401 \|\| res\.statusCode === 403/,
  'generic requests must not treat authenticated authorization failures as expired login')

assert.equal((authTs.match(/setToken\(res\.data\.data\.token\)/g) || []).length, 3,
  'all three successful authentication flows should update storage and memory through setToken')
assert.doesNotMatch(authTs, /setStorageSync\('token'/, 'authentication page should not bypass setToken')

assert.match(profileTs, /logout\(\)\s*\{\s*clearToken\(\)/,
  'logout should clear the in-memory token and persistent auth state')
assert.match(publishTs, /const token = getToken\(\)/,
  'publish token validation should use the shared token source')
assert.match(publishTs, /if \(!token\)[\s\S]*redirectToLogin\(undefined, '\/pages\/publish\/publish', COMMON_MESSAGES\.LOGIN_REQUIRED\)/,
  'publish should show a friendly login-required message before redirecting anonymous users')
assert.match(publishTs, /statusCode === 401[\s\S]*redirectToLogin\(token, '\/pages\/publish\/publish', response\?\.message\)/,
  'publish token validation should use the shared expired-login prompt and preserve its return path')
assert.match(publishTs, /response\?\.message[\s\S]*resolve\(false\)[\s\S]*NETWORK_ERROR[\s\S]*resolve\(false\)/,
  'publish token validation should surface backend errors and block initialization when validation fails')
assert.doesNotMatch(uploadTs, /statusCode === 401 \|\| res\.statusCode === 403/,
  'upload authorization failures must not be mistaken for expired login')
assert.match(uploadTs, /statusCode === 401[\s\S]*handleLoginRequired\(reject, token, data\.message\)/,
  'upload authentication failures should prefer the backend JSON message')
assert.match(typingsTs, /interface ApiResponse[\s\S]*code: string/,
  'mini program response typings should expose the backend response code')

console.log('auth token lifecycle tests passed')
