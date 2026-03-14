const REPORT_STATUS_LABELS = {
  NONE: '待生成',
  AI_DRAFT: 'AI草稿',
  EDITING: '编辑中',
  SIGNED: '已签发'
}

const REPORT_STATUS_TYPES = {
  NONE: 'info',
  AI_DRAFT: 'info',
  EDITING: 'warning',
  SIGNED: 'success'
}

export const reportStatusLabel = (status, fallback = '-') => (
  REPORT_STATUS_LABELS[status] || status || fallback
)

export const reportStatusType = (status, fallback = 'info') => (
  REPORT_STATUS_TYPES[status] || fallback
)

export const formatDateTime = (value, options = {}) => {
  const { withSeconds = false, placeholder = '-', useLocale = false } = options
  if (!value) return placeholder
  if (useLocale) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return String(value)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      ...(withSeconds ? { second: '2-digit' } : {})
    })
  }
  const raw = String(value).replace('T', ' ')
  return raw.substring(0, withSeconds ? 19 : 16)
}

