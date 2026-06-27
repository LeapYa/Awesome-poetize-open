export const DRAFT_META_FIELDS = [
  'commentStatus',
  'recommendStatus',
  'viewStatus',
  'submitToSearchEngine',
  'articleSlug',
  'summary',
  'autoSummary',
  'password',
  'tips',
  'articleCover',
  'autoGenerateCover',
  'videoUrl',
  'sortId',
  'labelId',
  'payType',
  'payAmount',
  'freePercent',
  'skipAiTranslation',
  'translationLanguage',
  'translationSummary'
]

export function uint8ArrayToBase64(uint8Array) {
  if (!uint8Array || uint8Array.length === 0) {
    return ''
  }
  let binary = ''
  const chunkSize = 0x8000
  for (let index = 0; index < uint8Array.length; index += chunkSize) {
    const chunk = uint8Array.subarray(index, index + chunkSize)
    binary += String.fromCharCode.apply(null, chunk)
  }
  return window.btoa(binary)
}

export function base64ToUint8Array(base64) {
  if (!base64) {
    return new Uint8Array()
  }
  const binary = window.atob(base64)
  const length = binary.length
  const bytes = new Uint8Array(length)
  for (let index = 0; index < length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes
}

export function buildDraftWebSocketUrl(baseURL, draftId, token) {
  const endpoint = new URL(baseURL)
  endpoint.protocol = endpoint.protocol === 'https:' ? 'wss:' : 'ws:'
  if (endpoint.pathname.endsWith('/api')) {
    endpoint.pathname = `${endpoint.pathname}/ws/article-draft`
  } else {
    endpoint.pathname = `${endpoint.pathname.replace(/\/$/, '')}/ws/article-draft`
  }
  endpoint.searchParams.set('draftId', draftId)
  endpoint.searchParams.set('token', token)
  return endpoint.toString()
}

export function applyTextDiff(target, nextValue, origin) {
  if (!target) {
    return
  }
  const currentValue = target.toString()
  const normalizedNextValue = nextValue || ''
  if (currentValue === normalizedNextValue) {
    return
  }

  let prefixLength = 0
  const maxPrefixLength = Math.min(currentValue.length, normalizedNextValue.length)
  while (prefixLength < maxPrefixLength && currentValue[prefixLength] === normalizedNextValue[prefixLength]) {
    prefixLength += 1
  }

  let currentSuffixIndex = currentValue.length
  let nextSuffixIndex = normalizedNextValue.length
  while (
    currentSuffixIndex > prefixLength &&
    nextSuffixIndex > prefixLength &&
    currentValue[currentSuffixIndex - 1] === normalizedNextValue[nextSuffixIndex - 1]
  ) {
    currentSuffixIndex -= 1
    nextSuffixIndex -= 1
  }

  const deleteLength = currentSuffixIndex - prefixLength
  const insertValue = normalizedNextValue.slice(prefixLength, nextSuffixIndex)
  const doc = target.doc
  if (!doc) {
    if (deleteLength > 0) {
      target.delete(prefixLength, deleteLength)
    }
    if (insertValue) {
      target.insert(prefixLength, insertValue)
    }
    return
  }

  doc.transact(() => {
    if (deleteLength > 0) {
      target.delete(prefixLength, deleteLength)
    }
    if (insertValue) {
      target.insert(prefixLength, insertValue)
    }
  }, origin)
}
