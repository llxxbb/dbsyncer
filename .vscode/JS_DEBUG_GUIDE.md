# JS 前端调试指南

## ❓ 为什么断点是灰色？

灰色断点（灰圈）说明 **VSCode 的调试器没有连接到正在运行的浏览器**。

传统 JSP/HTML 项目的前端代码运行在**浏览器中**，而不是 Node.js。需要通过 **Chrome/Edge DevTools 协议** 附加调试。

---

## ✅ 方案一：使用浏览器 DevTools（最简单，推荐）

1. **启动后端服务**
   ```
   Ctrl+Shift+P → Tasks: Run Task → start-backend-server
   ```

2. **打开浏览器访问**
   ```
   http://127.0.0.1:18686/mapping/page/edit?id=YOUR_ID
   ```

3. **按 F12 打开开发者工具**

4. **在 Sources 面板找到 JS 文件**
   - 路径：`http://127.0.0.1:18686/static/js/mapping/edit.js`
   - 或者按 `Ctrl+P` 搜索 `edit.js`

5. **设置断点**（红色圆点）

6. **刷新页面或触发操作**，断点会暂停执行

**优点**：
- ✅ 无需配置 VSCode
- ✅ 断点实时生效
- ✅ 可以查看变量、调用栈、控制台

---

## ✅ 方案二：VSCode + Edge/Chrome 调试（需要 Windows 浏览器）

### 1. 安装扩展
在 VSCode 中安装：
- **Debugger for Microsoft Edge** (`msjsdiag.debugger-for-edge`)
- 或 **Debugger for Chrome** (`msjsdiag.debugger-for-chrome`)

### 2. 选择调试配置
按 `F5`，选择：
- `Debug Frontend (Edge)` - 使用 Windows Edge
- `Debug Frontend (Chrome - Windows)` - 使用 Windows Chrome

### 3. 配置浏览器路径（如需要）
如果浏览器路径不同，修改 `launch.json` 中的：
```json
"runtimeExecutable": "/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
```

### 4. 启动调试
- 后端会自动启动（通过 `preLaunchTask`）
- 浏览器会自动打开并附加调试器
- 在 VSCode 中设置的断点会变为红色实心圆

---

## ✅ 方案三：在代码中添加 console.log 调试

如果不想配置调试器，直接在 JS 中添加日志：

```javascript
function updateTargetTableName(tableGroupId, newTableName, targetTablePK, $icon) {
    console.log("=== 目标表名称更新 ===");
    console.log("tableGroupId:", tableGroupId);
    console.log("newTableName:", newTableName);
    console.log("targetTablePK:", targetTablePK);
    
    var params = {
        'id': tableGroupId,
        'targetTable': newTableName,
        'targetTablePK': targetTablePK
    };
    
    doPoster("/tableGroup/edit", params, function (response) {
        console.log("响应:", response);
        
        if (response.success == true) {
            bootGrowl("目标表名称更新成功!", "success");
            $icon.siblings('.target-table-name').text(newTableName);
            $icon.data('current-name', newTableName);
        } else {
            if (response.status == 400 && response.resultValue &&
                typeof response.resultValue === 'object' &&
                response.resultValue.errorCode === 'TARGET_TABLE_NOT_EXISTS') {
                console.log("检测到缺表错误:", response.resultValue);
                showCreateTableConfirmDialogForMapping(response.resultValue);
            } else {
                bootGrowl(response.resultValue || "更新失败", "danger");
            }
        }
    });
}
```

然后在浏览器 DevTools 的 **Console** 面板查看日志。

---

## 🎯 推荐调试流程

### 验证修改是否生效

1. **清空浏览器缓存**
   - `Ctrl+Shift+Delete` → 勾选"缓存的图像和文件" → 清除
   - 或按 `Ctrl+F5` 强制刷新

2. **验证 JS 文件是否更新**
   - DevTools → Sources → 打开 `edit.js`
   - 搜索 `TARGET_TABLE_NOT_EXISTS`
   - 确认能看到新添加的错误处理代码

3. **设置断点**
   - 在 `doPoster("/tableGroup/edit", params, function (response) {` 这行设置断点
   - 点击保存按钮触发请求
   - 断点应该暂停执行

4. **检查响应数据**
   - 在断点处查看 `response` 对象
   - 确认 `response.resultValue.errorCode` 的值

---

## 🔧 常见问题

### Q: 修改后浏览器还是旧代码？
A: 强制刷新 `Ctrl+F5`，或清除缓存后刷新

### Q: 断点不触发？
A: 
1. 确认 JS 文件已加载（DevTools → Sources → 能看到文件）
2. 确认断点设置在会执行的代码路径上
3. 尝试添加 `debugger;` 语句强制暂停

### Q: VSCode 断点一直是灰色？
A: 使用浏览器 DevTools 调试更简单，不需要 VSCode 配置

---

## 📌 快速验证步骤

```bash
# 1. 启动后端
Ctrl+Shift+P → Tasks: Run Task → start-backend-server

# 2. 打开浏览器
http://127.0.0.1:18686/

# 3. 按 F12 打开 DevTools

# 4. 找到 edit.js 并设置断点

# 5. 触发保存操作，查看断点是否触发
```
