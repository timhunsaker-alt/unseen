# Unseen Voice

A companion to [Unseen](../README.md). It remembers how you sound, and every so often,
from somewhere behind you, something says what you said. Or something you didn't.

Everything runs on **your own machines**. Nothing is sent to any outside service.

> **Status:** compiles, loads next to Simple Voice Chat 2.6.26 for Minecraft 26.3, and registers with
> the voice chat. The audio path (recording and playback) and the VoiceStudio calls have not yet been
> tried with real players or a real voice server. Expect to adjust.

## What it needs

- Unseen (the base mod) and Fabric API
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) **on the server and on every client** (2.6.26 or newer for 26.3)
- Optional, for phrases nobody said (like "hey, over here" or your name): a **VoiceStudio** server (below)

Without a voice server it still works: it replays short clips of what players actually said.

## Install

1. Put `unseen-voice-0.1.0.jar` in the server's `mods` folder next to `unseen-0.1.0.jar`.
2. Start the server once. It writes `config/unseen-voice.json`.
3. Everyone who wants to be part of it types `/unseenvoice optin` in game. **Nothing is recorded for anyone who hasn't.**

## How it works

- Opted-in players' microphones are listened to on the server. Short speech clips (0.5 to 4 seconds)
  are kept in `<world>/data/unseen_voice/<player id>/clips/`, at most 60 clips or 25 MB per player. Oldest go first.
- The `voice` scare (dread 3+, player alone) plays one of those clips, or a generated phrase, from a spot
  behind the player that they aren't looking at. Only that player hears it. It's played a little slower and lower than it was said.
- With a voice server set, once about 6 seconds of speech are saved, it makes a cloned voice for that player
  and generates a bank of phrases ahead of time, so nothing slow happens during play.

## Player commands

| Command | What it does |
|---|---|
| `/unseenvoice optin` | Start. Short clips of your voice are kept on this server. |
| `/unseenvoice optout` | Stop, and delete every clip, phrase and setting of yours from this server. |
| `/unseenvoice status` | Clips saved, whether your voice is made, how many phrases are ready. |
| `/unseenvoice nickname <text>` | How your name should be said, for example `Timmy` for `tim_h2009`. |
| `/unseenvoice build` | (op) Make your phrases now instead of waiting. |

Testing a scare right away: `/unseen dread 5`, then `/unseen trigger voice`. It needs you to have opted in,
have clips or phrases saved, and have voice chat connected.

## Setting up the voice server (VoiceStudio, no GPU needed)

[VoiceStudio](https://github.com/debpalash/VoiceStudio) is an open source (AGPL-3.0) voice cloning server with an
OpenAI-style speech API. These steps follow its Docker documentation.

Needs: an x86-64 Linux machine (its image has no ARM version), Docker, about 6 GB of disk for the image and models,
and about 5 GB of RAM while a model is loaded. Without a GPU a short phrase takes on the order of 20 seconds to make,
which is why phrases are made in advance.

```bash
# 1. A secret key. Keep it; the mod needs it too.
export OMNIVOICE_API_KEY="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"

# 2. Start it. First run downloads about 2.4 GB of model files; watch with: docker logs -f omnivoice
docker run -d --name omnivoice \
  -p 3900:3900 \
  -e OMNIVOICE_API_KEY="$OMNIVOICE_API_KEY" \
  -v omnivoice-data:/app/omnivoice_data \
  ghcr.io/debpalash/voicestudio:stable
```

`-p 3900:3900` makes it reachable from other machines on your home network. Do not expose it to the internet.
If the Minecraft server runs on the same machine, use `-p 127.0.0.1:3900:3900` instead.

Check it's up: `curl http://<server address>:3900/health`

Then edit `config/unseen-voice.json` on the Minecraft server:

```json
{
  "voiceStudioUrl": "http://192.168.1.50:3900",
  "apiKey": "<the key from step 1>"
}
```

Restart the Minecraft server. Players who have opted in and have enough clips get their voice made automatically
(it checks every five minutes), or an op can run `/unseenvoice build`.

## Settings (config/unseen-voice.json)

| Setting | What it does |
|---|---|
| `voiceStudioUrl`, `apiKey`, `model`, `language` | The voice server. Leave the URL empty to only replay clips. |
| `phrases` | What it can say. `{name}` becomes the nickname, or the player's name. |
| `autoBuildBank` | Make each player's phrases automatically. |
| `minReferenceSeconds` / `maxReferenceSeconds` | How much recorded speech is used to clone a voice. |
| `maxClipsPerPlayer` / `maxBytesPerPlayer` | Storage limits per player. |
| `speechRmsThreshold` | How loud counts as speech. Raise it if clips are mostly background noise. |
| `playbackVolume` / `playbackSpeed` | How loud, and how slow and low (below 1). |
| `minDistance` / `maxDistance` | How far behind the player the voice comes from. |
| `phraseWeight` | How much more often generated phrases are picked than recorded clips. |

## Privacy

- Recording only happens for players who typed `/unseenvoice optin`, and `/unseenvoice optout` deletes everything of theirs from the server.
- Clips stay in the world folder. The only thing sent anywhere is a stitched reference recording (about 6 to 14 seconds) and phrase text,
  to the voice server **you** run.
- A cloned voice made on the voice server stays there until you delete its profile (named `unseen-` plus the first 8 characters of the player's id) in VoiceStudio.
- Tell anyone who joins the world. Only clone voices with the speaker's permission.
