# DbSyncer 项目

## 项目描述

DBSyncer 是一款开源的数据同步中间件，提供 MySQL、Oracle、SqlServer、PostgreSQL、Elasticsearch、Kafka、File、SQL 等同步场景。

### 核心功能

- **组合驱动**：自定义库同步到库组合，关系型数据库与非关系型之间组合，任意搭配表同步映射关系
- **实时监控**：驱动全量或增量实时同步运行状态、结果、同步日志和系统日志
- **开发插件**：自定义转化同步逻辑
- **异构数据库支持**：支持不同数据库类型间的数据同步和 DDL 同步

## 技术栈

- **后端**: Java 8, Spring Boot 2.5.14
- **前端**: jQuery, Bootstrap, Thymeleaf
- **数据库**: MySQL 5.7/8.0
- **构建工具**: Maven 3.6+

## 项目结构

```
dbsyncer/
├── dbsyncer-biz        # 业务逻辑层
├── dbsyncer-common     # 公共工具库
├── dbsyncer-connector/ # 数据库连接器
│   ├── dbsyncer-connector-base
│   ├── dbsyncer-connector-mysql
│   ├── dbsyncer-connector-oracle
│   ├── dbsyncer-connector-postgresql
│   ├── dbsyncer-connector-sqlserver
│   ├── dbsyncer-connector-elasticsearch
│   ├── dbsyncer-connector-kafka
│   └── dbsyncer-connector-file
├── dbsyncer-manager    # 管理模块
├── dbsyncer-parser     # 解析器
├── dbsyncer-plugin     # 插件系统
├── dbsyncer-sdk        # SDK
├── dbsyncer-storage    # 存储层
└── dbsyncer-web        # Web 应用
```

## 开发规范

- 遵循 GSD 流程进行文档管理和开发
- 所有架构决策记录在 `doc/adr/` 目录
- 实现细节记录在 `doc/history/NNNN/` 目录
- 测试用例清单在 `doc/tests/` 目录

## ADR 索引

查看 [doc/adr/README.md](doc/adr/README.md)
