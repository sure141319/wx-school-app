const BEIJING_UTC_OFFSET_MILLISECONDS = 8 * 60 * 60 * 1000

export function beijingDateKey(date: Date): string {
  const beijingDate = new Date(date.getTime() + BEIJING_UTC_OFFSET_MILLISECONDS)
  const year = beijingDate.getUTCFullYear()
  const month = String(beijingDate.getUTCMonth() + 1).padStart(2, '0')
  const day = String(beijingDate.getUTCDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatBeijingDisplayTime(createdAt?: string): string {
  if (!createdAt) return ''
  const text = createdAt.trim()
  const normalized = text.replace('T', ' ').slice(0, 16)
  if (!normalized.trim()) return ''

  const localDateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/)
  if (localDateTime) {
    const [, , month, day, hour, minute] = localDateTime
    return `${month}-${day} ${hour}:${minute}`
  }

  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return normalized

  const beijingDate = new Date(date.getTime() + BEIJING_UTC_OFFSET_MILLISECONDS)
  const month = String(beijingDate.getUTCMonth() + 1).padStart(2, '0')
  const day = String(beijingDate.getUTCDate()).padStart(2, '0')
  const hour = String(beijingDate.getUTCHours()).padStart(2, '0')
  const minute = String(beijingDate.getUTCMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}
