// 内置工具：文本转换（Groovy，纯本地）
// 入参：text 必填；operation=upper/lower/trim/reverse/capitalize/underscore/camel（默认 upper）
def esc = { it == null ? 'null' : '"' + it.toString().replace('\\', '\\\\').replace('"', '\\"').replace('\r', ' ').replace('\n', '\\n') + '"' }
def s = text == null ? '' : text.toString()
def op = binding.hasVariable('operation') && operation ? operation.toString().toLowerCase() : 'upper'
def r
switch (op) {
    case 'upper':
        r = s.toUpperCase()
        break
    case 'lower':
        r = s.toLowerCase()
        break
    case 'trim':
        r = s.trim()
        break
    case 'reverse':
        r = new StringBuilder(s).reverse().toString()
        break
    case 'capitalize':
        r = s.capitalize()
        break
    case 'underscore':
        r = s.replaceAll(/([a-z0-9])([A-Z])/, '$1_$2').replaceAll(/[\s\-]+/, '_').toLowerCase()
        break
    case 'camel':
        def parts = s.split(/[_\-\s]+/).findAll { it }
        r = parts ? parts[0].toLowerCase() + parts.tail().collect { it.toLowerCase().capitalize() }.join('') : ''
        break
    default:
        r = s
}
'{"operation":' + esc(op) + ',"result":' + esc(r) + '}'
