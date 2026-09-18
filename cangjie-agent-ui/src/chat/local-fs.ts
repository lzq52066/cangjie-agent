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

let rootHandlePromise: Promise<AnyHandle | null> | null = null

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
  const segments = normalizeSegments(args.path)
  const dir = await resolveDir(root, segments)
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
  return {
    success: true,
    path: segments.length ? segments.join('/') : '.',
    count: entries.length,
    entries
  }
}

async function readFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(false)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const segments = normalizeSegments(args.path)
  if (segments.length === 0) throw new Error('path 必须指向具体文件')
  const { parent, name } = await resolveParent(root, segments)
  const handle = await parent.getFileHandle(name)
  const file: File = await handle.getFile()
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
      content: await file.text()
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
    dataUrl
  }
}

async function writeFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const segments = normalizeSegments(args.path)
  if (segments.length === 0) throw new Error('path 必须指向具体文件')
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
    bytes: new Blob([content]).size
  }
}

async function renameFile(args: Record<string, any>): Promise<unknown> {
  const root = await getRootHandle(true)
  if (!root) throw new Error('尚未授权本地文件夹，请先点击"授权本地文件夹"')
  const fromSegs = normalizeSegments(args.fromPath)
  const toSegs = normalizeSegments(args.toPath)
  if (fromSegs.length === 0 || toSegs.length === 0) {
    throw new Error('fromPath 与 toPath 都必须指向具体的文件或文件夹')
  }

  const fromParent = await resolveDir(root, fromSegs.slice(0, -1))
  const fromName = fromSegs[fromSegs.length - 1]
  let fromHandle: AnyHandle | null = null
  try {
    fromHandle = await fromParent.getFileHandle(fromName)
  } catch (e) {
    if (e?.name !== 'NotFoundError') throw e
  }
  if (!fromHandle) {
    try {
      fromHandle = await fromParent.getDirectoryHandle(fromName)
    } catch (e) {
      if (e?.name === 'NotFoundError') {
        throw new Error(`源文件或文件夹不存在: ${fromSegs.join('/')}`)
      }
      throw e
    }
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
    toPath: toSegs.join('/')
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

/**
 * 执行一个本地工具调用。
 * @param toolName 函数名（local_list_dir / local_read_file / local_write_file / local_rename_file）
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
      default:
        return { error: `未知的本地工具: ${toolName}` }
    }
    return { result: JSON.stringify(payload) }
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    return { error: JSON.stringify({ success: false, error: message }) }
  }
}
