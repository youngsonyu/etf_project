/**
 * 将 rows（对象数组，key 为列标题）导出为带 BOM 的 UTF-8 CSV 文件。
 * BOM 保证 Excel 直接打开中文不乱码。
 */
export function exportToCsv(filename, rows) {
  if (!rows || !rows.length) return

  const headers = Object.keys(rows[0])

  const escapeCell = (v) => {
    const s = v == null ? '' : String(v)
    // 含逗号、双引号、换行时需要包裹引号
    if (s.includes(',') || s.includes('"') || s.includes('\n') || s.includes('\r')) {
      return `"${s.replace(/"/g, '""')}"`
    }
    return s
  }

  const lines = [
    headers.map(escapeCell).join(','),
    ...rows.map((row) => headers.map((h) => escapeCell(row[h])).join(','))
  ]

  const bom = '\uFEFF'
  const blob = new Blob([bom + lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
