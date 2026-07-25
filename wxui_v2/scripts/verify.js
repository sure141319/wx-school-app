const path = require('node:path')
const { spawnSync } = require('node:child_process')

const projectRoot = path.resolve(__dirname, '..')
const steps = [
  {
    name: 'TypeScript strict check',
    script: require.resolve('typescript/lib/tsc.js', { paths: [projectRoot] }),
    args: ['--noEmit']
  },
  {
    name: 'Mini program regression scripts',
    script: path.join(__dirname, 'run-tests.js'),
    args: []
  },
  {
    name: 'Generated CSS drift check',
    script: path.join(__dirname, 'check-css-build.js'),
    args: []
  }
]

for (const step of steps) {
  console.log(`\n== ${step.name} ==`)
  const result = spawnSync(
    process.execPath,
    [step.script, ...step.args],
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

console.log('\nMini program quality gate passed')
