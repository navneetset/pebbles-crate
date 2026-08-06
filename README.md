# Pebble's Crates

Loot crates for Fabric servers. Turn any block into a crate, hand out keys, and let players roll for
prizes with an animated reveal above the block. Everything a crate does is driven by JSON files you
edit on disk — there is no in-game loot editor to learn.

[Modrinth](https://modrinth.com/mod/pebbles-crate) ·
[Web config editor](https://pebblescrate.sethi.tech/) ·
[AdvancedCrates migration helper](https://pebblescrate.sethi.tech/)

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | Fabric, loader 0.16.5 or newer |
| Java | 21 |
| Required mod | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Required mod | [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) |
| Optional | [LuckPerms](https://luckperms.net/) — for permission nodes instead of op level |
| Optional | MongoDB — only for cross-server virtual key wallets |

Server-side. Players do not need the mod installed.

## Quickstart

1. Start the server once with the mod installed. It creates `config/pebbles-crate/` with `crates/`,
   `config.json` and `messages.json`.
2. Write a crate file, e.g. `config/pebbles-crate/crates/vanilla.json` (see
   [Crate configuration](#crate-configuration)). The filename does not matter; the `crateName`
   inside it does.
3. `/padmin reload`
4. `/padmin getcrate Vanilla Items Crate` — you get a paper "crate transformer".
5. Right-click any block with that paper. The block is now a crate.
6. `/padmin givekey <player> 1 Vanilla Items Crate`
7. Right-click the crate holding the key. The prize rolls above the block and the reward commands run.

Right-clicking a crate **without** a key opens a read-only preview of everything inside it and the
odds of each prize.

## Crate configuration

One file per crate in `config/pebbles-crate/crates/`. Files are read in alphabetical order; if two
files declare the same `crateName` the later one wins and a warning is logged.

```json
{
  "crateName": "Vanilla Items Crate",
  "screenName": "&6&lVanilla Crate",
  "crateKey": {
    "material": "minecraft:tripwire_hook",
    "name": "&#FFBF00Vanilla Key",
    "lore": [
      "&#FFDC73• Opens a Vanilla Items Crate"
    ]
  },
  "prize": [
    {
      "name": "&#63BC5DDiamond",
      "material": "minecraft:diamond",
      "amount": 1,
      "commands": [
        "give {player_name} minecraft:diamond 1"
      ],
      "broadcast": "&6{player_name} &fhas received {prize_name} &ffrom {crate_name}",
      "messageToOpener": "&6[Pebble's Crates] &fYou got {prize_name}",
      "lore": [
        "Chance of getting the drop: {chance}%"
      ],
      "chance": 40
    },
    {
      "name": "&rElytra",
      "material": "minecraft:elytra",
      "amount": 1,
      "commands": [
        "give {player_name} minecraft:elytra 1"
      ],
      "chance": 5
    }
  ]
}
```

### Crate fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| `crateName` | string | yes | The crate's identity. Used by every command, by keys, and by the placed-crate registry. Renaming it orphans existing keys and placed crates. |
| `screenName` | string | no | The title shown on the preview screen. Purely cosmetic; defaults to `crateName`. |
| `crateKey` | object | yes | The item that opens this crate. |
| `virtualKey` | boolean | no | Per-crate override for [virtual keys](#virtual-keys). Omit it to follow the global setting. |
| `style` | object | no | Particles, sounds and animation for this crate — see [Per-crate style overrides](#per-crate-style-overrides). Omit it to follow `config.json`. |
| `prize` | array | yes | The prize pool. A crate with an empty pool cannot be opened. |

### Key fields (`crateKey`)

| Field | Type | Required | Meaning |
|---|---|---|---|
| `material` | string | yes | Item id, e.g. `minecraft:tripwire_hook`. |
| `name` | string | yes | Display name; supports colour codes. |
| `lore` | array of string | yes | Lore lines; supports colour codes. Use `[]` for none. |
| `nbt` | string | no | Extra item data — see [Item NBT](#item-nbt). |

A key is recognised by its material **plus** the `CrateName` the mod writes into its custom data.
A hand-crafted item with the same material and name is not a key.

### Prize fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes | Display name in the preview and during the roll. |
| `material` | string | yes | Item id used for the icon. A prize is *displayed* as this item; what the player actually receives is whatever `commands` give them. |
| `amount` | int | yes | Stack size of the icon. |
| `chance` | int | yes | Relative weight, not a percentage. The displayed `{chance}` is `chance ÷ sum of all chances`. |
| `commands` | array of string | yes | Run as console, positioned at the player, when the prize is won. |
| `broadcast` | string | no | Announced to the whole server. Omit or leave empty for a silent prize. |
| `messageToOpener` | string | no | Sent only to the player who opened the crate. |
| `lore` | array of string | no | Icon lore in the preview. Defaults to a single `Chance: x%` line. |
| `nbt` | string | no | Extra item data for the icon — see [Item NBT](#item-nbt). |

Because rewards are delivered by `commands`, a prize can be anything a command can do: an item, a
Pokémon, a rank, money, a permission.

### Item NBT

The `nbt` field takes an SNBT string, and **both** syntaxes are supported. Which one you wrote is
detected automatically, so configs written for 1.19/1.20 keep working untouched.

**Legacy (pre-1.20.5) tag compound** — any key that is not a `namespace:path` identifier marks the
string as legacy, and it is run through Minecraft's DataFixer:

```json
"nbt": "{species:\"cobblemon:bulbasaur\",aspects:[\"shiny\"]}"
```

```json
"nbt": "{display:{Name:'{\"text\":\"Legacy Name\"}'},Enchantments:[{id:\"minecraft:sharpness\",lvl:5}]}"
```

**Modern (1.20.5+) component changes** — every top-level key is a component id:

```json
"nbt": "{\"minecraft:enchantments\":{levels:{\"minecraft:sharpness\":5}}}"
```

```json
"nbt": "{\"minecraft:custom_model_data\":1234,\"minecraft:unbreakable\":{}}"
```

Malformed SNBT is logged as a warning and ignored — it never takes the server down.

### Placeholders

| Placeholder | Valid in | Expands to |
|---|---|---|
| `{prize_name}` | `broadcast`, `messageToOpener`, prize `lore` | The prize's `name`, with its own colours preserved |
| `{player_name}` | `broadcast`, `messageToOpener`, `commands` | The player who opened the crate |
| `{crate_name}` | `broadcast`, `messageToOpener` | The crate's display name |
| `{chance}` | prize `lore` | The prize's chance as a percentage, two decimals |

Anything else in `{braces}` is printed literally, and logged at load as an unknown placeholder. The
common mistakes are `{prize.name}` and `{crateName}` — neither is substituted.

## Colours and formatting

Two text engines, chosen by `textType` in `config.json`. Both apply to crate configs and
`messages.json` alike, and both switch off Minecraft's default italics on item names and lore.

**`LEGACY`** (default):

| Syntax | Example |
|---|---|
| Colour codes | `&6`, `&c`, `&f` |
| Formatting codes | `&l` bold, `&n` underline, `&o` italic, `&m` strikethrough, `&k` obfuscated, `&r` reset |
| Hex colour | `&#FFBF00` |
| Bare hex colour | `#FFBF00` — accepted as well, because many existing configs are written this way |

**`MINIMESSAGE`**: the full [MiniMessage](https://docs.advntr.dev/minimessage/format.html) syntax —
`<gold>`, `<bold>`, `<#FFBF00>`, `<gradient:#ff0000:#0000ff>`, `<rainbow>`, and so on.

Legacy codes keep working in `MINIMESSAGE` mode. `&` codes and bare hex colours outside of tags are
translated to their MiniMessage equivalents before parsing, so a server can flip `textType` on
without rewriting every crate file first. Text *inside* a MiniMessage tag is left exactly as
written, so `<gradient:#ff0000:#0000ff>` is not mangled. A string MiniMessage cannot parse falls
back to being read as legacy text rather than failing.

## Commands and permissions

Every `/padmin` command requires the LuckPerms node `pebbles.admin.crate`, or operator permission
level 2 when LuckPerms is not installed. The console and command blocks always have access.

| Command | Permission | What it does |
|---|---|---|
| `/padmin crate` | `pebbles.admin.crate` | Browse every configured crate; grab a transformer or a key from the GUI |
| `/padmin getcrate <crateName>` | `pebbles.admin.crate` | Gives you the paper that turns a block into that crate. Player only |
| `/padmin givekey <players> <amount> <crateName>` | `pebbles.admin.crate` | Gives keys, physical or virtual depending on how the crate is configured |
| `/padmin givekey physical <players> <amount> <crateName>` | `pebbles.admin.crate` | Forces a real key item. Only exists when virtual keys are enabled |
| `/padmin givekey virtual <players> <amount> <crateName>` | `pebbles.admin.crate` | Forces wallet balance. Only exists when virtual keys are enabled |
| `/padmin activecrates` | `pebbles.admin.crate` | Lists every placed crate; click one to open its [settings screen](#per-crate-style-overrides) |
| `/padmin keys <player>` | `pebbles.admin.crate` | Inspects a player's key wallet. Only exists when virtual keys are enabled |
| `/padmin convertkeys <players>` | `pebbles.admin.crate` | Turns physical keys in a player's inventory into wallet balance. Only exists when virtual keys are enabled |
| `/padmin reload` | `pebbles.admin.crate` | Re-reads every config file from disk |
| `/keys` | `pebbles.crate.keys` | Opens your own key wallet. Only exists when virtual keys are enabled |
| `/keys send` | `pebbles.crate.keys` | Opens the send-keys screen |
| `/keys send <player> <amount> <crateName>` | `pebbles.crate.keys` | Sends keys directly |

`pebbles.crate.keys` is **granted by default** — it exists only so a server can take `/keys` away
from a group by negating it.

Placing and breaking a registered crate both require `pebbles.admin.crate` too, so a survival player
cannot mine a crate away.

## Global configuration

`config/pebbles-crate/config.json`, written with defaults on first start. A file from an older
version is topped up with the newer sections automatically, keeping your existing values. Unknown
sound, particle and item ids are reported at start-up and fall back to the default rather than
breaking the crate.

```json
{
  "textType": "LEGACY",
  "crate": {
    "cooldownMillis": 8000,
    "defaultKeyMaterial": "minecraft:tripwire_hook"
  },
  "animation": {
    "steps": 10,
    "ticksPerStep": 6,
    "holdTicks": 100,
    "finalScale": 1.25,
    "maxLifetimeTicks": 600,
    "shuffleSound": {
      "id": "minecraft:block.note_block.banjo",
      "volume": 0.5,
      "pitch": 1.0
    },
    "rewardSounds": [
      { "id": "minecraft:entity.allay.death", "volume": 0.5, "pitch": 0.5 },
      { "id": "minecraft:block.note_block.bell", "volume": 0.5, "pitch": 1.0 }
    ],
    "rewardSoundRepeats": 5
  },
  "particles": {
    "radiusBlocks": 16.0,
    "idle": "minecraft:firework",
    "reward": "minecraft:sculk_soul",
    "rewardCount": 50
  },
  "gui": {
    "openButtonSlot": 49
  },
  "virtualKeys": {
    "enabled": false,
    "storage": "local",
    "localWriteDelayMillis": 2000,
    "mongo": {
      "uri": "mongodb://localhost:27017",
      "database": "pebbles-crates",
      "collection": "PlayerKeys",
      "serverSelectionTimeoutSeconds": 5
    }
  }
}
```

| Setting | Default | Meaning |
|---|---|---|
| `textType` | `LEGACY` | `LEGACY` or `MINIMESSAGE` — see [Colours and formatting](#colours-and-formatting) |
| `crate.cooldownMillis` | `8000` | How long a player waits between opening any two crates. `0` disables the cooldown |
| `crate.defaultKeyMaterial` | `minecraft:tripwire_hook` | Stands in wherever a crate's own `crateKey.material` does not resolve |
| `animation.steps` | `10` | How many prizes flick past before the real one lands |
| `animation.ticksPerStep` | `6` | Ticks between them (20 ticks = 1 second) |
| `animation.holdTicks` | `100` | How long the won prize stays up afterwards |
| `animation.finalScale` | `1.25` | Size of the won prize relative to the ones rolled past |
| `animation.maxLifetimeTicks` | `600` | Backstop: the floating display removes itself after this no matter what |
| `animation.shuffleSound` | banjo | Played on every roll step. Volume `0`–`10`, pitch `0.5`–`2.0` |
| `animation.rewardSounds` | allay + bell | Played together when the prize is awarded. An empty list means silence |
| `animation.rewardSoundRepeats` | `5` | How many times that set is layered |
| `particles.radiusBlocks` | `16.0` | How close a player must be to see a crate's idle particles |
| `particles.idle` | `minecraft:firework` | The particle spiralling above every placed crate |
| `particles.reward` | `minecraft:sculk_soul` | The burst when a prize is won |
| `particles.rewardCount` | `50` | How many of them. `0` disables the burst |
| `gui.openButtonSlot` | `49` | Where the "Open Crate" button sits on a virtual crate's preview screen. Bottom row only, `46`–`51` — slots `45`, `52` and `53` carry the page controls, and a value outside the range is pulled to the nearest usable slot |

`virtualKeys.enabled` and `virtualKeys.storage` are read **once at start-up** — commands cannot be
registered after the server is up, and swapping a live store would strand queued writes. Changing
either needs a full restart; `/padmin reload` will say so. Everything else applies immediately on
reload.

## Per-crate style overrides

Everything under `animation` and `particles.idle` in `config.json` is the server-wide default. A
crate can override it, and a single placed crate can override that in turn — so the Vote crate at
spawn can throw hearts and chime while every other crate keeps the house style.

### Resolution order

Each field is resolved **on its own**, from the narrowest layer that sets it:

```
this placed crate  →  the crate type  →  config.json
```

A placement that sets only the reward sound's pitch still inherits that sound's id and volume from
the crate type, and everything else from `config.json`. `null` (or simply leaving a field out) means
"inherit", at every level. A sound or particle id the game does not know is skipped rather than used:
the next layer down supplies the value, and the unknown id is reported at start-up.

### On a crate type

Add a `style` object to the crate's JSON file. Every field is optional.

```json
{
  "crateName": "Vote",
  "crateKey": { "material": "minecraft:tripwire_hook", "name": "Vote Key", "lore": [] },
  "style": {
    "particleStyle": "heart",
    "particleType": "minecraft:heart",
    "shuffleSound": { "id": "minecraft:block.note_block.bell", "volume": 0.6, "pitch": 1.4 },
    "rewardSound": { "id": "minecraft:entity.player.levelup", "volume": 0.8, "pitch": 1.0 },
    "animationSteps": 14,
    "ticksPerStep": 4,
    "finalScale": 2.0
  },
  "prize": []
}
```

| Field | Range | Meaning |
|---|---|---|
| `particleStyle` | `cross-spiral`, `spiral`, `sparkle`, `heart`, `none` | The idle pattern above the crate. `none` draws nothing |
| `particleType` | any particle id | Overrides the particle that style draws |
| `shuffleSound` | `{ id, volume, pitch }` | Played on every roll step. Volume `0`–`10`, pitch `0.5`–`2.0` |
| `rewardSound` | `{ id, volume, pitch }` | Replaces `animation.rewardSounds` for this crate. One sound, not a list |
| `animationSteps` | `0`–`200` | How many prizes flick past |
| `ticksPerStep` | `1`–`200` | Ticks between them |
| `finalScale` | `0.1`–`10.0` | Size of the won prize |

Each pattern has its own default particle, used when `particleType` is absent:

| `particleStyle` | Default particle | Behaviour |
|---|---|---|
| `cross-spiral` | `particles.idle` from `config.json` | Two strands winding up, every tick. The mod's original look |
| `spiral` | `minecraft:firework` | The same, wound tighter |
| `sparkle` | `minecraft:end_rod` | A scatter above the crate, every 2 seconds |
| `heart` | `minecraft:heart` | A scatter around the crate, every 1.5 seconds |
| `none` | — | No idle particles at all |

`animation.holdTicks`, `rewardSoundRepeats`, `particles.reward` and the particle radius stay global.
`config.json` can name several reward sounds that play together; a `rewardSound` override replaces
that chord with the single sound it names, merged field by field over the first of them.

### On one placed crate

The values in `config/pebbles-crate/crate_data.json` grow an object form. Both are read forever, and
a placement with no style of its own is still written as a bare crate name:

```json
{
  "minecraft:overworld:2199023222900": "Silver",
  "minecraft:the_nether:-137713829994433": {
    "name": "Vote",
    "style": { "particleStyle": "sparkle", "finalScale": 3.0 }
  }
}
```

The `style` object is exactly the one above. Keys keep the `<worldId>:<packedBlockPos>` format;
dimension-less keys from very old versions are still migrated to the overworld on load.

### From the game

`/padmin activecrates` lists every placed crate; clicking one opens its settings screen.

- **Info** (top) — crate name, world and position, whether it uses virtual or physical keys, and
  which file the crate was read from.
- **Teleport** — puts you on top of that crate and closes the screen.
- **Particles here** — the old blacklist toggle. It is a hard off-switch for this one block and wins
  over any style, including one that would otherwise draw particles.
- **Editing: this placement / crate type** — the scope switch, lime for the single block and orange
  for every crate of that type. Everything below is written to whichever is shown.
- **Particle style, sounds, animation** — one item each.

The interaction is the same everywhere: **left click** is next or `+`, **right click** is previous
or `−`, **shift + left click** clears the value back to inherit. Sound items are the exception — a
plain click *plays* the sound as the crate would, so shift + left and shift + right walk the preset
list instead. Every item's lore says which of these apply to it, what the value in force is, where
that value comes from, and what the layer being edited has set.

The sound presets are: `block.note_block.banjo`, `.bell`, `.harp`, `.pling`, `.bit`,
`block.amethyst_block.chime`, `block.ender_chest.open`, `block.beacon.activate`,
`entity.experience_orb.pickup`, `entity.player.levelup`, `entity.firework_rocket.twinkle`,
`item.totem.use`, `ui.button.click`, plus *inherit*. Any other sound id — vanilla or from another
mod — can be set by editing the JSON; the screen leaves it alone and shows it as the value in force.
The screen's scale steps stay within `0.5`–`4.0`; the config file accepts the wider range above.

Everything the screen writes goes straight to disk and survives `/padmin reload`. Editing a crate
type rewrites only the `style` member of its file, so hand-written fields, comments in values, NBT
strings and key order are left as they are.

> The web editor at [pebblescrate.sethi.tech](https://pebblescrate.sethi.tech/) does not know about
> `style` yet and will drop it from a config it round-trips. That costs the crate its overrides —
> it falls back to inheriting `config.json` — but never breaks the file. Set styles from the game or
> by hand until the editor catches up.

## Messages

Every line the mod says to a player lives in `config/pebbles-crate/messages.json`, written with
defaults on first start and topped up when a new version adds a line. Values are parsed with the
same engine as crate configs, so colour codes work.

- A key you delete falls back to its built-in default. It is never printed raw.
- A key set to `""` silences that message entirely.
- Placeholders are per message and shown in the default text: `{player_name}`, `{crate_name}`,
  `{amount}`, `{seconds}`, `{command}`, `{summary}`, `{position}`, `{world}`, `{page}`, `{pages}`,
  and so on.
- A few entries are lists of lines (the crate transformer's lore, the send-screen's click hints).
  Both a single string and a list are accepted for those.
- A key the mod does not recognise — a typo, or one left behind by an older version — is reported
  once at start-up and then dropped the next time the file is written. Keep your own notes elsewhere:
  the file only ever holds the keys the running version uses.

## Virtual keys

Off by default. When enabled, keys stop being items in an inventory and become a per-player balance
the server keeps: `/keys` shows the wallet, `/keys send` moves keys between players, and clicking a
crate opens its preview screen with an **Open Crate** button instead of needing an item in hand.

Turn it on in `config.json` and restart:

```json
"virtualKeys": { "enabled": true, "storage": "local" }
```

With the feature off, none of the `/keys` commands are registered, no storage is ever created, and
physical keys behave exactly as they always have.

### Which crates use virtual keys

`virtualKeys.enabled` is the master switch: while it is off, every crate uses physical keys no
matter what its own file says. While it is on, every crate uses virtual keys unless it opts out with
`"virtualKey": false` in its file. Omitting the field inherits the global setting, which is why
every crate file written before this feature existed is still valid.

### Storage

**`"storage": "local"`** — one JSON file, `config/pebbles-crate/playerdata/keys.json`. Held in
memory and written back on a debounce (`localWriteDelayMillis`), plus a flush on shutdown. Right for
a single server.

**`"storage": "mongodb"`** — a shared wallet, so a player's keys follow them across every server
pointed at the same database. The driver is bundled and relocated inside the mod jar; nothing extra
to install. All database access happens off the game thread, and a `uuid` index is created on first
connect.

If MongoDB cannot be reached, the server logs the failure loudly and carries on against local
storage rather than leaving every crate unopenable. Keys granted while the connection was being
established are carried over. Keys will not be shared across servers until the database is back.

The document shape is compatible with the older standalone virtual-crate mod, so an existing
collection can be pointed at directly:

```json
{ "uuid": "…", "name": "…", "keys": [ { "crate": "Vanilla Items Crate", "amount": 3 } ] }
```

Note that `crate` is the `crateName`, never the `screenName`.

### Migrating a live crate to virtual keys

Physical keys already in circulation stop working the moment a crate goes virtual — clicking the
crate with one tells the player as much. Convert them instead:

```
/padmin convertkeys <player>
```

This scans the player's inventory for keys belonging to crates that actually use virtual keys,
credits the balance, and removes the items. Keys for crates still on physical keys are left alone.
`/padmin convertkeys @a` handles everyone online.

### Current limitations

- Keys can only be given to **online** players. Offline delivery is not supported yet.
- A player's wallet is loaded on join and released on quit, so with MongoDB there is a brief moment
  after joining where spending is refused with "your keys are still loading".
- The key is taken when the roll starts, and the prize is handed out when it finishes about three
  seconds later. A player who disconnects, or a server that stops, inside that window loses the key
  without the prize — the same as every version before 2.0.

## Upgrading from an older version

Configs load unchanged. Nothing in `config/pebbles-crate/crates/` needs editing, including
pre-1.20 SNBT `nbt` strings, `&`/`&#RRGGBB`/bare-`#RRGGBB` colours, and the original
`crateName`/`crateKey`/`prize` schema.

Two files migrate themselves on first start:

- **`crate_data.json`** — placed crates used to be keyed by a bare block position, which meant a
  crate in the Nether and one at the same coordinates in the Overworld were the same entry. Keys are
  now `worldId:packedPos`; old entries are read and rewritten as `minecraft:overworld`. Values may
  now be an object as well as a crate name — see
  [Per-crate style overrides](#per-crate-style-overrides) — but a crate with no style of its own is
  still written the way it always was.
- **`blacklist.txt`** — particle-blacklist lines used to be `x,y,z` and are rewritten as
  `world_id,x,y,z`, again assuming the Overworld.

Back both files up first if your crates live outside the Overworld and you would rather place them
again than have them assumed into it.

Also new in this version: `config.json` gains sections it did not have before (your existing
`virtualKeys` block is preserved), and `messages.json` is created. The floating prize is now a
display entity rather than a real dropped item, which closes the hopper-duplication exploit — if you
built anything around hoppers under crates, it will stop collecting.

## Building from source

```
./gradlew build
```

Output lands in `build/libs/`. Requires JDK 21.

## Supporters

Thanks to these kind folks from the Cobblemon Server discord for commissioning this free mod:

**Fiddy**: Souls Network · **Cube**: PokeCubed · **Ghoul/Rain**: Poke Meadows ·
**Ojcastillo29**: Treasure Network · **Frosteffects**: Lunar Legends
