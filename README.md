# CangJie Agent — 仓颉智能体平台

> 企业级大模型智能体（Agent）平台，一站式构建、部署、运营 AI 应用。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)

---

## 📖 简介

CangJie Agent 是一个面向企业级场景的 AI 智能体平台，后端基于 **Spring Boot 3 + Java 17**，前端基于 **Vue 3 + TypeScript + Vite**。平台提供了大模型管理、知识库（RAG）、工作流编排、工具/插件、提示词管理、智能应用发布、多渠道接入、可观测性等能力，帮助企业快速构建和运营 AI 应用。

---

## 🧩 系统架构

```
cangjie-agent
├── cangjie-common          -- 公共基础：统一返回、异常、MyBatis-Plus 基类、工具类
├── cangjie-core            -- 核心抽象：模型提供者、RAG 引擎、工作流节点、插件 SPI
├── cangjie-service-api     -- API 契约层（9 个业务模块 DTO）
│   ├── cangjie-user-api
│   ├── cangjie-model-api
│   ├── cangjie-knowledge-api
│   ├── cangjie-tool-api
│   ├── cangjie-application-api
│   ├── cangjie-workflow-api
│   ├── cangjie-trigger-api
│   ├── cangjie-oss-api
│   └── cangjie-system-api
├── cangjie-service         -- 业务实现层（11 个模块）
│   ├── cangjie-user        -- 用户认证 + RBAC 权限
│   ├── cangjie-model       -- 大模型配置管理
│   ├── cangjie-knowledge   -- 知识库（文档解析、切片、向量化、混合检索）
│   ├── cangjie-tool        -- 工具与插件
│   ├── cangjie-prompt      -- 提示词管理
│   ├── cangjie-workflow    -- 工作流引擎（9 种节点）
│   ├── cangjie-application -- 智能应用管理
│   ├── cangjie-chat        -- 对话服务
│   ├── cangjie-trigger     -- 渠道接入（微信等）
│   ├── cangjie-oss         -- 文件存储 + 操作日志 + 链路追踪
│   └── cangjie-system      -- 系统设置
├── cangjie-start           -- 启动入口 + Flyway 迁移 + 全局配置
└── cangjie-agent-ui        -- 前端（Admin 管理端 + Chat 对话端）
```

---

## ✨ 核心功能

### 🤖 大模型管理
支持多模型提供商（OpenAI 兼容接口），可配置模型参数（temperature、maxTokens 等），支持模型在线测试与默认模型设置。

| 模型管理 |
|:---:|
| ![模型管理](doc/assert/%E6%A8%A1%E5%9E%8B%E7%AE%A1%E7%90%86.png) |

### 📚 知识库（RAG）
- **多格式文档解析**：支持 PDF、Word、Excel、PPT、图片、纯文本
- **智能文本切片**：SmartTextSplitter，支持自定义分隔符与切片策略
- **向量存储**：基于 PostgreSQL + pgvector 扩展
- **混合检索**：向量相似度检索 + 全文检索，提升召回精度

| 知识库 |
|:---:|
| ![知识库](doc/assert/%E7%9F%A5%E8%AF%86%E5%BA%93.png) |

### 🔧 工具与插件
可注册外部工具和插件，扩展 Agent 能力边界。内置微信渠道插件。

| 工具和插件 |
|:---:|
| ![工具和插件](doc/assert/%E5%B7%A5%E5%85%B7%E5%92%8C%E6%8F%92%E4%BB%B6.png) |

### 💬 提示词管理
管理提示词模板、命令（Command）、规则（Rule）、技能（Skill）、记忆（Memory）。

| 提示词管理 |
|:---:|
| ![提示词管理](doc/assert/%E6%8F%90%E7%A4%BA%E8%AF%8D.png) |

### ⚙️ 工作流引擎
可视化拖拽编排，9 种内置节点：开始 / 结束 / LLM 调用 / 知识库检索 / 工具调用 / 条件判断 / 循环 / API 请求 / 代码执行，支持节点间变量传递。

| 工作流编排 |
|:---:|
| ![工作流](doc/assert/%E5%B7%A5%E4%BD%9C%E6%B5%81.png) |

### 🚀 智能应用
将模型、知识库、提示词、工作流组装为可对外发布的 AI 应用，一键发布。

| 智能应用 |
|:---:|
| ![智能应用](doc/assert/Agent%E5%BA%94%E7%94%A8.png) |

### 📡 可观测性
链路追踪（Trace）、系统指标、操作日志，全链路监控 AI 应用运行状态。

| 链路追踪 |
|:---:|
| ![链路追踪](doc/assert/%E9%93%BE%E8%B7%AF%E8%BF%BD%E8%B8%AA.png) |

### 🔐 RBAC 权限管理
基于 Sa-Token 的用户-角色-菜单三级权限体系，JWT 令牌认证。

### 📁 文件管理
支持本地存储与 MinIO 对象存储，统一管理平台中的文件资产。

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.4.1 | 核心框架 |
| MyBatis-Plus | 3.5.9 | ORM |
| PostgreSQL | 16 + pgvector | 数据库 + 向量检索 |
| Flyway | 11.0.0 | 数据库迁移 |
| Sa-Token | 1.39.0 | 认证与授权 |
| LangChain4j | 1.18.1 | AI 集成 |
| MinIO | 8.5.17 | 对象存储 |
| Caffeine / Redis | — | 缓存 |
| Knife4j + SpringDoc | — | API 文档 |
| Apache PDFBox / POI | — | 文档解析 |
| RabbitMQ | — | 消息队列 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5.13 | 前端框架 |
| TypeScript | ~5.8 | 类型安全 |
| Vite | 6.2.4 | 构建工具 |
| Element Plus | 2.13.5 | UI 组件库 |
| Vue Flow | 1.48.2 | 工作流可视化 |
| ECharts | 5.6.0 | 数据可视化 |
| Pinia | 2.3.1 | 状态管理 |
| Axios | 1.8.4 | HTTP 请求 |

---

## 🚀 快速启动

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| PostgreSQL | 16+（需安装 pgvector 扩展） |
| Redis | 6+ |
| RabbitMQ | 3+（可选） |

### 后端启动

```bash
# 1. 克隆项目
git clone <repo-url>
cd cangjie-agent

# 2. 编译
mvn clean compile -pl cangjie-start -am -DskipTests

# 3. 配置数据库（修改 application-dev.yml）
#    - PostgreSQL 连接信息
#    - Redis 连接信息
#    - MinIO 配置（可选）

# 4. 启动
mvn spring-boot:run -pl cangjie-start -DskipTests
```

### 前端启动

```bash
cd cangjie-agent-ui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问 `http://localhost:5173` 进入 Admin 管理端。

---

## 📁 项目配置

核心配置位于 `cangjie-start/src/main/resources/`：

| 文件 | 说明 |
|------|------|
| `application.yml` | 主配置（数据源、Redis、Flyway、线程池等） |
| `application-dev.yml` | 开发环境配置 |
| `db/migration/V*.sql` | Flyway 数据库迁移脚本 |

---

## 🧪 开发路线

- [x] 大模型管理与调用
- [x] 知识库（RAG）+ 混合检索
- [x] 可视化工作流编排
- [x] 工具与插件 SPI
- [x] 提示词管理
- [x] 智能应用发布
- [x] 多渠道接入
- [x] RBAC 权限管理
- [x] 可观测性（链路追踪/操作日志）
- [x] 文件管理（本地 + MinIO）
- [ ] 更多模型提供商接入
- [ ] 应用市场与模板
- [ ] 更多渠道插件

---

## 📄 开源协议

Copyright © 2025 CangJieCloud. All rights reserved.