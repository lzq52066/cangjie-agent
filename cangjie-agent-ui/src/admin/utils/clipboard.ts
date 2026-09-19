import { ElMessage } from 'element-plus'

/**
 * 复制文本到剪贴板并给出提示。
 * 浏览器在非安全上下文或用户未授权时会拒绝写剪贴板，这里统一降级为提示手动复制。
 * @param text 要复制的文本，为空时直接返回
 * @param failMessage 失败提示文案（必填，避免调用方漏传导致文案不一致）
 * @param successMessage 成功提示文案
 */
export async function copyText(
  text: string | undefined,
  failMessage: string,
  successMessage = '已复制'
): Promise<void> {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successMessage)
  } catch {
    ElMessage.warning(failMessage)
  }
}