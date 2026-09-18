import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'
import { OPTION_PAGE_SIZE } from '@shared/types'

/** 提示词模块子资源：模板 / 技能 / 规则 / 命令（记忆已独立为长期记忆管理） */
export type PromptResource = 'template' | 'skill' | 'rule' | 'command'

function crud(resource: PromptResource) {
  return {
    list(query: PageQuery = {}) {
      return request<PageResult<any>>({ method: 'GET', url: `/prompt/${resource}`, params: query })
    },
    /** 供下拉选择使用的精简列表 */
    async options() {
      const res = await request<PageResult<any>>({
        method: 'GET',
        url: `/prompt/${resource}`,
        params: { pageSize: OPTION_PAGE_SIZE }
      })
      return res?.list ?? []
    },
    get(id: string) {
      return request<any>({ method: 'GET', url: `/prompt/${resource}/${id}` })
    },
    create(data: any) {
      return request<any>({ method: 'POST', url: `/prompt/${resource}`, data })
    },
    update(id: string, data: any) {
      return request<any>({ method: 'PUT', url: `/prompt/${resource}/${id}`, data })
    },
    remove(id: string) {
      return request<void>({ method: 'DELETE', url: `/prompt/${resource}/${id}` })
    }
  }
}

export const promptApi = {
  template: crud('template'),
  skill: crud('skill'),
  rule: crud('rule'),
  command: crud('command'),
  /** 按资源名动态取用，供 Tab 页统一调用 */
  of(resource: PromptResource) {
    return promptApi[resource]
  }
}
