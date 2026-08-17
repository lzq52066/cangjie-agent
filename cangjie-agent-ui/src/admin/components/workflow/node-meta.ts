import type { Component } from 'vue'
import {
  VideoPlay, ChatDotRound, Search, SetUp, Switch, Refresh,
  Connection, Cpu, CircleClose
} from '@element-plus/icons-vue'

/** 工作流节点类型元信息：图标、颜色、默认配置等 */
export interface NodeTypeMeta {
  type: string
  name: string
  description: string
  icon: Component
  color: string
  defaultConfig: Record<string, any>
}

export const NODE_TYPES: NodeTypeMeta[] = [
  {
    type: 'start', name: '开始', description: '工作流入口，注入初始变量',
    icon: VideoPlay, color: '#67c23a', defaultConfig: {}
  },
  {
    type: 'llm', name: '大模型', description: '调用大模型生成内容',
    icon: ChatDotRound, color: '#409eff',
    defaultConfig: { modelId: '', prompt: '{question}', temperature: 0.7 }
  },
  {
    type: 'knowledge', name: '知识检索', description: 'RAG 混合检索知识库',
    icon: Search, color: '#0fc6c2', defaultConfig: { knowledgeBaseIds: [], topK: 5 }
  },
  {
    type: 'tool', name: '工具调用', description: '调用已注册的工具/插件',
    icon: SetUp, color: '#f59e0b', defaultConfig: { toolId: '', input: {} }
  },
  {
    type: 'condition', name: '条件分支', description: '按表达式选择后继分支',
    icon: Switch, color: '#f56c6c', defaultConfig: { expression: '' }
  },
  {
    type: 'loop', name: '循环', description: '按集合或次数循环执行',
    icon: Refresh, color: '#909399', defaultConfig: { items: '', maxIterations: 10 }
  },
  {
    type: 'api', name: 'HTTP 请求', description: '调用外部 HTTP 接口',
    icon: Connection, color: '#9b59b6', defaultConfig: { url: '', method: 'GET', body: {} }
  },
  {
    type: 'code', name: '代码执行', description: '执行脚本处理变量',
    icon: Cpu, color: '#e67e22', defaultConfig: { script: '' }
  },
  {
    type: 'end', name: '结束', description: '工作流出口，输出结果',
    icon: CircleClose, color: '#f56c6c', defaultConfig: { output: 'answer' }
  }
]

export function getMeta(type: string): NodeTypeMeta {
  return NODE_TYPES.find(t => t.type === type) || {
    type, name: type, description: '', icon: Cpu, color: '#909399', defaultConfig: {}
  }
}
