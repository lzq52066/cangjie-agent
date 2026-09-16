// 内置工具：日期解析与格式化（Groovy，纯本地）
// 入参：date 必填；inputFormat(可选,默认 yyyy-MM-dd)；outputFormat(可选,默认 yyyy-MM-dd)
def esc = { it == null ? 'null' : '"' + it.toString().replace('\\', '\\\\').replace('"', '\\"').replace('\r', ' ').replace('\n', '\\n') + '"' }
def inFmt = binding.hasVariable('inputFormat') && inputFormat ? inputFormat.toString() : 'yyyy-MM-dd'
def outFmt = binding.hasVariable('outputFormat') && outputFormat ? outputFormat.toString() : 'yyyy-MM-dd'
def parsed
try {
    parsed = java.time.LocalDate.parse(date.toString(), java.time.format.DateTimeFormatter.ofPattern(inFmt))
} catch (Exception ignored) {
    // 输入可能带时间，回退按日期时间解析
    parsed = java.time.LocalDateTime.parse(date.toString(), java.time.format.DateTimeFormatter.ofPattern(inFmt))
}
def result = parsed.format(java.time.format.DateTimeFormatter.ofPattern(outFmt))
'{"input":' + esc(date.toString()) + ',"result":' + esc(result) + '}'
