# <img src="icon.svg" width="25" height="25" alt="icon"> HeliumCore

![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.9-green.svg)
![Paper](https://img.shields.io/badge/Paper-API-blue.svg)
![Version](https://img.shields.io/badge/Version-1.2-red.svg)

**Lightweight, modular, and high‑performance plugin for your Minecraft server**

[Features](#features) • [Commands](#commands) • [Configuration](#configuration)

---

## 📖 Overview

**HeliumCore** is a lightweight, modular, and high‑performance plugin designed for Minecraft servers with a focus on scalability, compatibility, and flexibility. The platform provides a powerful toolkit for managing the server, players, and economy.

### ✨ Key Features

- 🎯 **High performance** — optimized code for maximum speed
- 🔧 **Modular architecture** — easily extensible system with independent components
- 🛡️ **Security** — built‑in moderation and exploit protection
- 💰 **Economy** — first‑class HeliumCoin currency with full functionality
- 👥 **Social features** — friends system, teleportation, and communication
- 🎨 **Customization** — flexible configuration for any server needs
- 📱 **Telegram integration** — notifications and server control via a Telegram bot

---

## 🎮 Features

### 🏠 Homes and Teleportation
- Set and teleport to home (`/home`, `/sethome`)
- Waypoint teleportation system (`/goto`)
- Lobby with a configurable spawn point (`/lobby`)

### 👥 Social
- **Friends system** — add, remove, and teleport to friends
- **Private messages** — secure player‑to‑player communication
- **Teleport requests** — TPA system with confirmation

### 💰 Economy
- **HeliumCoin** — the server’s own currency
- Player balance management
- Economic operations (transfers, purchases)

### 🛡️ Moderation and Security
- Ban and mute system
- Chat clearing
- Anti‑lag system
- Whitelist with web interface

### 🎨 Customization
- Skin system with SkinsRestorer support
- Customizable tablist and player lists
- Flexible ranks and permissions

### 📱 Telegram Integration
- **Real‑time notifications** — receive server event alerts
- **Remote control** — manage the server via a Telegram bot
- **Player monitoring** — track activity and statistics
- **Alerting system** — configure notifications for important events

---

## 🚀 Installation

1. Requirements
   - Java 17+
   - Paper/Velocity-compatible server (Paper 1.21.9 is recommended)

2. Download the plugin
   - Option A: Build locally (see section “Development → Build from Source”), resulting JAR: `target/heliumcore.jar`
   - Option B: Use a prebuilt JAR if available

3. Install
   - Stop your Minecraft server
   - Copy `heliumcore.jar` to your server’s `plugins` directory

4. First run (generate configs)
   - Start the server once to generate configuration files in `plugins/HeliumCore` (or the default config location)

5. Configure
   - Open `config.yml` and adjust:
     - `database` (MySQL or SQLite)
     - `economy` (currency name, starting balance)
     - `teleportation` (home limits, cooldowns)

6. Permissions
   - See the “Permissions” section below for keys like `helium.admin`, `helium.mod`

7. Reload/Restart
   - Restart the server to apply changes (preferred) or use your plugin manager to reload

8. Verify
   - Run `/help` or commands from the “Commands” section to ensure the plugin is active

Tip: For SkinsRestorer features, install and configure the SkinsRestorer plugin as well.

---

## 📋 Commands

### 🎮 General Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/home` | Teleport home | Everyone |
| `/sethome` | Set home | Everyone |
| `/lobby` | Teleport to lobby | Everyone |
| `/goto` | Teleport to a waypoint | Everyone |
| `/help` | List commands | Everyone |
| `/status` | Server status and ping | Everyone |
| `/msg <player/enable/disable>` | Private message or toggle PMs | Everyone |
| `/sup <message>` | Message moderators | Everyone |

### 👥 Social Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/friend <help/list/add/remove/accept/decline/tp>` | Friends system | `/friend add PlayerName` |
| `/tpa <player/accept/decline>` | Teleport request | `/tpa PlayerName` |

### 💰 Economy Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/coin balance` | Check balance | Everyone |
| `/coin give <player> <amount>` | Give currency | `helium.admin` |
| `/coin take <player> <amount>` | Take currency | `helium.admin` |
| `/coin set <player> <amount>` | Set balance | `helium.admin` |
| `/coin economy` | Economy info | `helium.admin` |

### 🛡️ Moderation

| Command | Description | Permission |
|---------|-------------|------------|
| `/ban <player> <time> <reason>` | Ban a player | `helium.mod` |
| `/unban <player>` | Unban a player | `helium.mod` |
| `/kick <player> <reason>` | Kick a player | `helium.mod` |
| `/mute <player> <time> <reason>` | Mute a player | `helium.mod` |
| `/unmute <player>` | Unmute a player | `helium.mod` |
| `/clearchat` | Clear chat | `helium.mod` |

### 🛠️ Admin/Server

| Command | Description | Permission |
|---------|-------------|------------|
| `/helium <reload/restart/optime>` | Reload/restart Helium configs | `helium.admin` |
| `/setlobby` | Set lobby spawn | `helium.admin` |
| `/hwl <add/remove> <player>` | Whitelist management | `helium.admin` |
| `/gm <mode>` | Change gamemode | `helium.admin` |
| `/fly` | Toggle flight | `helium.mod` |
| `/say <message>` | Broadcast from staff | `helium.mod` |
| `/skin <set/clear>` | Manage player skin | Everyone |
---

## ⚙️ Configuration

### Basic Settings

```yaml
# config.yml
database:
  type: "mysql"  # mysql or sqlite
  host: "localhost"
  port: 3306
  database: "heliumcore"
  username: "root"
  password: "password"

economy:
  currency_name: "HeliumCoin"
  starting_balance: 1000
  max_balance: 999999999

teleportation:
  home_limit: 1
  tpa_timeout: 60
  goto_cooldown: 30
```

### Permissions

```yaml
permissions:
  helium.admin:
    description: "Full access to administrative features"
    default: op
  helium.mod:
    description: "Access to moderator features"
    default: op

```

---

## 🔧 Development

### Build from Source

```bash
# Clone
git clone https://github.com/valjev/heliumcore.git
cd heliumcore

# Build
mvn clean package

# Run tests
mvn test
```

### Project Structure

```
src/main/java/ru/helium/core/
├── auth/           # Authentication system
├── commands/       # Plugin commands
├── database/       # Database access
├── gui/            # User interfaces
├── listeners/      # Event handlers
└── utils/          # Utilities and helper classes
```

---  
  
## 📦 Dependencies  
  
HeliumCore uses the following third‑party libraries and platforms:  
  
| Dependency     | Description                                    | Link |
|----------------|------------------------------------------------|------|
| **Paper API**  | High‑performance API for Minecraft servers     | [PaperMC](https://github.com/PaperMC/Paper) |
| **SkinsRestorer** | Skin restoration and integration            | [SkinsRestorer](https://github.com/SkinsRestorer/SkinsRestorer) |





---

[GitHub](https://github.com/valjev/heliumcore) • [Issues](https://github.com/valjev/heliumcore/issues) • [Discussions](https://github.com/valjev/heliumcore/discussions)
