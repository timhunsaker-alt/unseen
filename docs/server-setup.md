# Running Unseen (with voices) on a server

A step by step for a family server: one machine runs Minecraft, and your friends or family connect over the internet.
Linux is assumed for the server; the steps are the same on macOS apart from the paths.

## What goes where

| Thing | Server | Each player |
|---|---|---|
| Java 25 | yes | yes (the Minecraft launcher brings its own) |
| Fabric Loader for 26.3 | yes | yes |
| Fabric API | yes | yes |
| `unseen-0.1.0.jar` | yes | yes (it adds a block) |
| Simple Voice Chat 2.6.26 | yes | yes |
| `unseen-voice-0.1.0.jar` | yes | **no** (server only) |
| VoiceStudio (voice cloning) | reachable from the server | no |

## 1. The server

```bash
mkdir -p ~/unseen-server/mods && cd ~/unseen-server

# Fabric server launcher for Minecraft 26.3 (Loader 0.20.1)
curl -fLo fabric-server-launch.jar https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar

# Mods
curl -fLo mods/fabric-api-0.165.0+26.3.jar "https://cdn.modrinth.com/data/P7dR8mSH/versions/EQKgpeKu/fabric-api-0.165.0%2B26.3.jar"
curl -fLo mods/voicechat-fabric-2.6.26+26.3.jar "https://cdn.modrinth.com/data/9eGKb6K1/versions/cZm965sW/voicechat-fabric-2.6.26%2B26.3.jar"
# and copy unseen-0.1.0.jar and unseen-voice-0.1.0.jar from this repo's dist/ folder into mods/

java -Xmx4G -jar fabric-server-launch.jar nogui     # first run: creates eula.txt and stops
```

Open `eula.txt` and set `eula=true` **if you accept** [Mojang's EULA](https://aka.ms/MinecraftEULA), then start it again.

Recommended in `server.properties`:

```
online-mode=true        # real accounts only
white-list=true         # then: whitelist add <name> in the server console, for each family member
```

Start script (`start.sh`):

```bash
#!/bin/sh
cd "$(dirname "$0")"
exec java -Xmx4G -jar fabric-server-launch.jar nogui
```

## 2. The voice server (optional, for phrases nobody said)

Follow "Setting up the voice server" in [unseen-voice/README.md](../unseen-voice/README.md), then put its address and key in
`config/unseen-voice.json` on the Minecraft server (the file appears after the first start):

```json
{
  "voiceStudioUrl": "http://localhost:3900",
  "apiKey": "<your key>"
}
```

If VoiceStudio runs on the same machine, `localhost` is best: nothing about it then has to be reachable from the internet.
Leave the address empty to only replay clips of what players actually said.

## 3. Open the ports

| Port | For | Open it on the router and firewall |
|---|---|---|
| 25565 TCP | Minecraft | yes |
| 24454 **UDP** | Simple Voice Chat | yes (voice chat does not work without it) |

Do **not** open VoiceStudio's port (3900) to the internet unless it is behind a key and HTTPS.

If voice chat can't connect, set `voice_host` in `config/voicechat/voicechat-server.properties` to the server's public address and port.

## 4. Each player

1. Install Fabric Loader for 26.3 with the [Fabric installer](https://fabricmc.net/use/installer) and make a profile.
2. Put these in the `mods` folder: Fabric API, `unseen-0.1.0.jar`, and Simple Voice Chat (the same files as the server's, except `unseen-voice`).
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
3. Join the server. Voice chat asks for the microphone on first use; set push to talk or voice activation in its settings (default key `V`).

## 5. Trying the voice

1. Everyone who wants to take part types `/unseenvoice optin`. Nothing is recorded for anyone who hasn't.
2. Talk for a minute or so. Short clips are saved when a player pauses. `/unseenvoice status` shows how many.
3. An operator can speed things along (`op <name>` in the server console):
   ```
   /unseen dread 5              raise the haunting to maximum
   /unseenvoice build           make that player's phrases now (needs the voice server; takes a while)
   /unseenvoice phrase <player> <sentence>     add a sentence for that player
   /unseen trigger voice        play one now, from behind the player
   ```
4. Only the targeted player hears it. If nothing plays: they must have opted in, have voice chat connected, have saved clips or finished phrases, and not be looking toward the spot.

To stop at any time: `/unseenvoice optout` deletes everything of that player's from the server.
