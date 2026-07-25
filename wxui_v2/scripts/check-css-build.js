const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const projectRoot = path.resolve(__dirname, '..')
const sourcePath = path.join(projectRoot, 'styles', 'global.css')
const trackedOutputPath = path.join(projectRoot, 'app.wxss')
const temporaryRoot = path.resolve(os.tmpdir())
const temporaryDirectory = fs.mkdtempSync(
  path.join(temporaryRoot, 'campus-trade-css-')
)
const generatedOutputPath = path.join(temporaryDirectory, 'app.wxss')

function normalizeLineEndings(content) {
  return content.replace(/\r\n/g, '\n')
}

function firstDifferentLine(expected, actual) {
  const expectedLines = expected.split('\n')
  const actualLines = actual.split('\n')
  const lineCount = Math.max(expectedLines.length, actualLines.length)

  for (let index = 0; index < lineCount; index += 1) {
    if (expectedLines[index] !== actualLines[index]) {
      return index + 1
    }
  }
  return null
}

try {
  const tailwindCli = require.resolve('tailwindcss/lib/cli.js', {
    paths: [projectRoot]
  })
  const result = spawnSync(
    process.execPath,
    [tailwindCli, '-i', sourcePath, '-o', generatedOutputPath],
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

  const generated = normalizeLineEndings(
    fs.readFileSync(generatedOutputPath, 'utf8')
  )
  const tracked = normalizeLineEndings(
    fs.readFileSync(trackedOutputPath, 'utf8')
  )

  if (generated !== tracked) {
    const lineNumber = firstDifferentLine(generated, tracked)
    console.error(
      `app.wxss is out of date (first difference at line ${lineNumber}).`
    )
    console.error('Run "npm run build:css" and commit the generated file.')
    process.exitCode = 1
  } else {
    console.log('CSS build is up to date')
  }
} finally {
  const resolvedTemporaryDirectory = path.resolve(temporaryDirectory)
  const isInsideTemporaryRoot = resolvedTemporaryDirectory.startsWith(
    `${temporaryRoot}${path.sep}`
  )

  if (!isInsideTemporaryRoot) {
    throw new Error('Refusing to remove a directory outside the system temp root')
  }
  fs.rmSync(resolvedTemporaryDirectory, { recursive: true, force: true })
}
