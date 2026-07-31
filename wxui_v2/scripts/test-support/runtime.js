const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

const projectRoot = path.resolve(__dirname, '../..')

function loadTsModule(relativePath, options = {}) {
  const mocks = options.mocks || {}
  const moduleCache = new Map()
  const context = vm.createContext({
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    Promise,
    Date,
    Error,
    URL,
    URLSearchParams,
    encodeURIComponent,
    decodeURIComponent,
    ...options.globals
  })

  function resolveLocalModule(fromFile, specifier) {
    const unresolved = path.resolve(path.dirname(fromFile), specifier)
    const candidates = path.extname(unresolved)
      ? [unresolved]
      : [
          `${unresolved}.ts`,
          `${unresolved}.js`,
          path.join(unresolved, 'index.ts'),
          path.join(unresolved, 'index.js')
        ]
    const resolved = candidates.find(candidate => fs.existsSync(candidate))
    if (!resolved) {
      throw new Error(`Cannot resolve ${specifier} from ${fromFile}`)
    }
    return resolved
  }

  function loadFile(filePath) {
    const normalizedPath = path.normalize(filePath)
    if (moduleCache.has(normalizedPath)) {
      return moduleCache.get(normalizedPath).exports
    }

    if (path.extname(normalizedPath) === '.js') {
      return require(normalizedPath)
    }

    const source = fs.readFileSync(normalizedPath, 'utf8')
    const { outputText } = ts.transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2020
      },
      fileName: normalizedPath
    })
    const module = { exports: {} }
    moduleCache.set(normalizedPath, module)

    const localRequire = (specifier) => {
      if (Object.prototype.hasOwnProperty.call(mocks, specifier)) {
        return mocks[specifier]
      }
      if (specifier.startsWith('.')) {
        return loadFile(resolveLocalModule(normalizedPath, specifier))
      }
      return require(specifier)
    }

    const wrapper = vm.runInContext(
      `(function (require, module, exports) {\n${outputText}\n})`,
      context,
      { filename: normalizedPath }
    )
    wrapper(localRequire, module, module.exports)
    return module.exports
  }

  return loadFile(path.resolve(projectRoot, relativePath))
}

function setByPath(target, key, value) {
  const segments = key.replace(/\[(\d+)\]/g, '.$1').split('.')
  let current = target
  for (let index = 0; index < segments.length - 1; index += 1) {
    const segment = segments[index]
    if (!current[segment] || typeof current[segment] !== 'object') {
      current[segment] = {}
    }
    current = current[segment]
  }
  current[segments[segments.length - 1]] = value
}

function createComponentInstance(definition, dataOverrides = {}, instanceOverrides = {}) {
  const data = structuredClone(definition.data || {})
  Object.assign(data, structuredClone(dataOverrides))
  const instance = {
    data,
    setData(patch, callback) {
      for (const [key, value] of Object.entries(patch)) {
        setByPath(this.data, key, value)
      }
      if (callback) callback()
    },
    ...(definition.methods || {}),
    ...instanceOverrides
  }
  return instance
}

function loadComponent(relativePath, options = {}) {
  let definition
  const globals = {
    ...options.globals,
    Component(value) {
      definition = value
    }
  }
  const moduleExports = loadTsModule(relativePath, {
    ...options,
    globals
  })
  if (!definition) {
    throw new Error(`${relativePath} did not register a Component`)
  }
  return {
    definition,
    moduleExports,
    createInstance(dataOverrides, instanceOverrides) {
      return createComponentInstance(definition, dataOverrides, instanceOverrides)
    }
  }
}

module.exports = {
  createComponentInstance,
  loadComponent,
  loadTsModule
}
