# Unseen

A horror mod for Minecraft Java 26.3 (Fabric). Something else lives in your world.
You never see it. It never explains itself. You just find things.

## What it does

The "entity" has no body, no model, no AI. It's a **director**: it watches each
player, slowly gets bolder, and waits for moments when nobody is looking.

| Scare (`/unseen trigger <id>`) | When | What you find |
|---|---|---|
| `footprints` | Dread 1+, in snow | A trail walking up to your house and stopping. None leading away. Or a trail that ends 3 blocks behind where you're standing. Prints slowly fill back in with snow. |
| `circle` | Dread 3+, you're indoors at home, in snow | Prints that walk a slow circle around your house on the side you weren't looking. The last one turns to face the building. |
| `windowprints` | Dread 3+, away from home, in snow | Prints that walk up to one of your windows and stop. |
| `torch` | Dread 1+, away from home | One torch in your house is gone. It's in your chest. |
| `torchtrail` | Dread 2+ | Torches you left behind you go out one at a time, the farthest first, each one closer. |
| `door` | Dread 1+, away from home | The door you closed is open. Iron doors too. |
| `knock` | Dread 2+, you're indoors at home | Three slow knocks on your front door. Nobody's there. |
| `creak` | Dread 1+, outside near home | A chest in your house opens, then closes. |
| `footsteps` | Dread 2+ | Steps behind you, in time with yours. They stop when you stop. Sometimes there's one more. |
| `echo` | Dread 2+ | Your own footsteps, half a second late. They stop when you stop. |
| `photo` | Dread 2+, when you sleep | A picture of you asleep, left in a chest near your bed. |
| `journal` | Dread 3+, away from home | An unsigned book in a chest. Small true things about you, like notes. |
| `map` | Dread 4+, away from home | A map of your base with a red X on your bed. You never made it. |
| `sign` | Dread 3+, away from home | A sign near your house with something you said in chat written on it. |
| `deathflower` | Dread 1+, after you die | One flower where you died, once you've walked away. |
| `doorstep` | Dread 2+, away from home | One flower set on your doorstep. |
| `candle` | Dread 3+, away from home | A lit candle on the floor by your bed. |
| `tamper` | Dread 2+, away from home | An item frame turned a notch, or a campfire flipped lit/unlit. |
| `sorted` | Dread 2+, away from home | One of your chests, sorted. Nothing missing. |
| `crops` | Dread 2+, away from home | A ripe row of crops harvested and replanted; the harvest is put away in a chest. |
| `light` | Dread 3+, at night, outdoors | A lantern far off in the dark. Gone when you look away and walk toward it. |
| `witness` | Dread 2+, animals nearby | Every animal stops and stares at the same empty spot behind you. A pet may growl or hiss. |
| `bell` | Dread 3+, at night, outdoors | One bell, far away. Roughly once per world. |
| `bedturn` (not random; hooks into waking) | Dread 3+ | You wake up facing the wall, not the room. Use `/unseen trigger bedturn` then sleep. |
| `heartbeat` | Dread 3+ | Your own heartbeat, soft and low, only you can hear it. It speeds up, then stops. |

### After the dragon (dread 6)

When the Ender Dragon dies, everyone who was in the fight enters **the quiet**: a full in-game day with nothing at all,
even at maximum dread. Then dread 6 begins: events come more often (a 2 minute wait between them instead of 4, and
shorter cooldowns on each), and these appear:

| Scare | What you find |
|---|---|
| `logbook` | An unsigned book listing what you did, in detail: when, where, what you were doing and for how long (underground, swimming, hurt, asleep, in the Nether, in the End). Written from what it noted about you. |
| `wander` | At night, lit candles appear in your house and drift from room to room, **even while you watch**. As you come near they vanish, and your heartbeat starts. |
| `return` | Once, while you are away: a candle lit by your bed, a door standing open, a flower on the doorstep. |
| `wingbeats` | Far away at night, slow wingbeats pass overhead and fade. No roar. Nothing is there. |
| (photo) | The sleeping photo happens every time, sharper, with a tall shadow in the frame that stands at the edge and gets closer in each new photo. |
| (journal) | The note-taking book turns up more often. |

Testing it without killing the dragon: `/unseen dragon slain` (starts the quiet), `/unseen dragon skipquiet` (ends it), `/unseen dragon clear`,
or just `/unseen dread 6`.

**Dread** (0 to 5) goes up by one for every in-game day you play. At dread 0 nothing
happens at all, so new worlds feel normal. Higher dread means more frequent events.

**The rules that keep it unseen:**
- Nothing happens anywhere the player is looking (a wide 150° cone, walls ignored). The one exception is after the dragon, when wandering candles move in plain view.
- It only acts when you're alone (nobody else within 32 blocks).
- It learns where "home" is by watching where you sleep.
- It never tells you anything. No chat messages, no advancements.
- It never screams. No loud, shrill or sudden sounds: only quiet things, like footsteps, a knock, a far bell, wingbeats, and a soft heartbeat.

## Build and run

You need **JDK 25** (Temurin is fine) and an IDE (IntelliJ IDEA 2025.3+ works best).

```bash
./gradlew runClient     # launches Minecraft with the mod for testing
./gradlew build         # makes the mod jar in build/libs/
```

## Install (macOS and Linux, same jar)

The jar is plain Java, so one file works everywhere: `dist/unseen-0.1.0.jar`.

1. Install **Java 25** (Temurin is fine).
2. Install **Fabric Loader** for Minecraft 26.3 with the installer from https://fabricmc.net/use/installer
   (Loader 0.20.1 or newer). Run it, pick 26.3, and let it create the launcher profile.
3. Download **Fabric API for 26.3** (Modrinth: https://modrinth.com/mod/fabric-api).
4. Put `unseen-0.1.0.jar` and the Fabric API jar in your `mods` folder:
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. Launch the Fabric profile.

Everyone who joins a world needs the mod (the footprint is a new block).

**Optional voice companion:** `dist/unseen-voice-0.1.0.jar` also needs Simple Voice Chat. See [unseen-voice/README.md](unseen-voice/README.md).

To rebuild after changes: `./gradlew build`, then copy `build/libs/unseen-0.1.0.jar` and `unseen-voice/build/libs/unseen-voice-0.1.0.jar` (not the `-sources` ones) into `dist/`.

## Testing without waiting days

Turn cheats on, then:

```
/unseen dread 5                 skip ahead to max dread (6 is the after-the-dragon level)
/unseen trigger footprints      stand in snow, try it
/unseen trigger footsteps       then walk around
/unseen trigger photo           then go to sleep (needs a chest near the bed)
/unseen trigger torch           needs a home (sleep once) and you 24+ blocks away
/unseen trigger door            same, 20+ blocks away
/unseen trigger <any id above>  tab-complete lists them all; most need a home and distance
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

Events that need cleanup later can override `tickPlayer`; use `Director.get().later(ticks, task)`
for anything that should happen a few seconds from now.

Ideas for next ones: your bed's blanket color changed; the music disc in your
jukebox swapped; your name scratched into a sign deep in a cave you've never
been to; a second photo, from the doorway; a note-block tone from somewhere with no note block.

## Notes

- Multiplayer works. Each player has their own haunting. Everyone who joins needs
  the mod installed too (the footprint is a new block).
- The photo makes the screen flicker for about a tenth of a second while it's taken.
  That's the only clue. (It's a feature.)
- Written against the Fabric API for 26.3. If your game is 26.1.2, change the
  versions in `gradle.properties`; a few names may need fixing when it compiles.
