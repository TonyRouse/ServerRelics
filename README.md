# ServerRelics

A Minecraft Paper plugin for creating unique, trackable server-wide relics with special restrictions and integrations.

## Features

- **Unique Relics**: Items that can only exist once in the world
- **Configurable Restrictions**: Control storage, PvP, death behavior, and more
- **BlueMap Integration**: Live location tracking on your web map
- **PlaceholderAPI Support**: Display relic stats anywhere
- **Full Statistics**: Track hold times with leaderboards
- **Crash Protection**: State saved frequently, integrity checks for missing relics
- **Offline Handling**: Configurable expiration when holders go offline

## Default Relic: Crown of the Server

The plugin comes pre-configured with the "Crown of the Server" - a unique helmet that:

- Cannot be stored in containers (chests, ender chests, shulkers, hoppers)
- Forces PvP enabled while held
- Always drops on death (bypasses grave plugins)
- Never despawns when dropped
- Shows holder location on BlueMap
- Broadcasts ownership changes server-wide
- Grants potion effects (Strength, Speed, Health Boost, Glowing)

## Requirements

- Paper 1.20.4+ (tested on 1.21+)
- Java 17+

### Optional Dependencies

- **PlaceholderAPI** - For placeholder support
- **BlueMap** - For map marker integration
- **PvPManager** - For forced PvP on relic holders

## Installation

1. Download `ServerRelics-1.0.0.jar`
2. Place in your server's `plugins/` folder
3. Restart the server
4. Configure `plugins/ServerRelics/config.yml` as needed
5. Use `/relic spawn crown` to create the first crown

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/relic help` | Show help | None |
| `/relic spawn <relic>` | Spawn a relic at your location | `serverrelics.admin` |
| `/relic despawn <relic>` | Remove a relic from existence | `serverrelics.admin` |
| `/relic give <player> <relic>` | Give a relic to a player | `serverrelics.admin` |
| `/relic take <player>` | Remove relic from a player | `serverrelics.admin` |
| `/relic drop <relic>` | Force drop from offline holder | `serverrelics.admin` |
| `/relic locate [relic]` | Broadcast relic location to all players | None |
| `/relic stats [player]` | View relic statistics | None |
| `/relic leaderboard [relic]` | View top holders | None |
| `/relic reload` | Reload configuration | `serverrelics.admin` |

## Permissions

### Permission Groups (for LuckPerms)

| Permission | Description | Default |
|------------|-------------|---------|
| `serverrelics.*` | All permissions | op |
| `serverrelics.admin` | Admin commands (give, take, spawn, despawn, drop, reload) | op |
| `serverrelics.user` | User commands (locate, stats, leaderboard) + notifications | true |

### Individual Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `serverrelics.command.locate` | Use `/relic locate` | true |
| `serverrelics.command.stats` | Use `/relic stats` | true |
| `serverrelics.command.leaderboard` | Use `/relic leaderboard` | true |
| `serverrelics.command.give` | Use `/relic give` | op |
| `serverrelics.command.take` | Use `/relic take` | op |
| `serverrelics.command.spawn` | Use `/relic spawn` | op |
| `serverrelics.command.despawn` | Use `/relic despawn` | op |
| `serverrelics.command.drop` | Use `/relic drop` | op |
| `serverrelics.command.reload` | Use `/relic reload` | op |
| `serverrelics.bypass.pvp` | Bypass forced PvP while holding relic | false |
| `serverrelics.bypass.storage` | Bypass storage restrictions | false |
| `serverrelics.notify` | Receive relic broadcasts | true |
| `serverrelics.notify.admin` | Receive admin notifications (missing relics) | op |

## Configuration

The plugin auto-generates `config.yml` on first run. Key settings:

```yaml
# Database (sqlite works out of box, mysql available)
database:
  type: sqlite

# Crown configuration
crown:
  enabled: true
  display-name: "&6&l Crown of the Server &6&l"

  # Potion effects applied to holder
  effects:
    - "INCREASE_DAMAGE:0"    # Strength I
    - "SPEED:0"              # Speed I
    - "HEALTH_BOOST:1"       # +4 hearts
    - "GLOWING:0"            # Always visible

  restrictions:
    no-chest-storage: true
    no-ender-chest: true
    no-shulker-box: true
    no-hopper: true
    force-pvp: true
    bypass-graves: true
    never-despawn: true
    drop-on-logout: false

    # Crown stays with offline players, drops after 10 days
    offline-expiration:
      enabled: true
      days: 10
      drop-radius: 50

  # BlueMap marker settings
  bluemap:
    enabled: true
    marker-label: "Crown Holder"
    update-interval: 30
```

## PlaceholderAPI Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%relics_crown_holder%` | Current holder's name |
| `%relics_crown_holder_time%` | Current reign duration |
| `%relics_crown_total_time%` | Your total time as holder |
| `%relics_crown_total_time_<player>%` | Specific player's total time |
| `%relics_crown_top_<1-10>_name%` | Leaderboard player names |
| `%relics_crown_top_<1-10>_time%` | Leaderboard times |
| `%relics_crown_rank%` | Your leaderboard position |

## Creating Custom Relics

Add new relics in `config.yml` under the `relics` section following the crown template. Each relic needs:

- Unique ID
- Material type
- Display name and lore
- Restriction settings
- Optional effects and BlueMap config

## Edge Cases Handled

- **Player banned**: Relic drops at their location
- **Creative mode**: Players cannot hold relics in creative
- **Full inventory**: Pickup blocked until space available
- **Server crash**: State saved every minute
- **World deleted**: Relic respawns at main world spawn
- **Relic missing**: Admins notified every 5 minutes
- **Hopper exploits**: Relics cannot be moved by hoppers/droppers

## Building from Source

```bash
git clone https://github.com/yourusername/ServerRelics.git
cd ServerRelics
mvn clean package -Dmaven.test.skip=true
```

The built JAR will be in `target/ServerRelics-1.0.0.jar`

## License

MIT License - See [LICENSE](LICENSE) for details.

## Support

- Issues: [GitHub Issues](https://github.com/yourusername/ServerRelics/issues)
- Originally created for [SteadFast SMP](https://steadfastsmp.com)
