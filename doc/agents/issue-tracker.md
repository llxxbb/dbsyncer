# Local Markdown Issue Tracker

**Type**: Local Markdown

**Location**: `.scratch/<feature>/` directory

## Overview

This repository uses a local markdown file-based issue tracker instead of a remote issue tracker (GitHub Issues, GitLab Issues, etc.). All development tasks, bug reports, and feature requests are tracked as markdown files within the repository.

## Directory Structure

```
.scratch/
├── <feature-name>/
│   ├── 00-header.md          # Feature overview and status
│   ├── 01-specification.md   # Detailed specification
│   ├── 02-tasks.md           # Tracer-bullet task breakdown
│   ├── 03-checklist.md       # Verification checklist
│   └── 04-verification.md    # UAT and verification notes
```

## Workflow

1. **Create a new feature folder**: `.scratch/<feature-name>/`
2. **Write `00-header.md`**: Summarize the feature, its status, and status
3. **Use `/to-tickets`**: Generate task breakdown files
4. **Implement tasks**: Work through tasks in order, clearing context between each
5. **Use `/code-review`**: Review changes against spec before committing
6. **Close feature folder**: When all tasks are complete

## Status Values

- `draft` - Still being planned
- `in-progress` - Currently being implemented
- `complete` - All tasks done and verified
- `on-hold` - Temporarily paused

## External PRs as Request Surface

External PRs (e.g., from GitHub or GitLab) are **NOT** automatically pulled into this issue tracker. Only issues/tracked items created within the `.scratch/` directory are managed by this workflow.

## When to Use This

- Solo development projects
- Repositories without public issue trackers
- Projects where you want all work tracking in the repo
- When you prefer file-based over platform-based tracking

## Skills That Read This

- `/triage` - Categorize and prepare issues for implementation
- `/to-tickets` - Break specs into actionable tasks
- `/implement` - Implement individual tickets
- `/grill-with-docs` - Refine requirements before spec
- `/wayfinder` - Map out large, uncertain efforts

## Notes

- Keep one active workstream per feature folder
- Use the `./build.sh` or `./build.cmd` scripts for multi-platform builds
- The `.scratch/` directory is tracked in git for team visibility
