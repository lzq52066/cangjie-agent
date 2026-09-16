// 内置工具：正则提取（Groovy，纯本地）
// 入参：text 必填；pattern 必填(正则)；group(可选,默认0整段)；all(可选,true返回全部匹配)
def esc = { it == null ? 'null' : '"' + it.toString().replace('\\', '\\\\').replace('"', '\\"').replace('\r', ' ').replace('\n', '\\n') + '"' }
def src = text == null ? '' : text.toString()
def group = binding.hasVariable('group') && group != null ? (group as int) : 0
def all = binding.hasVariable('all') && all != null && all.toString().toLowerCase() == 'true'
def p = java.util.regex.Pattern.compile(pattern.toString())
def m = p.matcher(src)
def matches = new ArrayList()
while (m.find()) {
    matches.add(m.group(group))
}
if (matches.isEmpty()) {
    '{"matched":false,"matches":[]}'
} else if (all) {
    def arr = matches.collect { esc(it) }.join(',')
    '{"matched":true,"count":' + matches.size() + ',"matches":[' + arr + ']}'
} else {
    def arr = matches.collect { esc(it) }.join(',')
    '{"matched":true,"match":' + esc(matches.get(0)) + ',"matches":[' + arr + ']}'
}
