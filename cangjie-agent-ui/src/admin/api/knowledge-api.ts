import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

export const knowledgeApi = {
  // 知识库 CRUD
  list(query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: '/knowledge/base', params: query })
  },
  /** 供下拉选择使用的精简列表 */
  async options() {
    const res = await knowledgeApi.list({ pageSize: OPTION_PAGE_SIZE })
    return res?.list ?? []
  },
  get(id: string) {
    return request<any>({ method: 'GET', url: `/knowledge/base/${id}` })
  },
  create(data: any) {
    return request<any>({ method: 'POST', url: '/knowledge/base', data })
  },
  update(id: string, data: any) {
    return request<any>({ method: 'PUT', url: `/knowledge/base/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/knowledge/base/${id}` })
  },

  // 文档管理
  listDocuments(knowledgeBaseId: string, query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: `/knowledge/document/list/${knowledgeBaseId}`, params: query })
  },
  uploadDocument(knowledgeBaseId: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request<any>({
      method: 'POST',
      url: `/knowledge/document/upload/${knowledgeBaseId}`,
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  removeDocument(documentId: string) {
    return request<void>({ method: 'DELETE', url: `/knowledge/document/${documentId}` })
  },
  getDocument(documentId: string) {
    return request<any>({ method: 'GET', url: `/knowledge/document/${documentId}` })
  },
  listParagraphs(documentId: string, query: PageQuery = {}) {
    return request<PageResult<any>>({ method: 'GET', url: `/knowledge/document/paragraphs/${documentId}`, params: query })
  },

  // 重新向量化（仅文档级，逐个操作避免成本过高）
  reEmbedDocument(documentId: string) {
    return request<void>({ method: 'POST', url: `/knowledge/document/re-embed/${documentId}` })
  },

  // 检索测试
  search(data: { query: string; knowledgeBaseId?: string; knowledgeBaseIds?: string[]; topK?: number }) {
    return request<any[]>({ method: 'POST', url: '/knowledge/retrieval/search', data })
  }
}
