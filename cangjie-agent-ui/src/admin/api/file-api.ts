import { request } from '@shared/api/http'
import type { PageResult } from '@admin/api/observability-api'

export const fileApi = {
  list(params: { keyword?: string; category?: string; pageNum?: number; pageSize?: number }) {
    return request<PageResult<any>>({ method: 'GET', url: '/file', params })
  },
  upload(file: File, category?: string) {
    const formData = new FormData()
    formData.append('file', file)
    return request<any>({
      method: 'POST',
      url: '/file/upload',
      params: { category },
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/file/${id}` })
  },
  /** 下载/预览地址（浏览器直接打开） */
  downloadUrl(id: string) {
    return `/api/admin/file/${id}`
  }
}
