import { request } from '@shared/api/http'

export const knowledgeApi = {
  // 知识库 CRUD
  list(keyword?: string) {
    return request<any[]>({ method: 'GET', url: '/knowledge/base', params: { keyword } })
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
  listDocuments(knowledgeBaseId: string) {
    return request<any[]>({ method: 'GET', url: `/knowledge/document/list/${knowledgeBaseId}` })
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
  listParagraphs(documentId: string) {
    return request<any[]>({ method: 'GET', url: `/knowledge/document/paragraphs/${documentId}` })
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
