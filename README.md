# ImgPrev

> **⚠️ Warning:** **Imgur is blocked in the UK**, so Imgur images won't load for UK players. Postimages, ImgBB and Catbox work in the UK and are supported. **Klipy page links** (`klipy.com/gifs/…`) can't be converted. Right-click the GIF and copy its image address instead.

Hover previews for image links in Minecraft chat, for Fabric 26.2.

ImgPrev turns image links into short tokens you can send without the link text. Anyone with ImgPrev sees a small label such as `[▶ GIF]` or `[⧈ Image]`, and hovering over it shows the image or animated GIF. Players without the mod see the token as plain text, so nothing breaks for them.

Tokens look like `<~GXJtM2nNFCzT3etvzOBK~>`. They contain no links, so chat filters and link-blocking won't touch them. Since 0.3.0, tokens are shorter, and players need 0.3.0 or newer to preview them. Tokens from older versions still work.

## Features

- **Previews** for Giphy, Imgur, Tenor, Discord emoji and attachments, Gyazo, Klipy, Postimages, ImgBB, and Catbox. Still images, animated GIFs, and animated WebP are all supported.
- **Share page links work too.** Postimages (`postimg.cc/…`) and ImgBB (`ibb.co/…`) page links are looked up when you convert them, so you don't need to find the direct image address.
- **Full-resolution drawing.** Previews are decoded at your screen's resolution and scaled smoothly.
- **Auto-convert links.** Optionally turns links you send into tokens automatically. This also works in commands such as party chat (`/pc`) or `/msg`.
- **Settings screen** with `/imgprev settings` or through Mod Menu, plus `/imgprev` chat commands for quick changes.
- **Label colours.** Each kind of label (GIF, Image, Emoji, Discord) has its own colour. Pick a preset or type any hex colour.
- **Client-side only.** Works on any server. The server only ever sees the token text.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0+26.2 in your `mods` folder.
3. Download `imgprev-<version>.jar` from the [releases page](https://github.com/CJWM-Dev/imgprev/releases) and put it in your `mods` folder.
4. Optional, for the settings screen: add [Cloth Config](https://modrinth.com/mod/cloth-config) 26.2.155. Add [Mod Menu](https://modrinth.com/mod/modmenu) 20.0.3 to open it from the Mods list as well.

Requires Java 25.

## Using it

**Sending an image:** Use `/imglink <link>` to copy a token to your clipboard, or `/imglink chat <link>` to put the token straight into the chat box. If auto-convert is on, you can just paste a link and press Enter.

**Settings:** Type `/imgprev settings` to open the settings screen (needs Cloth Config), or open it from Mod Menu. Type `/imgprev` for the current status. Useful commands:

| Command | What it does |
|---|---|
| `/imgprev settings` | Open the settings screen |
| `/imgprev on` / `off` | Turn previews on or off |
| `/imgprev shift on` | Only show previews while holding Shift |
| `/imgprev autoconvert on` | Convert links when you send them |
| `/imgprev site <name> on` / `off` | Turn a site's previews on or off |
| `/imgprev colour <gif\|image\|emoji\|discord> <colour>` | Set a label colour: a preset such as `gold` or `orange`, or a hex colour like `#FF8800` |
| `/imgprev colour reset` | Put every label colour back to its default |
| `/imgprev cache clear` | Free the image cache |

**Klipy page links** (`klipy.com/gifs/…`) can't be converted, because Klipy only gives out the GIF's address through an API that needs a key. Right-click the GIF on the page and copy its image address (`static.klipy.com/…`) instead. That works for everyone, with no setup.

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
