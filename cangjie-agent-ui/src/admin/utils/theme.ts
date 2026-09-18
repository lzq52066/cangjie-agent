import { ref } from 'vue'

/**
 * 主题色管理：用户可在顶栏切换预设色或自定义取色，
 * 动态生成 Element Plus 主色及其 light/dark 派生色，并持久化到 localStorage。
 */

const STORAGE_KEY = 'cj-theme-color'
export const DEFAULT_THEME = '#67c23a'

export interface ThemePreset {
  name: string
  color: string
}

export const THEME_PRESETS: ThemePreset[] = [
  { name: '品牌绿', color: '#67c23a' },
  { name: '经典蓝', color: '#409eff' },
  { name: '靛青紫', color: '#5a5bdc' },
  { name: '活力紫', color: '#7c4dff' },
  { name: '青碧', color: '#13c2c2' },
  { name: '暖阳橙', color: '#e6a23c' },
  { name: '玫红', color: '#eb2f96' },
  { name: '朱砂红', color: '#f56c6c' }
]

const HEX_RE = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/

const themeColor = ref(DEFAULT_THEME)

type RGB = [number, number, number]

function hexToRgb(hex: string): RGB {
  let h = hex.replace('#', '')
  if (h.length === 3) h = h.split('').map(c => c + c).join('')
  const n = parseInt(h, 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function rgbToHsl([r, g, b]: RGB): [number, number, number] {
  r /= 255; g /= 255; b /= 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const l = (max + min) / 2
  let h = 0, s = 0
  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0))
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h *= 60
  }
  return [h, s, l]
}

function hslToHex(h: number, s: number, l: number): string {
  h = ((h % 360) + 360) % 360
  s = Math.max(0, Math.min(1, s))
  l = Math.max(0, Math.min(1, l))
  const c = (1 - Math.abs(2 * l - 1)) * s
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = l - c / 2
  let r = 0, g = 0, b = 0
  if (h < 60) [r, g, b] = [c, x, 0]
  else if (h < 120) [r, g, b] = [x, c, 0]
  else if (h < 180) [r, g, b] = [0, c, x]
  else if (h < 240) [r, g, b] = [0, x, c]
  else if (h < 300) [r, g, b] = [x, 0, c]
  else [r, g, b] = [c, 0, x]
  return toHex([(r + m) * 255, (g + m) * 255, (b + m) * 255])
}

function toHex(rgb: RGB): string {
  return '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('')
}

/** Element Plus 的 mix 规则：c1 * weight + c2 * (1 - weight) */
function mix(c1: RGB, c2: RGB, weight: number): RGB {
  return c1.map((v, i) => v * weight + c2[i] * (1 - weight)) as RGB
}

export function applyThemeColor(hex: string) {
  if (!HEX_RE.test(hex)) return
  const rgb = hexToRgb(hex)
  const white: RGB = [255, 255, 255]
  const black: RGB = [0, 0, 0]
  const vars: Record<string, string> = {
    '--cj-primary': hex,
    '--cj-primary-rgb': rgb.join(', '),
    '--el-color-primary': hex,
    '--el-color-primary-light-3': toHex(mix(white, rgb, 0.3)),
    '--el-color-primary-light-5': toHex(mix(white, rgb, 0.5)),
    '--el-color-primary-light-7': toHex(mix(white, rgb, 0.7)),
    '--el-color-primary-light-8': toHex(mix(white, rgb, 0.8)),
    '--el-color-primary-light-9': toHex(mix(white, rgb, 0.9)),
    '--el-color-primary-dark-2': toHex(mix(black, rgb, 0.2))
  }
  const root = document.documentElement
  for (const [key, value] of Object.entries(vars)) root.style.setProperty(key, value)
  themeColor.value = hex
}

/** 应用启动时尽早调用，避免主题色闪烁 */
export function initTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  applyThemeColor(saved && HEX_RE.test(saved) ? saved : DEFAULT_THEME)
}

export function setThemeColor(hex: string) {
  if (!HEX_RE.test(hex)) return
  localStorage.setItem(STORAGE_KEY, hex)
  applyThemeColor(hex)
}

export function resetTheme() {
  setThemeColor(DEFAULT_THEME)
}

export function useTheme() {
  return { themeColor, setThemeColor, resetTheme }
}

/**
 * ECharts 等 canvas 图表无法读取 CSS 变量，需显式传入调色板。
 * 以当前主题色为第一色，其余颜色在色轮上按黄金角（137.508°）旋转生成，
 * 使任意数量的曲线都尽量分散在色相空间，避免相近主题色与固定辅助色撞色。
 */
export function getChartPalette(count = 10): string[] {
  const [h0, s0, l0] = rgbToHsl(hexToRgb(themeColor.value))
  // 保持与主题色一致的色彩调性，同时约束到白底上可读的饱和度/明度区间
  const s = Math.max(0.58, Math.min(0.78, s0))
  const l = Math.max(0.42, Math.min(0.52, l0))
  const GOLDEN_ANGLE = 137.508
  return Array.from({ length: count }, (_, i) =>
    i === 0 ? themeColor.value : hslToHex(h0 + GOLDEN_ANGLE * i, s, l)
  )
}
