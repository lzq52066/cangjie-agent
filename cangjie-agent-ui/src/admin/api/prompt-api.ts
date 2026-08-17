import { request } from '@shared/api/http'

/** 提示词模块子资源：模板 / 技能 / 记忆 / 规则 / 命令 */
export type PromptResource = 'template' | 'skill' | 'memory' | 'rule' | 'command'

function crud(resource: PromptResource) {
  return {
    list(keyword?: string) {
      return request<any[]>({ method: 'GET', url: `/prompt/${resource}`, params: { keyword } })
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
  memory: crud('memory'),
  rule: crud('rule'),
  command: crud('command'),
  /** 按资源名动态取用，供 Tab 页统一调用 */
  of(resource: PromptResource) {
    return promptApi[resource]
  }
}
