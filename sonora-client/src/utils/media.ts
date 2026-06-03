const API_BASE_URL = String(import.meta.env.VITE_APP_BASE_API || '')
  .replace(/^['"]|['"]$/g, '')
  .replace(/\/$/, '')

const PREVIEW_PATH = '/api/files/preview'

const isAbsoluteUrl = (value: string) => /^https?:\/\//i.test(value)

const normalizeQueryString = (value: string) => {
  const firstQuestionMark = value.indexOf('?')
  if (firstQuestionMark === -1) {
    return value
  }

  const secondQuestionMark = value.indexOf('?', firstQuestionMark + 1)
  if (secondQuestionMark === -1) {
    return value
  }

  return `${value.slice(0, secondQuestionMark)}&${value.slice(secondQuestionMark + 1)}`
}

const setQueryParam = (value: string, key: string, paramValue: string) => {
  const [pathWithQuery, hash = ''] = value.split('#')
  const [path, query = ''] = pathWithQuery.split('?')
  const params = new URLSearchParams(query)
  params.set(key, paramValue)
  const nextQuery = params.toString()
  return `${path}${nextQuery ? `?${nextQuery}` : ''}${hash ? `#${hash}` : ''}`
}

export const resolveMediaUrl = (value?: string | null): string => {
  if (!value) {
    return ''
  }

  const normalized = normalizeQueryString(value.trim())
  if (!normalized) {
    return ''
  }

  if (
    normalized.startsWith('data:') ||
    normalized.startsWith('blob:') ||
    normalized === '/default-cover.svg' ||
    normalized === '/default-avatar.svg'
  ) {
    return normalized
  }

  if (isAbsoluteUrl(normalized)) {
    return normalized
  }

  if (normalized.startsWith(PREVIEW_PATH)) {
    return API_BASE_URL ? `${API_BASE_URL}${normalized}` : normalized
  }

  if (normalized.startsWith('/')) {
    return normalized
  }

  const encodedKey = encodeURIComponent(normalized)
  const previewUrl = `${PREVIEW_PATH}?key=${encodedKey}`
  return API_BASE_URL ? `${API_BASE_URL}${previewUrl}` : previewUrl
}

export const withImageParam = (value?: string | null, param?: string): string => {
  const resolved = resolveMediaUrl(value)
  if (!resolved || !param) {
    return resolved
  }

  if (resolved.startsWith('data:') || resolved.startsWith('blob:')) {
    return resolved
  }

  return setQueryParam(resolved, 'param', param)
}
