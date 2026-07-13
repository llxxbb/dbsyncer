# Triage Labels

This document defines the label vocabulary used by the triage skill for categorizing issues and tasks.

## Canonical Label Set

| Label | Meaning | When to Apply |
|-------|---------|---------------|
| `needs-triage` | Maintainer needs to evaluate the issue | New issues, bug reports, feature requests |
| `needs-info` | Waiting on reporter for more details | Issue is unclear, reproduction steps missing |
| `ready-for-agent` | Fully specified, agent can implement | Specification complete, no human context needed |
| `ready-for-human` | Needs human implementation | Requires developer decision, business logic, or design |
| `wontfix` | Will not be actioned | Out of scope, duplicate, superseded, or invalid |

## Label Usage Rules

1. **Initial State**: All new issues start with `needs-triage`
2. **Evaluation**: After triage, apply ONE of:
   - `needs-info` if waiting for reporter
   - `ready-for-agent` if an AFK agent can implement
   - `ready-for-human` if requires human developer
   - `wontfix` if declining the request
3. **Implementation**: Remove triage labels when work starts
4. **Completion**: Remove all labels when done

## Default Values

All labels use their canonical names exactly as shown above. No customization is applied.

## Notes

- These labels are recorded in task header files (e.g., `00-header.md`) rather than applied as system labels
- The triage skill uses these values to categorize work for downstream skills
- Custom label names can be configured if your workflow differs
