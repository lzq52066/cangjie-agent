/** 格式化 JSON 字符串便于阅读，非 JSON 内容原样返回 */
export function prettyJson(text?: string): string {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

/** 后端 ISO 时间（2026-09-19T10:00:00）转为「2026-09-19 10:00:00」 */
export function formatDateTime(t?: string): string {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}
