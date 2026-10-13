# Pollinations for Paper

Adds Pollinations AI to a Paper server:

- **NPC villagers** with their own personas. Right-click one, or use `/polli say`, to talk. Each NPC remembers recent messages with each player.
- **Images on maps.** `/polli image <prompt>` generates a picture and gives you a filled map showing it. Images survive restarts.

Every AI call is billed to the Pollinations API key in your config, so you pay with your own Pollen.

## Install

1. Get a Paper 1.21 server (Java 21).
2. Copy `pollinations-minecraft-1.0.0.jar` into `plugins/`.
3. Start the server once. It creates `plugins/Pollinations/config.yml`.
4. Create an API key at <https://enter.pollinations.ai/keys> and put it in `config.yml`:

   ```yaml
   api-key: "pk_your_key_here"
   ```

5. Run `/polli reload`, or restart the server.

Without a key, the plugin still loads, and every AI command replies with a message saying the owner needs to set one up.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/polli npc create <name> <persona>` | admin | Spawns a villager named `<name>` with that persona. Players spawn it one block from where they stand. The console spawns it at world spawn. |
| `/polli npc remove <name>` | admin | Removes the NPC and its villager. |
| `/polli npc list` | everyone | Lists NPC names. |
| `/polli say <name> <message>` | everyone | Sends a message to an NPC. The reply is shown only to you. |
| `/polli image <prompt>` | everyone | Generates an image and gives you a map showing it. |
| `/polli reload` | admin | Re-reads `config.yml` and checks the model list again. |

Right-click an NPC to start a chat. Your next chat lines go to that NPC, not to the server. Type `bye` to end it, or wait 3 minutes.

NPC names use letters, digits and `_`, up to 16 characters. Personas are up to 400 characters.

## Permissions

| Node | Default | Covers |
| --- | --- | --- |
| `pollinations.chat` | everyone | `say`, right-click chat, `npc list` |
| `pollinations.image` | everyone | `image` |
| `pollinations.admin` | op | `npc create`, `npc remove`, `reload` |

## Config

`plugins/Pollinations/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `api-key` | empty | Your key from <https://enter.pollinations.ai/keys>. Keep it private. |
| `text-model` | `amazon/nova-micro-v1` | Model for NPC chat. See <https://gen.pollinations.ai/text/models>. |
| `image-model` | `black-forest-labs/flux.1-schnell` | Model for `/polli image`. See <https://gen.pollinations.ai/image/models>. |
| `max-tokens` | `120` | Upper bound on tokens per NPC reply. |
| `cooldown-seconds` | `5` | Minimum gap between AI requests from one player. The console is exempt. |
| `max-requests-per-hour` | `60` | Requests per player per hour. Chat and images are counted separately. |
| `memory-messages` | `12` | Recent messages each NPC keeps per player. |

On startup, and on `/polli reload`, the plugin reads the public model lists and logs a warning if a configured model is missing, paid-only, or reporting down. These checks use no key and cost nothing.

## Files

- `plugins/Pollinations/config.yml`: settings and the API key.
- `plugins/Pollinations/npcs.json`: NPC personas and per-player memory. Chat lines are stored here in plain text.
- `plugins/Pollinations/maps/<map-id>.png`: the image for each generated map. The plugin reloads these on startup.

## Costs

Pollen is billed per call. `amazon/nova-micro-v1` and `black-forest-labs/flux.1-schnell` were the cheapest healthy, non-paid-only models in the live lists when this was built.

- Chat: two test replies used 103 and 162 tokens in total. At the listed nova-micro price that is about $0.000006 and $0.000008 per turn.
- Images: the listed price is 0.002 Pollen (about $0.002) per image.

Prices and availability change, so check the model lists before you rely on these numbers. `max-tokens`, `cooldown-seconds`, and `max-requests-per-hour` limit how much one player can spend.

## Support

- **Build target:** Paper 1.21.11, Java 21. The plugin compiles against the Paper 1.21.11 API.
- **Unit tested:** chat request and reply handling, the model-list checks, the rate limiter, image conversion to map colours, text cleanup, and error messages.
- **Run on a real Paper 1.21.11 server (console only):** the plugin enabled, `/polli npc create` spawned a villager, `/polli npc list` and `/polli say` worked, and the plugin's own HTTP client got status 200 from `gen.pollinations.ai` with `amazon/nova-micro-v1`. An NPC replied in character and recalled "My name is Tom" on the next message; the memory is saved in `plugins/Pollinations/npcs.json`. The chat models, the image route and the model lists were also called live from test scripts.
- **Not yet tested:** a real Minecraft client. Right-click chat, `/polli image` and maps shown in-game (the image-to-map conversion is unit tested, and the image route was called live, but the map was not rendered on a client), and the per-player permission nodes are not verified. Try them on a Paper 1.21.11 server before you rely on them.
- **Spigot:** not supported. The plugin uses Paper APIs (`AsyncChatEvent`, the region and entity schedulers).
- **Folia:** not declared and not tested. Do not run it on Folia.

## Privacy

Prompts and NPC chat go to `gen.pollinations.ai`. Keep `npcs.json` private, because it contains player chat.

## Building

```
mvn package
```

The jar is `target/pollinations-minecraft-1.0.0.jar`. Run unit tests with `mvn test`.

## License

MIT. See [LICENSE](LICENSE).
