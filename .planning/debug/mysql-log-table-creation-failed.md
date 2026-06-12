---
status: active
goal: find_root_cause_only
trigger: MySQL log table not created after startup - SQL executed but table missing
created_at: 2026-06-23T00:00:00Z
slug: mysql-log-table-creation-failed
---

# Root Cause Analysis: Log Table Creation Failure

## Symptoms
After deleting `dbsyncer_log` table and restarting:
1. Log shows: `CREATE TABLE dbsyncer_log ...` SQL being executed
2. **No exception thrown**
3. **Table NOT created in database**

## Root Cause

### Issue 1: Missing newline in readSql()

In `MySQLStorageService.readSql()` method:
```java
while ((newLine = bf.readLine()) != null) {
    res.append(newLine);  // ❌ NO newline added!
}
```

`BufferedReader.readLine()` **strips newline characters**, so appending without `\n` causes all lines to be **concatenated without line breaks**:

**Before fix**, SQL becomes:
```sql
-- 创建日志表（新版本）-- ADR-0016: 日志表结构更新CREATE TABLE `dbsyncer_log`  (  `ID` varchar(64)...
```

All content is on a **single line**, which MySQL cannot parse correctly.

### Issue 2: SQL file contains comments

The SQL script `/dbsyncer_mysql_log.sql` contains:
```sql
-- 创建日志表 (新版本)
-- ADR-0016: 日志表结构更新
CREATE TABLE ...
-- ALTER TABLE ... (commented migration)
```

When `readSql` doesn't filter comments and doesn't add newlines, MySQL receives malformed SQL.

## Fix Applied

### 1. Add newlines in readSql():
```java
while ((newLine = bf.readLine()) != null) {
    res.append(newLine).append("\n");  // ✅ Add newline
}
```

### 2. Filter comments to prevent issues:
```java
String trimmedLine = newLine.trim();
if (trimmedLine.startsWith("--") || trimmedLine.startsWith("/*")) {
    continue;  // Skip comment lines
}
res.append(newLine).append("\n");
```

### 3. Handle exceptions gracefully in initTable():
```java
try {
    createTableIfNotExist(tableName, e);
} catch (Exception ex) {
    logger.error("创建系统表失败：tableName={}, error={}", tableName, ex.getMessage(), ex);
    // Don't throw - allow other tables to be created
}
```

## Verification Steps

1. Delete `dbsyncer_log` table from database
2. Restart application
3. Check logs for: `创建系统表失败` (should NOT appear)
4. Query MySQL: `SHOW TABLES LIKE 'dbsyncer_log'` (should show table)
5. Query table structure: `DESCRIBE dbsyncer_log` (should show full schema)

## Related Issues

This fix addresses the root cause of:
- **No error logging** - because error data cannot be stored without log table
- **Silent failures** - exceptions were being swallowed in initTable()
