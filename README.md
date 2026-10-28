# ImgPrev

> **⚠️ Warning:** **Imgur is blocked in the UK**, so Imgur images won't load for UK players. Postimages, ImgBB and Catbox work in the UK and are supported. **Klipy page links need a Klipy API key** (`/imgprev klipy key <key>`). Without one, use the GIF's image address from the Klipy page instead.

Hover previews for image links in Minecraft chat, for Fabric 26.2.

ImgPrev turns image links into short tokens you can send without the link text. Anyone with ImgPrev sees a small `[🖼 GIF]` label, and hovering over it shows the image or animated GIF. Players without the mod see the token as plain text, so nothing breaks for them.

Tokens look like `<~1gXJtM2nNFCzT3etvzOB~>`. They contain no links, so chat filters and link-blocking won't touch them.

## Features

- **Previews** for Giphy, Imgur, Tenor, Discord emoji and attachments, Gyazo, Klipy, Postimages, ImgBB, and Catbox. Still images, animated GIFs, and animated WebP are all supported.
- **Share page links work too.** Postimages (`postimg.cc/…`) and ImgBB (`ibb.co/…`) page links are looked up when you convert them, so you don't need to find the direct image address.
- **Full-resolution drawing.** Previews are decoded at your screen's resolution and scaled smoothly.
- **Auto-convert links.** Optionally turns links you send into tokens automatically. This also works in commands such as party chat (`/pc`) or `/msg`.
- **Settings screen** through Mod Menu, plus a `/imgprev` chat command for quick changes.
- **Client-side only.** Works on any server. The server only ever sees the token text.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0+26.2 in your `mods` folder.
3. Download `imgprev-<version>.jar` from the [releases page](https://github.com/CJWM-Dev/imgprev/releases) and put it in your `mods` folder.
4. Optional, for the settings screen: add [Mod Menu](https://modrinth.com/mod/modmenu) 20.0.3 and [Cloth Config](https://modrinth.com/mod/cloth-config) 26.2.155.

Requires Java 25.

## Using it

**Sending an image:** Use `/imglink <link>` to copy a token to your clipboard, or `/imglink chat <link>` to put the token straight into the chat box. If auto-convert is on, you can just paste a link and press Enter.

**Settings:** Open Mod Menu, select ImgPrev, and click the gear icon. Or type `/imgprev` for the current status. Useful commands:

| Command | What it does |
|---|---|
| `/imgprev on` / `off` | Turn previews on or off |
| `/imgprev shift on` | Only show previews while holding Shift |
| `/imgprev autoconvert on` | Convert links when you send them |
| `/imgprev site <name> on` / `off` | Turn a site's previews on or off |
| `/imgprev klipy key <key>` | Set your Klipy API key |
| `/imgprev cache clear` | Free the image cache |

**Klipy page links** (`klipy.com/gifs/…`) need a Klipy API key to convert, because the GIF address has to be looked up. Set one with `/imgprev klipy key <key>`, or copy the GIF's image address from the Klipy page instead, which needs no key.

## Limits

These are adjustable in the settings screen:

- **Largest download:** 32 MB by default, up to 128 MB.
- **Image memory:** 256 MB by default. Older previews are dropped when this fills up.
- **Preview size:** up to 256 GUI pixels by default. Previews are never bigger than half the screen's width or 60% of its height.

## Building from source

```
./gradlew build
```

The jar is written to `build/libs/`. Unit tests run as part of the build. The in-game tests need a display and the live sites, so run them separately with `./gradlew runClientGameTest`.

## Dependencies

| Dependency | Required | Notes |
|---|---|---|
| Minecraft 26.2 | yes | |
| Fabric Loader 0.19.3+ | yes | |
| Fabric API 0.161.0+26.2 | yes | |
| Java 25 | yes | |
| Mod Menu 20.0.3 | no | Opens the settings screen from the Mods list |
| Cloth Config 26.2.155 | no | Needed for the settings screen |
| TwelveMonkeys ImageIO 3.15.3 | bundled | WebP support, included in the jar (BSD-3, see `META-INF/licenses`) |

## Contact

Author: **ElongateArms**. Get in touch on Discord: **ElongateArms**.

Bug reports and feature requests are welcome on the [issue tracker](https://github.com/CJWM-Dev/imgprev/issues).

## License

All rights reserved. The bundled TwelveMonkeys library is under its own BSD-3 license.
