# 使用手册

## kafka 主题覆盖

在驱动配置中可通过扩展参数中指定 topic 来覆盖 kafka 连接器中的的缺省主题。

## 关于单点

本系统只支持单点部署，如同步任务过重，可考虑分拆到多个独立部署中进行处理。

## 关于总数

总数是一个参考值，因为它只在任务创建时统计一次，并不能反映后续的数据变化情况。

## 密码重置

java -jar dbsyncer-web.jar --dbsyncer.admin.password=lxb

## DDL

- Sql server DDL：目前基于 CT(change tracing) 技术在增量 DML 时自动检测字段差异并生成对应的 DDL.特性情况，增量开始且无 DML 操作但进行了 DDL 操作将无法感知，因为用于结构比对的前一数据没有形成。