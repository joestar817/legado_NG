import type { BookSoure, RssSource, Source } from '../source'
import { isNullOrBlank } from './utils'

const isBookSource = (source: Source): source is BookSoure =>
  'bookSourceName' in source

export const isInvaildSource: (source: Source) => boolean = source => {
  if (isBookSource(source)) {
    return (
      !isNullOrBlank(source.bookSourceName) &&
      !isNullOrBlank(source.bookSourceUrl) &&
      !isNullOrBlank(source.bookSourceType)
    )
  }
  return !isNullOrBlank(source.sourceName) && !isNullOrBlank(source.sourceUrl)
}

export const getSourceUniqueKey = (source: Source) =>
  isBookSource(source) ? source.bookSourceUrl : source.sourceUrl
export const getSourceName = (source: Source) =>
  isBookSource(source) ? source.bookSourceName : source.sourceName

export const isSourceMatches: (source: Source, searchKey: string) => boolean = (
  source,
  searchKey,
) => {
  // TODO: 正则和普通字符串识别 识别 * . \ [ ] <= <! != = ?: () \d\w\s\...
  if (isBookSource(source)) {
    return (
      (source.bookSourceName.includes(searchKey) ||
        source.bookSourceUrl.includes(searchKey) ||
        source.bookSourceGroup?.includes(searchKey) ||
        source.bookSourceComment?.includes(searchKey)) ??
      false
    )
  }
  return (
    (source.sourceName.includes(searchKey) ||
      source.sourceUrl.includes(searchKey) ||
      source.sourceGroup?.includes(searchKey) ||
      source.sourceComment?.includes(searchKey)) ??
    false
  )
}

export const convertSourcesToMap = (sources: Source[]): Map<string, Source> => {
  const map = new Map()
  sources.forEach(source => map.set(getSourceUniqueKey(source), source))
  return map
}

const bookRuleGroups = new Set([
  'ruleSearch',
  'ruleExplore',
  'ruleBookInfo',
  'ruleToc',
  'ruleContent',
  'ruleReview',
])
const editedRuleFields = new WeakMap<object, Set<string>>()

const isRuleGroup = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value)

const getRuleGroup = (source: Source, namespace: string): unknown =>
  (source as Record<string, unknown>)[namespace]

export const isLegacyRuleGroup = (source: Source, namespace: string) => {
  const group = getRuleGroup(source, namespace)
  return group != null && !isRuleGroup(group)
}

export const readRuleField = (source: Source, namespace: string, id: string) => {
  const group = getRuleGroup(source, namespace)
  const value = isRuleGroup(group) ? group[id] : undefined
  return value == null || typeof value === 'object' ? '' : String(value)
}

export const writeRuleField = (
  source: Source,
  namespace: string,
  id: string,
  text: string,
) => {
  const record = source as Record<string, unknown>
  if (record[namespace] == null && text !== '') record[namespace] = {}
  const group = record[namespace]
  if (!isRuleGroup(group) || group[id] === text) return

  group[id] = text
  const fields = editedRuleFields.get(group)
  if (fields) fields.add(id)
  else editedRuleFields.set(group, new Set([id]))
}

const normalizeField = (record: Record<string, unknown>, key: string) => {
  const value = record[key]
  if (value === null || (typeof value === 'string' && !value.trim())) {
    delete record[key]
  } else if (value instanceof Object) {
    const nested = value as Record<string, unknown>
    for (const field in nested) normalizeField(nested, field)
  }
}

export const normalizeSource = (source: Source) => {
  const record = source as Record<string, unknown>
  const book = isBookSource(source)
  for (const key in record) {
    if (book && bookRuleGroups.has(key)) {
      // Keep imported group shapes and values. Only clean fields edited in this form.
      const group = record[key]
      if (isRuleGroup(group)) {
        editedRuleFields.get(group)?.forEach(field => normalizeField(group, field))
      }
    } else {
      normalizeField(record, key)
    }
  }
}

export const emptyBookSource = {
  ruleSearch: {},
  ruleBookInfo: {},
  ruleToc: {},
  ruleContent: {},
  // ruleReview: {},
  ruleExplore: {},
} as BookSoure
export const emptyRssSource = {} as RssSource
