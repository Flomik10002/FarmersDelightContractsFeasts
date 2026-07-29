# Farmer's Delight: Contracts & Feasts

**Requires [Farmer's Delight](https://www.curseforge.com/minecraft/mc-mods/farmers-delight-refabricated) (NeoForge, 1.21.1).**

## About

Farmer's Delight gives you dozens of crops, tools and dishes — and then you settle on the same three recipes forever. Contracts & Feasts fixes that by giving every one of them a buyer.

Place a Contract Board, take an order from a customer, cook what they actually asked for, turn it in. Emeralds, XP, and occasionally something rarer. The board also shows up on its own, standing right in villages.

## Features

• **Contract Board** — a placeable, take-only order board with up to 18 concurrent offers, refreshed once per in-game day (Bountiful-style weighted rotation, not constant churn).<br>
• **Six customer types** — Village Bakery, Local Tavern, Farmers' Market, Village Kitchen, Traveling Merchants, and the late-game Harvest Feast, each pulling from its own themed catalog.<br>
• **Four rarity tiers** — Common through Special, rolled independently of which customer you're talking to, with a hidden reputation counter that quietly tilts the odds toward bigger contracts the more you complete.<br>
• **Recipe-aware pricing** — a dish's payout is computed from its real crafting tree (raw ingredients → cutting board → cooking pot → plate), not a hand-picked number, so effort is actually rewarded.<br>
• **Generates in every village** — a small board stand spawns as a genuine extra building next to the town center, its wood (and stone, in deserts) automatically matching the village's biome.<br>
• **Villagers visit on their own time** — idle villagers occasionally wander over to check the board, purely for atmosphere.<br>
• **Addon-friendly by design** — objectives are built from data tags and Farmer's Delight's own semantic tags (`meals`, `snacks`, `sweets`, `drinks`), so compatible food mods slot in without any extra configuration.

## Balance tooling

`./gradlew runBalance` runs a headless simulation across every customer and rarity tier and prints worth/reward statistics — useful if you're tuning your own datapack on top of this mod.

## Localization

• English (en_us) — Flomik<br>
• Russian (ru_ru) — Flomik<br>
• Chinese Simplified (zh_cn) — Flomik

## License

All Rights Reserved.
