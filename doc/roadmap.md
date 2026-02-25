# 待解决的问题或缺陷

## 优先级高的问题

- 为每个转换器提供来源选择
  - F()：原字段
  - C()：其他转换器加工的值
- UI: 过滤条件简化,过滤条件直接输入表达式
- getMetaInfo 满天飞，实际上是表信息。MetaInfo 和 Table 大体相同的区别是啥？有必要存在两个吗？
- 重大隐性bug: 系统配置丢失问题：在数据库里会形成多份“系统配置”，应该为一份，原因原有配置没有加载完成，但请求了 login 接口，导致系统配置重建。

## 问题池

- 存在 ddl 被 jsqlparser 解析多次的情况（一次表过滤的名称解析，一次内容解析）
- 增量-定时 重构
  - 增量定时-移除 AbstractDatabaseConnector.reader, 与 Listener 中重复定义, 但 Reader 语义更好。
- AbstractDatabaseConnector.filterColumn 这个应该在编辑时处理，而不是在运行时处理
- 使用 kafka 作为中间数据源
- 优化：监控界面增加任务结束的记录


