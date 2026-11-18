import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import ts from 'typescript'
import { reactive } from 'vue'

const require = createRequire(import.meta.url)

function loadUtility(name) {
  const url = new URL(`../src/utils/${name}.ts`, import.meta.url)
  const code = ts.transpileModule(readFileSync(url, 'utf8'), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText
  const exports = {}
  new Function('exports', 'require', code)(exports, dependency =>
    dependency === './utils' ? loadUtility('utils') : require(dependency),
  )
  return exports
}

const { isLegacyRuleGroup, readRuleField, writeRuleField, normalizeSource } =
  loadUtility('souce')
const clone = value => JSON.parse(JSON.stringify(value))

test('absent and null rule groups stay blank until text is entered', () => {
  for (const source of [reactive({}), reactive({ ruleExplore: null })]) {
    const before = clone(source)
    assert.equal(readRuleField(source, 'ruleExplore', 'bookList'), '')
    assert.equal(isLegacyRuleGroup(source, 'ruleExplore'), false)
    writeRuleField(source, 'ruleExplore', 'bookList', '')
    assert.deepEqual(clone(source), before)
    writeRuleField(source, 'ruleExplore', 'bookList', 'new discovery rule')
    assert.deepEqual(clone(source), {
      ruleExplore: { bookList: 'new discovery rule' },
    })
    assert.equal(
      readRuleField(source, 'ruleExplore', 'bookList'),
      'new discovery rule',
    )
  }
})

test('an existing empty group receives the update and other groups keep their shapes', () => {
  const source = reactive({
    bookSourceName: 'book',
    ruleSearch: {},
    ruleExplore: {},
    ruleBookInfo: null,
  })
  const group = source.ruleExplore
  writeRuleField(source, 'ruleExplore', 'name', 'discovery name')
  assert.equal(source.ruleExplore, group)
  assert.deepEqual(clone(source), {
    bookSourceName: 'book',
    ruleSearch: {},
    ruleExplore: { name: 'discovery name' },
    ruleBookInfo: null,
  })
})

test('save removes a cleared rule without deleting neighbours or converting another group', () => {
  const source = reactive({
    bookSourceName: 'book',
    ruleExplore: {
      name: 'remove this',
      bookList: 'keep this',
      futureField: null,
      untouchedBlank: '',
    },
    ruleSearch: { checkKeyWord: '', name: 'search name' },
    ruleBookInfo: null,
  })
  writeRuleField(source, 'ruleExplore', 'name', ' \n ')
  normalizeSource(source)
  assert.deepEqual(clone(source), {
    bookSourceName: 'book',
    ruleExplore: { bookList: 'keep this', futureField: null, untouchedBlank: '' },
    ruleSearch: { checkKeyWord: '', name: 'search name' },
    ruleBookInfo: null,
  })
})

test('legacy rule values are read-only and survive serialization and save unchanged', () => {
  const source = reactive({
    bookSourceName: 'old book',
    ruleSearch: '   ',
    ruleExplore: '{"bookList":"legacy discovery"}',
    ruleToc: ['old list'],
    ruleContent: false,
  })
  const before = clone(source)
  for (const group of ['ruleSearch', 'ruleExplore', 'ruleToc', 'ruleContent']) {
    assert.equal(isLegacyRuleGroup(source, group), true)
    assert.equal(readRuleField(source, group, 'bookList'), '')
    writeRuleField(source, group, 'bookList', 'ignored')
  }
  normalizeSource(source)
  assert.deepEqual(clone(source), before)
})

test('subscriptions retain their flat field cleanup', () => {
  const source = reactive({
    sourceName: 'rss',
    ruleContent: '',
    ruleTitle: 'title',
    loginUi: null,
  })
  normalizeSource(source)
  assert.deepEqual(clone(source), { sourceName: 'rss', ruleTitle: 'title' })
})
