const path = require('node:path')
const { spawnSync } = require('node:child_process')

const projectRoot = path.resolve(__dirname, '..')
const steps = [
  {
    name: 'TypeScript strict check',
    args: [
      require.resolve('typescript/lib/tsc.js', { paths: [projectRoot] }),
      '--noEmit'
    ]
  },
  {
    name: 'Mini program regression scripts',
    args: ['--test', '--test-concurrency=1', 'scripts/test-*.js']
  },
  {
    name: 'Generated CSS drift check',
    args: [path.join(__dirname, 'check-css-build.js')]
  }
]

for (const step of steps) {
  console.log(`\n== ${step.name} ==`)
  const result = spawnSync(
    process.execPath,
    step.args,
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
