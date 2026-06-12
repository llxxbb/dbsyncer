# DbSyncer 项目路线图

## 当前里程碑

### Milestone v2.26.0

**目标**: 日志查询功能优化和数据模型重构

#### 阶段 16: 日志查询功能分散到任务面板

- **状态**: 进行中
- **负责人**: 系统
- **目标**: 重构日志查询功能，使用 model_type + model_id 双字段
- **ADR**: [0016-日志查询功能分散到任务面板](doc/adr/0016-日志查询功能分散到任务面板.md)
- **实施文档**: `doc/history/0016/`

##### 子阶段

1. **数据模型更新**
   - LogVo.java 添加 modelType、modelId 字段
   - MySQL 表结构更新：ADD COLUMN model_type, model_id
   - ConfigConstant.java 新增日志常量

2. **基础服务重构**
   - BaseServiceImpl.log() 统一为三参数方法
   - 实现 getModelType() 和 getModelId() 辅助方法
   - TableGroup 日志关联到父级 Mapping

3. **监控服务更新**
   - MonitorServiceImpl.queryLog() 支持多维度过滤
   - MonitorServiceImpl.clearLog() 按 modelId 清空

4. **前端改造**
   - 任务页面新增日志查询 Tab
   - 监控页面移除日志查询功能
   - 日志列表显示 modelType 字段

##### 验收标准

- [ ] 表结构包含 model_type 和 model_id 字段
- [ ] LogVo 字段与实际数据库表对齐
- [ ] 日志列表支持按类型过滤
- [ ] TableGroup 日志正确关联到父级 Mapping
- [ ] 监控页面日志查询功能已移除

---

## 待规划阶段

### Milestone v2.27.0 - 性能优化

- **规划中**: 优化日志查询性能，添加缓存机制
- **规划中**: 数据库连接池优化

### Milestone v2.28.0 - 插件扩展

- **规划中**: 插件热加载机制
- **规划中**: 插件市场/仓库

### Milestone v2.29.0 - 监控增强

- **规划中**: Prometheus 指标暴露
- **规划中**: Grafana 仪表盘集成

---

## 规划流程

1. **需求阶段**: 收集用户需求和技术债务
2. **规划阶段**: 评估影响范围和实施成本
3. **实施阶段**: 分步骤实现，遵循 ADR 流程
4. **验收阶段**: 验证功能和质量标准

## GSD 文档规范

- **ADR 文档**: `doc/adr/NNNN-*.md`
- **设计文档**: `doc/history/NNNN/design-base.md`
- **实现说明**: `doc/history/NNNN/impl-notes.md`
- **设计审查**: `doc/history/NNNN/review-design.md`
- **测试要求**: `doc/history/NNNN/test-requirements.md`
