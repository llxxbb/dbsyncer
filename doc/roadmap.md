# 待解决的问题或缺陷

## 优先级高的问题

- 重大隐性bug: 系统配置丢失问题：在数据库里会形成多份“系统配置”，应该为一份，原因原有配置没有加载完成，但请求了 login 接口，导致系统配置重建。

## 问题池

- sql server with(nolock) 有脏读问题，有概率造成数据不一致。
- 存在 ddl 被 jsqlparser 解析多次的情况（一次表过滤的名称解析，一次内容解析）
- 增量-定时 重构
  - 增量定时-移除 AbstractDatabaseConnector.reader, 与 Listener 中重复定义, 但 Reader 语义更好。
- 使用 kafka 作为中间数据源


