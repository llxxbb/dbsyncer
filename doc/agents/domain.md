# Domain Documentation

This document defines the layout and consumer rules for domain documentation in this repository.

## Layout: Single Context

This repository uses a **single-context** layout:

```
doc/
├── CONTEXT.md           # Global domain language and glos
├── ADR/                 # Architectural Decision Records
│   ├── 0001-title.md
│   └── ...
└── agents/              # Agent configuration docs
```

## CONTEXT.md

**Location**: `doc/CONTEXT.md`

**Purpose**: Central glossary and domain language reference for all agent skills.

**Contents**:
- Ubiquitous language terminology
- Domain entities and their meanings
- Cross-references to ADRs
- Key business rules and constraints

**Consumer Rules**:
- Skills like `/implement`, `/tdd`, `/diagnosing-bugs` read this file to understand domain context
- Keep terminology consistent with DDD principles
- Update when domain language evolves
- Write in clear, agent-friendly markdown

## ADR (Architectural Decision Records)

**Location**: `doc/adr/`

**Purpose**: Record important architectural decisions with rationale.

**Format**: Use the `decision-tracker` skill to maintain ADRs.

**Consumer Rules**:
- Skills read ADRs to understand past decisions and constraints
- New features should reference relevant ADRs
- Each ADR should have: context, decision, consequences

## Files That Read Domain Docs

- **`/codebase-design`** - Uses domain language for interface design
- **`/tdd`** - Understands domain entities when writing tests
- **`/implement`** - Applies domain rules in implementation
- **`/diagnosing-bugs`** - Uses domain logic for debugging
- **`/improve-codebase-architecture`** - Respects architectural decisions

## When to Update

- **CONTEXT.md**: When new domain terminology is coined or clarified
- **ADR/**: When making important architectural decisions
- **triage-labels.md**: When changing the triage workflow

## Notes

- Single-context layout means all domain knowledge is in one place
- For monorepos with distinct contexts, use multi-context layout instead
- Keep domain docs current - stale docs lead to misaligned implementations
