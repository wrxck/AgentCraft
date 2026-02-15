# AgentCraft

AI-powered NPC agents for Minecraft, driven by Claude.

> **Experimental** — This project is in early alpha. Expect breaking changes, rough edges, and the occasional NPC existential crisis.

## What is AgentCraft?

AgentCraft is a Spigot plugin that spawns intelligent NPC agents into your Minecraft world. Each agent is backed by Claude (via CLI or API), has a unique personality profile, and can autonomously navigate, build, mine, farm, craft, and converse with players. Agents remember past interactions through a vector-based long-term memory system and persist across server restarts.

## Features

- **Packet-based NPCs** — Pure ProtocolLib implementation, no NMS. Agents appear as real players with skins, equipment, and animations.
- **35 tools** — Agents can break, place, craft, smelt, farm, breed, eat, sleep, store items, build structures, go on expeditions, and more.
- **A\* pathfinding** — Custom pathfinder with per-tick iteration budgets, step-up/drop-down support, and macro-scale navigation for long distances.
- **Long-term memory** — Qdrant vector database with sentence-transformer embeddings. Agents remember players, conversations, and experiences across sessions.
- **Autonomous behavior** — Agents think and act independently when idle, driven by their personality profile.
- **Expeditions** — Send agents on mining, gathering, or exploration missions. Includes tunnel mining, branch mining, and cave exploration strategies.
- **Persistence** — Full PostgreSQL-backed state persistence. Agents survive server restarts with their inventory, location, and memory intact.
- **Agent profiles** — 8 distinct personalities (builder, explorer, farmer, guard, hermit, merchant, miner, trickster), each with unique names, skins, and behavioral traits.
- **Conversation system** — Debounced chat with proximity detection. Agents respond in character with context from their memory and current environment.
- **Chunk force-loading** — Agents remain active even when no players are nearby.

## Architecture

| Component | Role |
|---|---|
| **BehaviorController** | Tick-based state machine driving agent behavior at 1-tick intervals |
| **NavigationController** | A\* pathfinding with 200-iteration per-tick budget, 4-directional movement |
| **ActionQueue** | Sequential action execution (move, break, place, etc.) |
| **ConversationManager** | Debounced chat handling with busy-state management |
| **MemoryManager** | Vector similarity search over agent memories via Qdrant |
| **AutonomousController** | Periodic independent thinking cycles for idle agents |
| **ToolRegistry** | 35 registered tools that agents can invoke through Claude |
| **PersistenceManager** | PostgreSQL-backed save/restore of agent state |
| **ExpeditionController** | Long-running gathering missions with multiple mining strategies |

## Requirements

- Java 21
- Spigot 1.21.1
- [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) 5.4.0+
- PostgreSQL (agent persistence)
- [Qdrant](https://qdrant.tech/) (vector memory)
- Sentence-transformers embedding service
- Claude CLI or Anthropic API key

## Infrastructure

AgentCraft runs as a Docker Compose stack with three services:

| Service | Image | Purpose |
|---|---|---|
| `minecraft` | Spigot 1.21.1 | Game server with AgentCraft plugin |
| `qdrant` | qdrant/qdrant | Vector database for agent memory |
| `embeddings` | sentence-transformers | Text embedding for memory similarity search |

PostgreSQL, MongoDB, and Redis run on a shared `databases` network managed separately.

## Configuration

`plugins/AgentCraft/config.yml`:

| Key | Default | Description |
|---|---|---|
| `claude-cli-path` | `/usr/local/bin/claude` | Path to Claude CLI binary |
| `llm-provider` | `cli` | LLM backend: `cli` or `api` |
| `anthropic-api-key` | — | Required if `llm-provider` is `api` |
| `chat-model` | `haiku` | Model for conversations (`haiku` or `sonnet`) |
| `max-turns` | `25` | Max agentic turns per task |
| `action-delay-ticks` | `4` | Ticks between actions (20 = 1 second) |
| `chat-radius` | `15` | Block radius for addressing agents |
| `chat-debounce-seconds` | `2` | Seconds to wait before NPC responds |
| `default-profile` | `builder` | Default agent personality |
| `rate-limit-tokens` | `5` | Rate limiter tokens per player |
| `rate-limit-refill-seconds` | `60` | Token refill interval |
| `allowed-players` | `[]` | Whitelist (empty = allow all) |
| `chat-players` | `[]` | Players whose chat is forwarded to NPCs |
| `memory.qdrant-host` | `qdrant` | Qdrant hostname |
| `memory.qdrant-port` | `6333` | Qdrant port |
| `memory.embeddings-host` | `embeddings` | Embeddings service hostname |
| `memory.embeddings-port` | `8090` | Embeddings service port |
| `autonomous.enabled` | `true` | Enable autonomous behavior |
| `autonomous.interval-seconds` | `60` | Seconds between autonomous cycles |
| `chunk-forcing.enabled` | `true` | Keep agent chunks loaded |
| `chunk-forcing.radius` | `1` | Chunk load radius around agents |

## Commands

All commands use `/agent` (alias: `/ac`).

| Command | Permission | Description |
|---|---|---|
| `/agent spawn <profile> [count]` | `agentcraft.admin` | Spawn agents (1-10) of a profile |
| `/agent spawnall` | `agentcraft.admin` | Spawn one of each profile |
| `/agent despawn <name>` | `agentcraft.admin` | Remove an agent |
| `/agent task <name> <task>` | `agentcraft.use` | Assign a task to an agent |
| `/agent expedition <name> <material> [count]` | `agentcraft.use` | Send agent on a gathering mission |
| `/agent list` | — | List all active agents |
| `/agent status <name>` | — | Show agent details |
| `/agent stop <name>` | `agentcraft.admin` | Stop current task |
| `/agent tp <name>` | `agentcraft.use` | Teleport to an agent |
| `/agent recall <name>` | `agentcraft.use` | Teleport agent to you |
| `/agent cancel <name>` | `agentcraft.use` | Cancel active expedition |
| `/agent output <name>` | — | Show agent's output directory |
| `/agent reload` | `agentcraft.admin` | Reload config and profiles |
| `/agent help` | — | Show command list |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `agentcraft.admin` | op | Spawn, despawn, stop agents and reload config |
| `agentcraft.use` | true | Give tasks to agents and interact with them |

## Agent Profiles

Each profile defines a personality with unique names, skins, specialty, fears, desires, temperament, and quirks.

| Profile | Specialty |
|---|---|
| **Builder** | Construction and design |
| **Explorer** | Discovery and adventure |
| **Farmer** | Agriculture and animal husbandry |
| **Guard** | Protection and combat |
| **Hermit** | Solitude and survival |
| **Merchant** | Trading and commerce |
| **Miner** | Excavation and ore extraction |
| **Trickster** | Mischief and pranks |

## Building from Source

```bash
# Requires Java 21 and Maven
mvn clean package

# Output: target/AgentCraft-0.1.0-alpha.jar
# Also copies to plugins/ directory via shade plugin
```

## License

TBD
