# 实施说明 — ADR-0014 钉钉群消息通知机制

## 变更文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `dbsyncer-plugin/.../plugin/model/NotifyType.java` | 通知事件类型枚举 |
| `dbsyncer-plugin/.../plugin/config/NotifyConfig.java` | 全局通知配置（含 MailConfig/DingTalkConfig） |
| `dbsyncer-plugin/.../plugin/impl/CompositeNotifyService.java` | 组合通知服务 |
| `dbsyncer-plugin/.../plugin/impl/DingTalkNotifyServiceProvider.java` | 钉钉 Webhook 通知 |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `dbsyncer-plugin/.../plugin/model/NotifyMessage.java` | 增加 `type` 字段及 getter/setter |
| `dbsyncer-plugin/.../plugin/impl/MailNotifyServiceProvider.java` | 移除 `@ConditionalOnProperty`/`@ConfigurationProperties`，改从 `NotifyConfig` 读取配置；构造器注入 |
| `dbsyncer-plugin/.../plugin/NotifySupportConfiguration.java` | 注册 `NotifyConfig`，fallback bean 改为 `enabled=false` 时激活 |
| `dbsyncer-parser/.../parser/MessageService.java` | 增加 `sendMessage(String, String, NotifyType)` 重载 |
| `dbsyncer-parser/.../parser/impl/MessageServiceImpl.java` | 实现新重载，`type` 透传 |
| `dbsyncer-parser/.../parser/strategy/impl/FlushStrategyImpl.java` | 增加 `notifyOnError()` 方法，错误数据进入队列时触发通知 |

## 配置示例

```yaml
dbsyncer:
  plugin:
    notify:
      enabled: true
      channels:
        - mail
        - dingtalk
      mail:
        username: "xxx@qq.com"
        password: "***"
      dingtalk:
        webhook-url: "https://oapi.dingtalk.com/robot/send?access_token=***"
        secret: "***"
        keywords: "dbsyncer 警报"
        notify-types:
          - TASK_INTERRUPTED
          - DATA_ERROR_QUEUE
```

## 编译验证

全量 `mvn compile -o` 通过，18 个模块无编译错误。
