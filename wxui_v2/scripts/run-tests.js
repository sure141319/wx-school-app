const { readdirSync } = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const scriptsDirectory = __dirname
const projectRoot = path.resolve(scriptsDirectory, '..')
const testFiles = readdirSync(scriptsDirectory)
  .filter(name => /^test-.*\.js$/.test(name))
  .sort()

if (testFiles.length === 0) {
  throw new Error('No mini program test scripts were found')
}

for (const testFile of testFiles) {
  console.log(`\n> ${testFile}`)
  const result = spawnSync(
    process.execPath,
    [path.join(scriptsDirectory, testFile)],
    {
      cwd: projectRoot,
      stdio: 'inherit'
    }
  )

  if (result.error) {
    throw result.error
  }
  if (result.status !== 0) {
    process.exit(result.status || 1)
  }
}

console.log(`\nMini program checks passed (${testFiles.length} scripts)`)
