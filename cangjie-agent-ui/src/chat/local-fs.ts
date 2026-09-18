/**
 * 浏览器本地文件工具执行器（File System Access API）。
 *
 * 模型调用 LOCAL 工具时服务端不执行，run 挂起并下发 local_tool_required 帧，
 * 由本模块在用户授权的目录内完成文件操作，再把 JSON 结果回传后端续跑。
 *
 * 安全约束：
 * 1. 只能访问用户通过 showDirectoryPicker 授权的根目录，句柄存 IndexedDB 复用；
 * 2. 所有 path 都限定为相对根目录的相对路径，禁止盘符绝对路径与 ".." 越界；
 * 3. 重命名目标已存在即失败，不做覆盖。
 */

const DB_NAME = 'cangjie-local-fs'
const STORE = 'handles'
const ROOT_KEY = 'root'

interface DirPickOptions {
  mode?: 'read' | 'readwrite'
}

/* eslint-disable @typescript-eslint/no-explicit-any */
type AnyHandle = any

/** 只读、无需二次确认的本地工具 */
const READONLY_TOOLS = new Set(['local_list_dir', 'local_read_file'])
/** 会改动文件系统、执行前必须二次确认的本地工具 */
const MUTATING_TOOLS = new Set(['local_write_file', 'local_rename_file', 'local_delete_file'])

let rootHandlePromise: Promise<AnyHandle | null> | null = null

/**
 * 会话工作目录（相对授权根的段数组）。
 * 模型列目录后再用裸文件名读文件时，沿用 shell 语义在"当前所在目录"下解析，
 * 避免每次都要求模型拼完整相对路径。仅保留段数组（不保存句柄），
 * 因为目录句柄权限态会随会话变化，需要时从根重新解析。
 */
let cwdSegments: string[] = []

/** 列出目录后推进工作目录；"." 回到根 */
function setCwd(segments: string[]): void {
  cwdSegments = segments.slice()
}

/** 在 cwd 下拼接相对路径（拒绝 .. 越界；传入绝对路径时返回 null 表示不适用） */
function resolveUnderCwd(rawPath: string): string[] | null {
  const p = (rawPath ?? '').trim()
  if (!p || p === '.') return null
  // 盘符绝对路径 / 前导分隔符：不按 cwd 拼接，交给既有兜底流程
  if (/^[a-zA-Z]:[\\/]/.test(p) || p.startsWith('/') || p.startsWith('\\')) return null
  const rel = rawSegments(p)
  const merged = [...cwdSegments]
  for (const seg of rel) {
    if (seg === '.') continue
    if (seg === '..') {
      if (merged.length === 0) return null // 越出授权根，拒绝
      merged.pop()
    } else {
      merged.push(seg)
    }
  }
  return merged
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) {
        req.result.createObjectStore(STORE)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function idbGet(key: string): Promise<AnyHandle | undefined> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly')
    const req = tx.objectStore(STORE).get(key)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function idbSet(key: string, value: AnyHandle): Promise<void> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).put(value, key)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

async function idbDel(key: string): Promise<void> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).delete(key)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/** 浏览器是否支持 File System Access API */
export function isLocalFsSupported(): boolean {
  return typeof (window as any).showDirectoryPicker === 'function'
}

/**
 * 弹出目录选择框并持久化授权句柄。
 * @param mode readwrite 才能写入/重命名，列目录与读取只需 read
 */
export async function authorizeDirectory(options: DirPickOptions = {}): Promise<void> {
  if (!isLocalFsSupported()) {
    throw new Error('当前浏览器不支持本地文件操作，请使用最新版 Chrome / Edge')
  }
  const handle = await (window as any).showDirectoryPicker({
    id: 'cangjie-local-root',
    mode: options.mode ?? 'readwrite'
  })
  await idbSet(ROOT_KEY, handle)
  rootHandlePromise = null
}

/** 移除已授权目录 */
export async function revokeDirectory(): Promise<void> {
  await idbDel(ROOT_KEY)
  rootHandlePromise = Promise.resolve(null)
}

/**
 * 取出已授权的根目录句柄（无授权返回 null）。
 * 如需写入权限会按需调用 requestPermission 弹窗。
 */
export async function getRootHandle(requireWrite = false): Promise<AnyHandle | null> {
  if (!rootHandlePromise) {
    rootHandlePromise = idbGet(ROOT_KEY).then((h) => h ?? null)
  }
  const handle = await rootHandlePromise
  if (!handle) {
    return null
  }
  const mode = requireWrite ? 'readwrite' : 'read'
  let perm: string | undefined = await handle.queryPermission?.({ mode })
  if (perm === 'prompt') {
    // 用户尚未就本次会话授权（或刷新后回到 prompt 态），弹一次原生授权框
    perm = await handle.requestPermission?.({ mode })
  }
  if (perm && perm !== 'granted') {
    throw new Error(requireWrite ? '未获得文件夹的写入权限' : '未获得文件夹的读取权限')
  }
  return handle
}

/**
 * 校验并规范化相对路径，返回拆分后的段数组。
 * 拒绝：盘符/URL 绝对路径、以 / 开头、含 "."/".." 之外的空段越界。
 */
function normalizeSegments(rawPath: string | undefined): string[] {
  const p = (rawPath ?? '').trim()
  if (!p || p === '.') {
    return []
  }
  if (/^[a-zA-Z]:[\\/]/.test(p) || p.startsWith('/') || p.startsWith('\\')) {
    throw new Error(`路径必须是相对授权目录的相对路径，不能使用绝对路径: ${rawPath}`)
  }
  const segments = p.split(/[\\/]/).map((s) => s.trim()).filter(Boolean)
  for (const seg of segments) {
    if (seg === '..') {
      throw new Error(`路径越界，禁止使用 .. : ${rawPath}`)
    }
    // Windows 非法字符与控制字符做基本拦截
    if (/[<>:"|?*\u0000-\u001F]/.test(seg)) {
      throw new Error(`路径包含非法字符: ${seg}`)
    }
  }
  return segments
}

type WantKind = 'file' | 'directory' | 'any'

interface ResolvedTarget {
  /** 命中目标相对授权根目录的段数组 */
  segments: string[]
  handle: AnyHandle
  /** true 表示走了兜底匹配（模型给的是绝对路径或中间目录写错） */
  matched: boolean
}

/** 不做安全校验地拆分原始路径段（兜底解析前用，安全由后续只在根目录内探测保证） */
function rawSegments(rawPath: string): string[] {
  return rawPath.split(/[\\/]/).map(s => s.trim()).filter(Boolean)
}

/**
 * 沿授权根目录按段探测目标是否存在。
 * 中途任一段 NotFound 返回 null（不抛异常），权限类异常继续向上抛。
 */
async function probeBySegments(
  root: AnyHandle,
  segments: string[],
  wantKind: WantKind
): Promise<AnyHandle | null> {
  if (segments.length === 0) return wantKind === 'file' ? null : root
  let dir = root
  for (let i = 0; i < segments.length - 1; i++) {
    try {
      dir = await dir.getDirectoryHandle(segments[i])
    } catch (e) {
      if (e?.name === 'NotFoundError') return null
      throw e
    }
  }
  const name = segments[segments.length - 1]
  if (wantKind !== 'directory') {
    try {
      return await dir.getFileHandle(name)
    } catch (e) {
      if (e?.name !== 'NotFoundError') throw e
    }
  }
  if (wantKind !== 'file') {
    try {
      return await dir.getDirectoryHandle(name)
    } catch (e) {
      if (e?.name !== 'NotFoundError') throw e
    }
  }
  return null
}

/**
 * 全树按文件名模糊搜索（带遍历上限，避免目录过大时长时间卡死）。
 * 评分：文件名完全相等 60，包含/被包含 30；末级父目录也吻合再加 20。
 * 同名命中多个时取路径更浅的，结果稳定。
 */
const FUZZY_SCAN_LIMIT = 8000
const FUZZY_SCAN_LIMIT_SCOPED = 20000
/** 遍历时直接跳过的噪音目录：依赖/构建产物/版本库/回收站等，数量大且不会是源码目标 */
const SKIP_DIRS = new Set([
  'node_modules', 'target', 'build', 'dist', '.git', '.svn', '.idea', '.vscode',
  '__pycache__', '.gradle', '.mvn', 'bin', 'obj', 'vendor',
  '$recycle.bin', 'system volume information'
])
async function findFuzzy(
  root: AnyHandle,
  wanted: string[],
  wantKind: WantKind,
  base: string[] = [],
  limit = FUZZY_SCAN_LIMIT
): Promise<ResolvedTarget | null> {
  const needle = wanted[wanted.length - 1].toLowerCase()
  const parentNeedle = wanted.length > 1 ? wanted[wanted.length - 2].toLowerCase() : null
  let best: (ResolvedTarget & { score: number }) | null = null
  let visited = 0

  async function walk(dir: AnyHandle, path: string[]) {
    if (visited > limit) return
    for await (const [name, handle] of dir.entries()) {
      if (++visited > limit) break
      const cur = [...path, name]
      if (handle.kind === 'directory' && SKIP_DIRS.has(name.toLowerCase())) continue
      const kindOk = wantKind === 'any' || handle.kind === wantKind
      if (kindOk) {
        const lower = name.toLowerCase()
        let score = 0
        if (lower === needle) score = 60
        else if (lower.includes(needle) || needle.includes(lower)) score = 30
        if (score > 0 && parentNeedle && cur.length >= 2 &&
            cur[cur.length - 2].toLowerCase() === parentNeedle) {
          score += 20
        }
        if (score > 0 && (!best || score > best.score ||
            (score === best.score && cur.length < best.segments.length))) {
          best = { segments: cur, handle, matched: true, score }
        }
      }
      if (handle.kind === 'directory') await walk(handle, cur)
    }
  }

  // 定位到限定子树（通常是当前工作目录）；句柄失效则回退全树由调用方处理
  let startDir = root
  if (base.length > 0) {
    const baseHandle = await probeBySegments(root, base, 'directory')
    if (!baseHandle) return null
    startDir = baseHandle
  }
  await walk(startDir, base)
  return best ? { segments: best.segments, handle: best.handle, matched: true } : null
}

/**
 * 智能解析已存在的文件/文件夹：
 * 1. 严格相对路径直接命中；
 * 2. 模型给了盘符绝对路径（如 C:\Users\..\a.txt）或写错中间目录时，剥离盘符后
 *    用尾部路径在授权目录树内逐级精确匹配；
 * 3. 仍未命中则全树按文件名模糊搜索。
 * 整个过程只在授权根目录内探测，绝不越界。
 */
async function resolveExistingPath(
  root: AnyHandle,
  rawPath: string | undefined,
  wantKind: WantKind
): Promise<ResolvedTarget> {
  const original = (rawPath ?? '').trim()

  // 1) 严格相对路径（相对授权根）
  let strictFailed = false
  try {
    const strict = normalizeSegments(original)
    const hit = await probeBySegments(root, strict, wantKind)
    if (hit) return { segments: strict, handle: hit, matched: false }
    strictFailed = true
  } catch {
    // 绝对路径/含 .. / 非法字符：进入下面的兜底流程
  }

  // 1.5) 工作目录回退：相对授权根未命中时，按"当前所在目录（最近一次 list_dir）"解析。
  // 模型常先列目录再用裸文件名读取，shell 语义下裸文件名应相对当前目录。
  if (strictFailed && cwdSegments.length > 0) {
    const underCwd = resolveUnderCwd(original)
    if (underCwd && underCwd.length > 0) {
      const hitCwd = await probeBySegments(root, underCwd, wantKind)
      if (hitCwd) {
        return {
          segments: underCwd,
          handle: hitCwd,
          matched: true
        }
      }
    }
  }

  // 2) 剥离盘符与前导分隔符、丢弃 .. 与 . 段，只保留用于在根目录内匹配的干净段
  const stripped = original
    .replace(/^[a-zA-Z]:[\\/]+/, '')
    .replace(/^[\\/]+/, '')
  const clean = rawSegments(stripped)
    .filter(s => s !== '..' && s !== '.')
    .filter(s => !/[<>:"|?*\u0000-\u001F]/.test(s))
  if (clean.length > 0) {
    // 2a) 尾部逐级精确匹配：从最长后缀开始，命中即最贴近用户意图
    for (let start = 0; start < clean.length; start++) {
      const cand = clean.slice(start)
      const hit = await probeBySegments(root, cand, wantKind)
      if (hit) return { segments: cand, handle: hit, matched: true }
    }
    // 2b) 先在当前工作目录子树内模糊（授权范围很大时，全树配额可能被盘根噪音耗尽）
    if (cwdSegments.length > 0) {
      const scoped = await findFuzzy(root, clean, wantKind, cwdSegments, FUZZY_SCAN_LIMIT_SCOPED)
      if (scoped) return scoped
    }
    // 2c) 全树按文件名模糊
    const fuzzy = await findFuzzy(root, clean, wantKind)
    if (fuzzy) return fuzzy
  }

  const kindText = wantKind === 'directory' ? '文件夹' : '文件'
  // 识别盘符根目录写法（如 d:\a.png），提示用户直接授权对应盘符或其包含目标的文件夹
  const driveMatch = original.match(/^([a-zA-Z]):[\\/]([^\\/]+)/)
  const driveHint = driveMatch
    ? `目标在 ${driveMatch[1].toUpperCase()} 盘，浏览器只能在你授权的文件夹内操作，` +
      `请点击「重新选择文件夹并执行」，在弹窗中选择 ${driveMatch[1].toUpperCase()} 盘本身` +
      `（或包含 ${driveMatch[2]} 的上层文件夹）。`
    : '请点击「重新选择文件夹并执行」，选择一个确实包含该' + kindText + '的文件夹。'
  const modelHint = `提示：若不确定文件位置，请先调用 local_list_dir 查看项目目录，` +
    `再用相对授权文件夹的完整路径（如 cangjie-service/cangjie-chat/src/main/java/.../${wantedLeaf(original)}）读取。`
  throw new Error(`在当前授权文件夹内找不到${kindText}: ${original}。${driveHint}${modelHint}`)
}

/** 从原始路径里取末级文件名，用于错误提示示例 */
function wantedLeaf(rawPath: string): string {
  const segs = rawSegments(rawPath.replace(/^[a-zA-Z]:[\\/]+/, ''))
  return segs[segs.length - 1] || '目标文件'
}

/** 在根目录下按段取得目录句柄（create=true 时按需创建） */
async function resolveDir(root: AnyHandle, segments: string[], create = false): Promise<AnyHandle> {
  let dir = root
  for (let i = 0; i < segments.length; i++) {
    dir = await dir.getDirectoryHandle(segments[i], { create })
  }
  return dir
}

async function resolveParent(root: AnyHandle, segments: string[], create = false) {
  const parentSegs = segments.slice(0, -1)
  const name = segments[segments.length - 1]
  const parent = await resolveDir(root, parentSegs, create)
  return { parent, name }
}

/** 目录下是否已存在同名文件或文件夹 */
async function existsHandle(dir: AnyHandle, name: string): Promise<boolean> {
  try {
    await dir.getFileHandle(name)
    return true
  } catch (fileErr) {
    if (fileErr?.name !== 'NotFoundError') {
      // 权限类异常不应被静默吞掉
      throw fileErr
    }
  }
  try {
    await dir.getDirectoryHandle(name)
    return true
  } catch (dirErr) {
    if (dirErr?.name !== 'NotFoundError') {
      throw dirErr
    }
  }
  return false
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/* ==================== 四个本地工具 ==================== */

async function listDir(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(false)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const raw = (args.path ?? '').trim()
  // 根目录快捷写法无需解析
  const target = (!raw || raw === '.')
    ? { segments: [] as string[], handle: root as AnyHandle, matched: false }
    : await resolveExistingPath(root, raw, 'directory')
  const dir = target.handle
  const segments = target.segments
  // 进入目录即更新会话工作目录，后续裸文件名相对该目录解析（shell 语义）
  setCwd(segments)
  const entries: Array<Record<string, unknown>> = []
  for await (const [name, handle] of dir.entries()) {
    const isDir = handle.kind === 'directory'
    let size = 0
    let lastModified = 0
    if (!isDir) {
      const file = await handle.getFile()
      size = file.size
      lastModified = file.lastModified
    }
    entries.push({
      name,
      type: isDir ? 'directory' : 'file',
      size,
      sizeText: isDir ? null : fmtSize(size),
      lastModified: lastModified ? new Date(lastModified).toISOString() : null
    })
  }
  entries.sort((a, b) => {
    if (a.type !== b.type) return a.type === 'directory' ? -1 : 1
    return String(a.name).localeCompare(String(b.name))
  })
  const pathText = segments.length ? segments.join('/') : '.'
  const note = target.matched
    ? `已在授权文件夹内按路径自动定位到：${pathText}。当前工作目录已切换至此，后续读取本目录下文件可直接用文件名（如 README.md），跨目录仍需完整相对路径。`
    : (segments.length > 0
      ? `当前工作目录：${pathText}。后续读取本目录下文件可直接用文件名，跨目录请用相对该授权文件夹的完整路径。`
      : '当前位于授权文件夹根目录。')
  return {
    success: true,
    path: pathText,
    cwd: pathText,
    count: entries.length,
    entries,
    note
  }
}

async function readFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(false)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const target = await resolveExistingPath(root, args.path, 'file')
  const segments = target.segments
  const matchedNote = target.matched
    ? `已在授权文件夹内自动定位到：${segments.join('/')}，后续请直接使用该相对路径`
    : null
  const file: File = await target.handle.getFile()
  const encoding = args.encoding === 'base64' ? 'base64' : 'utf-8'

  const isText =
    encoding === 'utf-8' &&
    (file.type.startsWith('text/') ||
      /\.(txt|md|json|csv|log|xml|yaml|yml|js|ts|vue|java|groovy|html|css|ini|conf|properties|sql|sh)$/i.test(
        file.name
      ))

  if (encoding === 'utf-8' && isText) {
    return {
      success: true,
      path: segments.join('/'),
      name: file.name,
      encoding: 'utf-8',
      size: file.size,
      content: await file.text(),
      ...(matchedNote ? { note: matchedNote } : {})
    }
  }
  // 二进制（含图片）：转 data URL，模型可据大小判断；超大文件拦截避免撑爆上下文
  if (file.size > 5 * 1024 * 1024) {
    return {
      success: false,
      error: `文件过大（${fmtSize(file.size)}），二进制读取上限 5MB，请改用文本读取或缩小文件`
    }
  }
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  return {
    success: true,
    path: segments.join('/'),
    name: file.name,
    encoding: 'base64',
    mimeType: file.type || 'application/octet-stream',
    size: file.size,
    dataUrl,
    ...(matchedNote ? { note: matchedNote } : {})
  }
}

async function writeFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const rawPath = (args.path ?? '').trim()
  let segments = normalizeSegments(rawPath)
  if (segments.length === 0) throw new Error('path 必须指向具体文件')
  let underCwd = false
  // 裸文件名（无子目录）默认写入当前工作目录，贴合"列目录→在该目录建文件"的 shell 直觉；
  // 带子目录的显式路径仍以授权根为基准，避免误写。
  if (segments.length === 1 && cwdSegments.length > 0) {
    const merged = resolveUnderCwd(rawPath)
    if (merged && merged.length > 0) {
      segments = merged
      underCwd = true
    }
  }
  const content = typeof args.content === 'string' ? args.content : String(args.content ?? '')
  const { parent, name } = await resolveParent(root, segments, true)
  const handle = await parent.getFileHandle(name, { create: true })
  const writable = await handle.createWritable()
  try {
    await writable.write(content)
  } finally {
    await writable.close()
  }
  return {
    success: true,
    path: segments.join('/'),
    bytes: new Blob([content]).size,
    ...(underCwd ? { note: `已写入当前工作目录：${segments.join('/')}` } : {})
  }
}

async function renameFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  // 源路径支持智能兜底（绝对路径/写错中间目录时在授权树内自动定位）
  const fromTarget = await resolveExistingPath(root, args.fromPath, 'any')
  const fromSegs = fromTarget.segments
  if (fromSegs.length === 0) {
    throw new Error('fromPath 必须指向具体的文件或文件夹')
  }
  const fromHandle = fromTarget.handle
  const fromParent = await resolveDir(root, fromSegs.slice(0, -1))
  const fromName = fromSegs[fromSegs.length - 1]

  // 目标必须是相对路径，避免模型给盘符路径时把文件移到意料之外的位置
  let toSegs: string[]
  try {
    toSegs = normalizeSegments(args.toPath)
  } catch {
    throw new Error('toPath 必须是相对授权根目录的相对路径（如 photos/b.png），不能使用盘符绝对路径')
  }
  if (toSegs.length === 0) {
    throw new Error('toPath 必须指向具体的文件或文件夹')
  }
  // 只给目标文件名时，默认与源同目录（shell mv 语义），避免裸文件名被当成移到根目录
  if (toSegs.length === 1 && fromSegs.length > 1) {
    toSegs = [...fromSegs.slice(0, -1), toSegs[0]]
  }

  // 目标已存在即失败（不覆盖）：文件或目录任一存在都拒绝
  const toParent = await resolveDir(root, toSegs.slice(0, -1), true)
  const toName = toSegs[toSegs.length - 1]
  if (await existsHandle(toParent, toName)) {
    throw new Error(`目标已存在，拒绝覆盖: ${toSegs.join('/')}`)
  }

  if (typeof fromHandle.move === 'function') {
    // Chrome 110+ 支持同卷 move
    await fromHandle.move(toParent, toName)
  } else {
    // 兜底：复制到目标再删除源（老版本浏览器）
    await copyHandleRecursive(fromHandle, toParent, toName)
    await fromParent.removeEntry(fromName, { recursive: true })
  }
  return {
    success: true,
    fromPath: fromSegs.join('/'),
    toPath: toSegs.join('/'),
    ...(fromTarget.matched
      ? { note: `源路径已在授权文件夹内自动定位到：${fromSegs.join('/')}，后续请直接使用该相对路径` }
      : {})
  }
}

/** move 不可用时的递归复制兜底（文件走 FileSystemWritable，目录递归） */
async function copyHandleRecursive(source: AnyHandle, destParent: AnyHandle, destName: string): Promise<void> {
  if (source.kind === 'file') {
    const dest = await destParent.getFileHandle(destName, { create: true })
    const writable = await dest.createWritable()
    try {
      const file = await source.getFile()
      await writable.write(file)
    } finally {
      await writable.close()
    }
    return
  }
  const destDir = await destParent.getDirectoryHandle(destName, { create: true })
  for await (const [name, handle] of source.entries()) {
    await copyHandleRecursive(handle, destDir, name)
  }
}

/** 统计目录直接子项数量（用于删除前提示"非空文件夹"），带上限避免大目录卡顿 */
async function countChildren(dir: AnyHandle, cap = 1000): Promise<number> {
  let n = 0
  for await (const _ of dir.entries()) {
    if (++n >= cap) break
  }
  return n
}

async function deleteFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const target = await resolveExistingPath(root, args.path, 'any')
  const segs = target.segments
  if (segs.length === 0) {
    throw new Error('path 必须指向具体的文件或文件夹，不能删除授权根目录')
  }
  const parent = await resolveDir(root, segs.slice(0, -1))
  const name = segs[segs.length - 1]
  const isDir = target.handle.kind === 'directory'
  // 文件夹必须递归删除（含其下全部内容）；文件用非递归
  await parent.removeEntry(name, { recursive: isDir })
  return {
    success: true,
    path: segs.join('/'),
    type: isDir ? 'directory' : 'file',
    deleted: true,
    ...(target.matched
      ? { note: `已在授权文件夹内自动定位到：${segs.join('/')}` }
      : {})
  }
}

/** 是否为只读本地工具（列目录/读取），可自动执行无需二次确认 */
export function isReadonlyLocalTool(toolName: string): boolean {
  return READONLY_TOOLS.has(toolName)
}

/** 是否为会改动文件系统的本地工具（写/改名/删除），执行前必须二次确认 */
export function isMutatingLocalTool(toolName: string): boolean {
  return MUTATING_TOOLS.has(toolName)
}

/** 二次确认方案：写/改/删操作预检后生成，供对话卡片渲染"允许 / 拒绝" */
export interface LocalToolConfirmPlan {
  /** write / rename / delete，决定卡片危险样式 */
  kind: 'write' | 'rename' | 'delete'
  /** low=普通(新建/改名) / high=不可逆或覆盖 */
  riskLevel: 'low' | 'high'
  /** 一句话动作概述（卡片标题下的动作说明） */
  title: string
  /** 风险要点列表（逐条展示给用户） */
  warnings: string[]
  /** 主操作按钮文案 */
  confirmText: string
}

/** 文件句柄的简要元信息（大小/类型/修改时间），用于覆盖前提示 */
async function describeExisting(handle: AnyHandle): Promise<{ kind: string; size?: number; sizeText?: string; modified?: string }> {
  if (handle.kind === 'directory') {
    const children = await countChildren(handle)
    return {
      kind: '文件夹',
      sizeText: children >= 1000 ? '含 1000+ 个直接子项' : children > 0 ? `含 ${children} 个直接子项` : '空文件夹'
    }
  }
  const file: File = await handle.getFile()
  return {
    kind: '文件',
    size: file.size,
    sizeText: fmtSize(file.size),
    modified: new Date(file.lastModified).toLocaleString()
  }
}

/**
 * 变更类本地工具执行前预检：探测目标存在性并生成二次确认方案。
 * 抛错（如源文件不存在、路径非法）由调用方按普通执行失败处理。
 * 必须在用户已授权（可拿到根句柄）后调用。
 */
export async function planLocalTool(
  toolName: string,
  argumentsJson?: string
): Promise<LocalToolConfirmPlan | null> {
  let args: Record<string, any> = {}
  if (argumentsJson) {
    args = JSON.parse(argumentsJson)
  }
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹')

  if (toolName === 'local_write_file') {
    const segments = normalizeSegments(args.path)
    if (segments.length === 0) throw new Error('path 必须指向具体文件')
    const name = segments[segments.length - 1]
    const pathText = segments.join('/')
    // 父目录尚不存在时执行阶段会自动创建；此处只为判断同名文件是否已存在
    let parent: AnyHandle | null = root
    try {
      parent = await resolveDir(root, segments.slice(0, -1), false)
    } catch (e) {
      if (e?.name === 'NotFoundError') parent = null
      else throw e
    }
    let existing: AnyHandle | null = null
    if (parent) {
      try {
        existing = await parent.getFileHandle(name)
      } catch (e) {
        if (e?.name !== 'NotFoundError') throw e
      }
    }
    if (!existing) {
      // 新建：低风险，但仍属变更，做一次轻确认
      return {
        kind: 'write',
        riskLevel: 'low',
        title: `新建文件 ${pathText}`,
        warnings: ['该文件当前不存在，将在授权文件夹内新建并写入内容。'],
        confirmText: '新建并写入'
      }
    }
    const meta = await describeExisting(existing)
    return {
      kind: 'write',
      riskLevel: 'high',
      title: `覆盖已存在的文件 ${pathText}`,
      warnings: [
        `目标文件已存在（${meta.sizeText}${meta.modified ? `，修改于 ${meta.modified}` : ''}）。`,
        '写入会用新内容整体覆盖原文件，原内容无法恢复。'
      ],
      confirmText: '覆盖并写入'
    }
  }

  if (toolName === 'local_rename_file') {
    const fromTarget = await resolveExistingPath(root, args.fromPath, 'any')
    const fromSegs = fromTarget.segments
    if (fromSegs.length === 0) throw new Error('fromPath 必须指向具体的文件或文件夹')
    let toSegs: string[]
    try {
      toSegs = normalizeSegments(args.toPath)
    } catch {
      throw new Error('toPath 必须是相对授权根目录的相对路径（如 photos/b.png），不能使用盘符绝对路径')
    }
    if (toSegs.length === 0) throw new Error('toPath 必须指向具体的文件或文件夹')
    // 与执行逻辑保持一致：只给目标文件名时默认与源同目录
    if (toSegs.length === 1 && fromSegs.length > 1) {
      toSegs = [...fromSegs.slice(0, -1), toSegs[0]]
    }

    const warnings: string[] = []
    let risk: 'low' | 'high' = 'low'
    // 目标父目录尚不存在时执行阶段会自动创建；仅当父目录存在且同名目标存在时才判为冲突
    let toParent: AnyHandle | null = root
    try {
      toParent = await resolveDir(root, toSegs.slice(0, -1), false)
    } catch (e) {
      if (e?.name === 'NotFoundError') toParent = null
      else throw e
    }
    if (toParent && await existsHandle(toParent, toSegs[toSegs.length - 1])) {
      throw new Error(`目标已存在，拒绝覆盖: ${toSegs.join('/')}`)
    }
    const sameFolder = fromSegs.slice(0, -1).join('/') === toSegs.slice(0, -1).join('/')
    if (sameFolder) {
      warnings.push(`仅改名：${fromSegs[fromSegs.length - 1]} → ${toSegs[toSegs.length - 1]}，内容不变。`)
    } else {
      risk = 'high'
      warnings.push(`将移动到新位置：${fromSegs.join('/')} → ${toSegs.join('/')}。`)
    }
    return {
      kind: 'rename',
      riskLevel: risk,
      title: sameFolder ? '重命名文件' : '移动文件/文件夹',
      warnings,
      confirmText: sameFolder ? '确认重命名' : '确认移动'
    }
  }

  if (toolName === 'local_delete_file') {
    const target = await resolveExistingPath(root, args.path, 'any')
    const segs = target.segments
    if (segs.length === 0) throw new Error('path 必须指向具体的文件或文件夹，不能删除授权根目录')
    const isDir = target.handle.kind === 'directory'
    const meta = await describeExisting(target.handle)
    return {
      kind: 'delete',
      riskLevel: 'high',
      title: `删除${isDir ? '文件夹' : '文件'} ${segs.join('/')}`,
      warnings: [
        isDir
          ? `将永久删除该文件夹及其下全部内容（${meta.sizeText}），不进回收站。`
          : `将永久删除该文件（${meta.sizeText}），不进回收站。`,
        '此操作不可撤销，请确认路径无误。'
      ],
      confirmText: '确认永久删除'
    }
  }

  return null
}

/**
 * 执行一个本地工具调用。
 * @param toolName 函数名
 * @param argumentsJson 模型给出的参数 JSON 原文
 * @return 回填给模型的 JSON 字符串
 */
export async function executeLocalTool(
  toolName: string,
  argumentsJson?: string
): Promise<{ result: string } | { error: string }> {
  let args: Record<string, any> = {}
  if (argumentsJson) {
    try {
      args = JSON.parse(argumentsJson)
    } catch {
      return { error: `工具参数不是合法 JSON: ${argumentsJson.slice(0, 200)}` }
    }
  }
  try {
    let payload: unknown
    switch (toolName) {
      case 'local_list_dir':
        payload = await listDir(args)
        break
      case 'local_read_file':
        payload = await readFile(args)
        break
      case 'local_write_file':
        payload = await writeFile(args)
        break
      case 'local_rename_file':
        payload = await renameFile(args)
        break
      case 'local_delete_file':
        payload = await deleteFile(args)
        break
      default:
        return { error: `未知的本地工具: ${toolName}` }
    }
    return { result: JSON.stringify(payload) }
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    return { error: JSON.stringify({ success: false, error: message }) }
  }
}
