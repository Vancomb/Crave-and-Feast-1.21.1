# Crave & Feast — NeoForge 1.21.1 port

Unofficial NeoForge 1.21.1 port of [Crave & Feast](https://github.com/skps2010/crave_and_feast)
by skps2010 (MIT), ported and extended by **Vancomb** from Fabric 1.21.10 to NeoForge 1.21.1.

Same core gameplay, same `config/crave_and_feast.json` format, same lang files and tooltip icons —
plus new in-game configuration UI (see [What I added](#what-i-added)).

## What I added

On top of the straight port, this version adds:

- **In-game Settings screen** — one place to reach everything the mod offers.
- **HUD editor** — drag a HUD to move it, drag its corner to resize, click the eye to hide/show;
  separate layouts for the on-screen HUD and the inventory HUD, with a reset-to-default button.
- **Craving Pool editor** — a searchable list of every food, so you can block or allow individual
  items in the craving pool, and add items by id (e.g. `minecraft:cookie`).
- Config **commands** for managing the above.

Everything above is my work; the original craving mechanic and config format are skps2010's.

## Build

Requires JDK 21.

```
gradlew build          (Windows)
./gradlew build        (macOS/Linux)
```

The jar lands in `build/libs/crave_and_feast-neoforge-1.21.1-1.0.0.jar`.

Test in-game from the project: `gradlew runClient` (or `runServer`).

No local Java setup? Push this folder to a GitHub repo — the included Actions workflow builds it
and attaches the jar as a downloadable artifact on the run.

## What changed from the Fabric version

| Fabric | NeoForge |
|---|---|
| `FoodComponentMixin` (@Redirect in `FoodComponent#onConsume`) | `PlayerMixin` (@WrapOperation on `FoodData#eat` inside `Player#eat`) |
| `PlayerManagerMixin` (join sync) | `PlayerEvent.PlayerLoggedInEvent` |
| `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| Fabric networking + `PacketCodec` | `RegisterPayloadHandlersEvent` + `StreamCodec`, registered as **optional** |
| `PersistentState` + Codec | `SavedData` (same file names in `world/data/`) |
| `HudElementRegistry` before CHAT | `RegisterGuiLayersEvent` below `VanillaGuiLayers.CHAT` |
| `ItemTooltipCallback` | `ItemTooltipEvent` |

Small fixes made along the way:
- The bite that hits the per-round craving limit now still gets the craving bonus (originally the craving was rerolled before the bonus check).
- Food ids are always full registry ids (`minecraft:apple`); on 1.21.1 `Item#toString()` drops the namespace.
- Packets are optional and only sent to clients that have the mod, so vanilla clients can join a server running it (they get the hunger effects, just no HUD/tooltips). Fake players (machines) are ignored.
- Modded foods that provide food via NeoForge's `getFoodProperties` are included in the craving pool.
- Penalty tooltips show the reduced shank count instead of the base value.

## License & credits

Licensed under the [MIT License](LICENSE), the same license as the original.

- Original **Crave & Feast** © 2025 skps2010 — <https://github.com/skps2010/crave_and_feast>
- NeoForge 1.21.1 port and added features © 2026 Vancomb

This is an **unofficial** port and is not affiliated with or endorsed by skps2010. The original
copyright notice and MIT license text are retained in `LICENSE`, as the license requires.

## CurseForge description

> **Crave & Feast (NeoForge)** adds a dynamic craving system that rewards eating a variety of
> foods. Each player always has one food they crave — eating it grants double nourishment, while
> eating the same food over and over gives diminishing returns.
>
> This NeoForge 1.21.1 build also adds in-game configuration: a settings screen, a draggable and
> resizable HUD editor, and a searchable craving-pool editor for including or excluding individual
> foods.
>
> Unofficial NeoForge port by Vancomb of [Crave & Feast](https://github.com/skps2010/crave_and_feast)
> by skps2010, used under the MIT License.
