# Pebble's Lootcrate
Pebble's Lootcrate mod for Fabric Minecraft version 1.19.2

<p><a title="Fabric Language Kotlin" href="https://minecraft.curseforge.com/projects/fabric-language-kotlin" target="_blank" rel="noopener noreferrer"><img style="display: block; margin-left: auto; margin-right: auto;" src="https://i.imgur.com/c1DH9VL.png" alt="" width="171" height="50" /></a></p>

Make sure you have Fabric Language Kotlin installed!

If you would like to migrate from AdvancedCrates to Pebble's Crate, this will help speed things up a little: <br>
https://pebblescrate.sethi.tech/

## Permissions
For all admin commands, if you have LuckPerm installed, the following is required:<br>
`pebbles.admin.crate`

## Available Commands
`/padmin crate` Displays all available crate in the config and lets you grab a crate transformer/crate key <br>
`/padmin getcrate <name>` Get a crate transformer <br>
`/padmin givekey <player> <amount> <cratename>` Gives cratekey to a specific player <br>

## How to use?
Come and try out the crate in a server running Cobblemon: **dynastymc.org**

After installing the mod, you may navigate to your `/config/pebbles-crate/crates`then create a file, for example `example.json`
<br>
You may use the follow example as reference for the structure: <br>

```
{
  "crateName": "Vanilla Items Crate",
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
	  "nbt": "{species:\"cobblemon:bulbasaur\",aspects:[\"shiny\"]}",
      "commands": [
        "give {player_name} minecraft:diamond 1"
      ],
      "broadcast": "&6{player_name} &fhas received {prize_name} &ffrom {crate_name}",
      "messageToOpener": "&6[Pebble's Crates] &f&lYou got {prize_name} &f&lfrom Vanilla Items Crate",
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
      "broadcast": "&6{player_name} &fhas received {prize_name} &ffrom {crate_name}",
      "messageToOpener": "&6[Pebble's Crates] &f&lYou got {prize_name} &f&lfrom Vanilla Items Crate",
      "lore": [
        "Chance of getting the drop: {chance}%"
      ],
      "chance": 5
    },
    {
      "name": "&rGolden Apple",
      "material": "minecraft:golden_apple",
      "amount": 1,
      "commands": [
        "give {player_name} minecraft:golden_apple 1"
      ],
      "broadcast": "&6{player_name} &fhas received {prize_name} &ffrom {crate_name}",
      "messageToOpener": "&6[Pebble's Crates] &f&lYou got {prize_name} &f&lfrom Vanilla Items Crate",
      "lore": [
        "Chance of getting the drop: {chance}%"
      ],
      "chance": 55
    }
  ]
}
```
Note that the mod supports legacy formatting (e.g. &#63BC5D, &4, &f, &r) as demonstrated in the example.

## Todo
- Global customisable options (UI elements such as arrow for next/previous)
- Comment in classes
- Further optimise the mod
- Admin UI for editing the loot in-game
- Finish writing todos
