---
name: cass
description: Use this skill when you need to search across agent conversation history (Claude Code, Codex, Cursor, Gemini, Aider, ChatGPT). Invoke when looking for past solutions, debugging patterns, or cross-agent knowledge. Also use for the cass-memory system (cm) to manage learned rules and playbooks.
---

# cass - Search All Your Agent History

What: cass indexes conversations from Claude Code, Codex, Cursor, Gemini, Aider, ChatGPT, and more into a unified, searchable index. Before solving a problem from scratch, check if any agent already solved something similar.

**NEVER run bare `cass`** - it launches an interactive TUI. Always use `--robot` or `--json`.

## Quick Start

```bash
# Check if index is healthy (exit 0=ok, 1=run index first)
cass health

# Search across all agent histories
cass search "authentication error" --robot --limit 5

# View a specific result (from search output)
cass view /path/to/session.jsonl -n 42 --json

# Expand context around a line
cass expand /path/to/session.jsonl -n 42 -C 3 --json

# Learn the full API
cass capabilities --json  # Feature discovery
cass robot-docs guide     # LLM-optimized docs
```

## Why Use It

- **Cross-agent knowledge**: Find solutions from Codex when using Claude, or vice versa
- **Forgiving syntax**: Typos and wrong flags are auto-corrected with teaching notes
- **Token-efficient**: `--fields minimal` returns only essential data

## Key Flags

| Flag             | Purpose                                                |
|------------------|--------------------------------------------------------|
| --robot / --json | Machine-readable JSON output (required!)               |
| --fields minimal | Reduce payload: source_path, line_number, agent only  |
| --limit N        | Cap result count                                       |
| --agent NAME     | Filter to specific agent (claude, codex, cursor, etc.) |
| --days N         | Limit to recent N days                                 |

stdout = data only, stderr = diagnostics. Exit 0 = success.

## Memory System: cass-memory (cm)

The Cass Memory System (cm) is a tool for giving agents an effective memory based on the ability to quickly search across previous coding agent sessions across an array of different coding agent tools (e.g., Claude Code, Codex, Gemini-CLI, Cursor, etc) and projects (and even across multiple machines, optionally) and then reflect on what they find and learn in new sessions to draw out useful lessons and takeaways; these lessons are then stored and can be queried and retrieved later, much like how human memory works.

The `cm onboard` command guides you through analyzing historical sessions and extracting valuable rules.

### Quick Start

```bash
# 1. Check status and see recommendations
cm onboard status

# 2. Get sessions to analyze (filtered by gaps in your playbook)
cm onboard sample --fill-gaps

# 3. Read a session with rich context
cm onboard read /path/to/session.jsonl --template

# 4. Add extracted rules (one at a time or batch)
cm playbook add "Your rule content" --category "debugging"
# Or batch add:
cm playbook add --file rules.json

# 5. Mark session as processed
cm onboard mark-done /path/to/session.jsonl
```
