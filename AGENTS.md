# DbSyncer Agent Guidelines

请使用中文交流

## Quick Start

- **Use `coder` skill** for all programming tasks
- **Use `decision-tracker` skill** for architecture changes
- 应用 GSD 技能生成文档时，不要重复 `decisioin-tracker` 生成的内容，但可引用。
- **Language**: Java 8, Spring Boot 2.5.14

## Build & Run

```bash
# Build (creates dbsyncer-*.zip)
./build.sh

# Run locally
cd dbsyncer-web
mvn spring-boot:run

# VSCode: Ctrl+Shift+P → Tasks: Run Task → build-dbsyncer
```

## Project Structure

```
dbsyncer/
├── dbsyncer-biz        # Business logic
├── commons             # Common utilities
├── dbsyncer-connector/ # Connectors (mysql, oracle, postgresql, elasticsearch, etc.)
├── dbsyncer-manager    # Management
├── dbsyncer-plugin     # Plugin system
├── dbsyncer-sdk        # SDK
├── dbsyncer-storage    # Storage
└── dbsyncer-web        # Web UI (Spring Boot + Thymeleaf)
```

## Critical Rules

### String Comparison (Case-Insensitive)

**NEVER** use `.toLowerCase()`, `.toUpperCase()`, or `.equalsIgnoreCase()` directly.

Use these instead:

```java
// Field name comparison
field.matchesName(targetName)  // ADR-0008

// String comparison  
StringUtil.equalsIgnoreCase(str1, str2)
```

**Why**: Locale safety, null safety, single source of truth.

### API Response Fields

**NEVER** assume response field names. Always check the actual backend response structure.

Standard response format:
```javascript
// Backend returns:
{ success: true, resultValue: [...], status: ... }

// Frontend should read:
res.resultValue  // ✅ Correct
res.data         // ❌ Wrong - field doesn't exist
```

**Why**: Backend uses `resultValue`, not `data`. Mismatch causes silent failures.

### Read Project Conventions

Before coding, check `.openclaw/conventions.md` for project-specific rules. This file takes priority over general coding standards.

## Development Workflow

### Debugging (VSCode)

1. **Full debug session**: `Debug: Compile & Launch`
   - Backend: `http://127.0.0.1:18686/`
   - Frontend: Chrome Debug (121:18686)

2. **Quick restart**: `Ctrl+Shift+P` → `restart-dbsyncer`

### Default Access

- **URL**: `http://127.0.0.1:18686`
- **Login**: `admin/admin`

## Testing

```bash
# Run all tests
mvn test

# Run single module tests
cd dbsyncer-common && mvn test
```

## Environment

- **JDK**: 1.8 (required)
- **Maven**: 3.6+
- **Port**: 18686

## Debug Port

Default: 5005 (can conflict). Check `application.properties` for `server.port` and Java agent settings.

## 字符串比较

**禁止**直接调用 `.toLowerCase()` / `.toUpperCase()` / `.equalsIgnoreCase()`。使用 `StringUtil.equalsIgnoreCase()` 或 `field.matchesName()`（ADR-0008）。

## Agent skills

### Issue tracker

Local Markdown under `.scratch/<feature>/`. External PRs are not a triage surface. See `doc/agents/issue-tracker.md`.

### Triage labels

All five canonical labels use default names: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `doc/agents/triage-labels.md`.

### Domain docs

Single-context layout: `doc/CONTEXT.md` + `doc/adr/`. See `doc/agents/domain.md`.
