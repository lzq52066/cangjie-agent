// 内置工具：日期加减（Groovy，纯本地）
// 入参：date(可选,默认今天)、days、months、years(可选,默认0)、format(可选,默认 yyyy-MM-dd)
def esc = { it == null ? 'null' : '"' + it.toString().replace('\\', '\\\\').replace('"', '\\"').replace('\r', ' ').replace('\n', '\\n') + '"' }
def fmt = binding.hasVariable('format') && format ? format.toString() : 'yyyy-MM-dd'
def formatter = java.time.format.DateTimeFormatter.ofPattern(fmt)
def baseDate
if (binding.hasVariable('date') && date) {
    baseDate = java.time.LocalDate.parse(date.toString(), formatter)
} else {
    baseDate = java.time.LocalDate.now()
}
def d = binding.hasVariable('days') && days != null ? (days as long) : 0L
def m = binding.hasVariable('months') && months != null ? (months as long) : 0L
def y = binding.hasVariable('years') && years != null ? (years as long) : 0L
def target = baseDate.plusDays(d).plusMonths(m).plusYears(y)
'{"base":' + esc(baseDate.format(formatter)) + ',"result":' + esc(target.format(formatter)) + '}'
