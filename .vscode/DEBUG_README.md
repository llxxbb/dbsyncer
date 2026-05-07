# VSCode Debug 配置使用说明

## 环境要求

1. **安装扩展**:
   - Spring Boot Extension Pack (v0.23.0+)
   - Spring Boot Dashboard
   -Debugger for Chrome

2. **前提条件**:
   - 已安装 JDK 8+
   - 已安装 Maven 3.6+
   - 数据库配置已正确设置 (`dbsyncer-web/src/main/resources/application.properties`)

## 调试流程

### 方式一：完整调试（推荐）

1. **先编译项目**
   ```
   Ctrl+Shift+P → "Tasks: Run Task" → "build-dbsyncer"
   ```

2. **启动后端服务**
   - 点击左上角 Debug 按钮（或按 F5）
   - 选择 "Debug: Compile & Launch"
   - 等待控制台显示 "Started WebApplication in..."

3. **打开浏览器调试**
   - 选择 "Debug Frontend" 启动 Chrome 调试器
   - 输入你的实际 Mapping ID，例如：`http://127.0.0.1:18686/mapping/page/edit?id=YOUR_ID`

### 方式二：快速启动

1. **编译并启动（一键命令）**
   ```
   Ctrl+Shift+P → "Tasks: Run Task" → "restart-dbsyncer"
   ```

2. **手动打开浏览器**
   - 访问：`http://127.0.0.1:18686/`
   - 进入具体的编辑页面

## 前端调试

### 配置说明

- **WebRoot**: `${workspaceFolder}/dbsyncer-web/src/main/resources/public`
- **调试端口**: 18686
- **调试页面模板**:
  - 映射编辑：`/mapping/page/edit`
  - TableGroup 编辑：`/tableGroup/page/editTableGroup`

### 断点调试技巧

1. 在 `edit.js` 中设置断点：
   - `function updateTargetTableName()` - 目标表名称更新
   - `function submit()` - 保存操作

2. 使用 Console 查看：
   ```javascript
   console.log("表 ID:", $("#id").val());
   console.log("目标表:", $("#targetTable").val());
   console.log("主键:", $("#targetTablePK").val());
   ```

3. 断点条件设置：
   - 右键断点 → "Add breakpoint condition"
   - 例如：`response.resultValue.errorCode === 'TARGET_TABLE_NOT_EXISTS'`

## 常见问题

### Q: 端口 18686 被占用
A: 修改 `application.properties` 中的 `server.port` 配置

### Q: 编译失败
A: 检查 Maven 配置和本地仓库 (`~/.m2/repository`)

### Q: 数据库连接失败
A: 检查 `application.properties` 中的 `dbsyncer.storage.mysql.url` 配置

### Q: 前端断点不生效
A: 确保在调试模式下打开浏览器，不要使用生产模式

## 修改后的代码验证路径

1. **后端**: `TableGroupServiceImpl.java` → `edit()` 方法
   - 断点位置：第 162 行（检查目标表存在性）
   - 断点位置：第 202 行（主键变更 DDL）

2. **前端**: `edit.js` → `updateTargetTableName()` 方法
   - 断点位置：第 383 行（调用 /tableGroup/edit）

## 快捷键

| 快捷键 | 功能 |
|--------|------|
| F5 | 启动调试 |
| Shift+F5 | 停止调试 |
| F9 | 设置/取消断点 |
| F10 | 单步跳过 |
| F11 | 单步进入 |
| Ctrl+Shift+P | 命令面板 |
| Ctrl+Shift+B | 运行任务 |
