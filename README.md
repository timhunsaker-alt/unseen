# Unseen

A horror mod for Minecraft Java 26.3 (Fabric). Something else lives in your world.
You never see it. It never explains itself. You just find things.

## What it does

The "entity" has no body, no model, no AI. It's a **director**: it watches each
player, slowly gets bolder, and waits for moments when nobody is looking.

| Scare | When | What you find |
|---|---|---|
| `footprints` | Dread 1+, in snow | A trail walking up to your house and stopping. None leading away. Or a trail that ends 3 blocks behind where you're standing. Prints slowly fill back in with snow. |
| `torch` | Dread 1+, you're away from home | One torch in your house is gone. It's in your chest. |
| `door` | Dread 1+, you're away from home | The door you closed is open. Iron doors too. |
| `footsteps` | Dread 2+ | Steps behind you, in time with yours. They stop when you stop. Sometimes there's one more. |
| `photo` | Dread 2+, when you sleep | A picture of you asleep, taken from across the room, left in a chest near your bed. |

**Dread** (0 to 5) goes up by one for every in-game day you play. At dread 0 nothing
happens at all, so new worlds feel normal. Higher dread means more frequent events.

**The rules that keep it unseen:**
- Nothing happens anywhere the player is looking (a wide 150° cone, walls ignored).
- It only acts when you're alone (nobody else within 32 blocks).
- It learns where "home" is by watching where you sleep.
- It never tells you anything. No chat messages, no advancements.

## Build and run

You need **JDK 25** (Temurin is fine) and an IDE (IntelliJ IDEA 2025.3+ works best).

```bash
./gradlew runClient     # launches Minecraft with the mod for testing
./gradlew build         # makes the mod jar in build/libs/
```

To play it normally: install Fabric Loader for 26.3, put **Fabric API** and
`build/libs/unseen-0.1.0.jar` in your `mods` folder.

## Testing without waiting days

Turn cheats on, then:

```
/unseen dread 5                 skip ahead to max dread
/unseen trigger footprints      stand in snow, try it
/unseen trigger footsteps       then walk around
/unseen trigger photo           then go to sleep (needs a chest near the bed)
/unseen trigger torch           needs a home (sleep once) and you 24+ blocks away
/unseen trigger door            same, 20+ blocks away
/unseen status                  what it knows about you
/unseen dread -1                back to normal escalation
```

The memory for each world is in `saves/<world>/data/unseen_watcher.json`.
Delete it to reset the haunting.

## Where things live

```
src/main/java/dev/unseen/
  director/Director.java    the entity: decides when and what
  director/Tuning.java      ALL the numbers (how often, how scary)  <- start here
  director/Senses.java      "can they see this spot?", snow, chests
  events/                   one file per scare
  block/FootprintBlock.java the footprint in the snow
src/client/java/dev/unseen/client/
  PhotoCamera.java          takes the sleeping photo
  Darkroom.java             turns it into a grainy black-and-white map
tools/make_assets.py        draws the footprint shape (edit BOOT to change it)
```

## Adding a new scare

1. Make a class in `events/` that implements `HauntEvent`.
2. In `run()`, check conditions, do the thing, return `true` if it happened.
3. Always check `Senses.canSee(player, pos)` before touching anything.
4. Add it to the `events` list in `Director`.

Ideas for next ones: a sign near your house with the last thing you typed in
chat; your bed's blanket color changed; a single flower where you last died;
the music disc in your jukebox swapped; an item frame that's rotated every
morning; your name scratched into a sign deep in a cave you've never been to.

## Notes

- Multiplayer works. Each player has their own haunting. Everyone who joins needs
  the mod installed too (the footprint is a new block).
- The photo makes the screen flicker for about a tenth of a second while it's taken.
  That's the only clue. (It's a feature.)
- Written against the Fabric API for 26.3. If your game is 26.1.2, change the
  versions in `gradle.properties`; a few names may need fixing when it compiles.
