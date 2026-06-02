const API_BASE_URL = String(import.meta.env.VITE_APP_BASE_API || '')
  .replace(/^['"]|['"]$/g, '')
  .replace(/\/$/, '')

const PREVIEW_PATH = '/api/files/preview'

const isAbsoluteUrl = (value: string) => /^https?:\/\//i.test(value)

export const resolveMediaUrl = (value?: string | null): string => {
  if (!value) {
    return ''
  }

  const normalized = value.trim()
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
