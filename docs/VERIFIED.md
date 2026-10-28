# Verified against Minecraft 26.2

Source of truth for names/descriptors used by mixins and render code. Checked 2026-10-27 against:

- `minecraft-client.jar` / `minecraft-common.jar` 26.2 (unobfuscated) via `javap`
- Loom 1.17.21 `genSources` (Vineflower) output
- `fabric-api-0.161.0+26.2.jar` nested module jars via `javap`

Legend: **OK** = handoff skeleton is correct as written. **CHANGED** = skeleton must be updated.

## Toolchain

| Item | Result |
|---|---|
| Loom `1.17-SNAPSHOT` | resolves to 1.17.21; was the official 26.2 template pairing with Gradle 9.5.1 + Loader 0.19.3 (fabric-example-mod `26.2` branch, Jun–Aug 2026) |
| Fabric API `0.161.0+26.2` | latest 26.2 build; `depends.fabricloader >=0.18.4`, so Loader 0.19.3 is fine |
| Java 25 toolchain | no resolver plugin is configured; Gradle auto-detects JDKs in `~/.jdks` (Temurin 25.0.5+8 installed there). The Gradle daemon itself runs fine on Java 24; `runClient` launches the game on the 25 toolchain |
| `build.gradle` | spec version plus a `processResources` block (needed to expand `${version}` in `fabric.mod.json`) |

## §9 items

### 1. `ChatComponent#addMessage` — CHANGED

The 1.21.x 3-arg `addMessage` no longer exists. 26.2 has three public entry points that all call one **private** 4-arg method:

```java
public void addClientSystemMessage(Component)                               // -> SYSTEM_CLIENT
public void addServerSystemMessage(Component)                               // -> SYSTEM_SERVER (Hypixel system chat)
public void addPlayerMessage(Component, MessageSignature, GuiMessageTag)    // -> PLAYER
private void addMessage(Component, @Nullable MessageSignature, GuiMessageSource, @Nullable GuiMessageTag)
```

Mixin target (covers player, server-system and client-system chat in one place):

```
addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V
```

Package moves: `GuiMessageTag`, `GuiMessage`, and the new enum `GuiMessageSource { PLAYER, SYSTEM_SERVER, SYSTEM_CLIENT }` live in `net.minecraft.client.multiplayer.chat`. Chat is reached via `minecraft.gui.hud.getChat()`.

### 2. Where a hovered `HoverEvent.ShowText` becomes a tooltip — RESOLVED

Flow in `GuiGraphicsExtractor`:

1. Focused chat (`ChatComponent.DrawingFocusedGraphicsAccess`) draws with `graphics.textRenderer(HoveredTextEffects.TOOLTIP_AND_CURSOR, this)`.
2. `RenderingTextCollector.accept(Style)` stores the hovered style in `hoveredTextStyle` when `allowTooltip && style.getHoverEvent() != null`.
3. `extractDeferredElements(mx, my, a)` calls `componentHoverEffect(minecraft.font, hoveredTextStyle, mx, my)`, then `nextStratum()` and runs `deferredTooltip`.
4. `componentHoverEffect` switches on the hover event; `ShowText` → `setTooltipForNextFrame(font, font.split(text, …), mx, my)`.

Injection point (private, signature matches the skeleton's handler params):

```
GuiGraphicsExtractor#componentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V
@Inject(at = @At("HEAD"), cancellable = true)
```

Chosen approach: inside the injection, don't blit directly. `componentHoverEffect` runs *before* the `nextStratum()` that lifts tooltips above screen content. Instead call
`setTooltipForNextFrame(font, List.of(), Optional.of(new ImageTooltipData(media)), mx, my)` and map `ImageTooltipData` → a `ClientTooltipComponent` via Fabric's `ClientTooltipComponentCallback` (item 8). That gets vanilla positioning/screen clamping (`DefaultTooltipPositioner`), the tooltip background, and the top stratum for free. (This is the handoff's listed fallback, promoted to primary.) Loading / error states use `setTooltipForNextFrame(font, Component, mx, my)`.

Hover only exists while the chat screen is open; unfocused HUD chat uses `HoveredTextEffects.NONE`.

### 3. `GuiGraphicsExtractor` draw calls — OK

```java
public void blit(RenderPipeline, Identifier, int x, int y, float u, float v,
                 int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight)
// UVs: u/textureWidth .. (u+srcWidth)/textureWidth, same for v. Also a 13-arg variant with trailing int color.
public void fill(int x0, int y0, int x1, int y1, int col)            // RenderPipelines.GUI
public void setTooltipForNextFrame(Font, Component, int, int)
public void setTooltipForNextFrame(Font, List<Component>, Optional<TooltipComponent>, int, int)
    // image inserted at index 0 if the list is empty, else index 1
```

`RenderPipelines.GUI_TEXTURED` exists. The skeleton's sprite-sheet call `blit(GUI_TEXTURED, id, x, y, 0f, frame*frameH, w, h, frameW, frameH, sheetW, sheetH)` is correct. `ClientTooltipComponent` draw hook is `extractImage(Font, int x, int y, int w, int h, GuiGraphicsExtractor)`, plus `getWidth(Font)` / `getHeight(Font)`.

### 4. Clipboard — OK

`KeyboardHandler#getClipboard(): String`, `KeyboardHandler#setClipboard(String)`; `Minecraft.keyboardHandler` is a public final field.

### 5. `ChatScreen` / `setScreen` — OK

- `ChatScreen(String initial, boolean isDraft)` (and a 3-arg variant with `closeOnSubmit`). Use `new ChatScreen(token, false)`.
- `Minecraft.gui` is public final; `Gui#setScreen(@Nullable Screen)`.
- `/imglink chat` must defer opening the screen: `ChatScreen` runs the command synchronously inside its key handler and then calls `gui.setScreen(null)`. **CHANGED:** use `mc.schedule(...)`, not the skeleton's `mc.execute(...)`. `execute` only queues when `reentrantCount != 0`; vanilla GLFW key events arrive via `minecraft.execute(() -> keyPress(...))` so it happens to work there, but Fabric's client gametest input calls `keyPress` directly and the new screen is opened then immediately closed (caught by `ImgLinkClientGameTest`). `BlockableEventLoop#schedule(R)` always appends to the task queue.

### 6. Textures — OK, with notes

- `DynamicTexture(Supplier<String> label, NativeImage image)` **uploads in the constructor** via `RenderSystem.getDevice()`, keeps the `NativeImage` until `close()`, and always samples with `getRepeat(FilterMode.NEAREST)` (blocky when scaled).
- **Used instead (0.2.0):** a GPU texture per frame with no `TextureManager` entry. `GpuDevice#createTexture(String label, USAGE_COPY_DST | USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, w, h, 1, 1)` + `createCommandEncoder().writeToTexture(texture, nativeImage)` + `createTextureView(texture)`, the same calls `DynamicTexture` makes. `writeToTexture` copies the pixels out before returning (GL: `glTexSubImage2D`; Vulkan: `transientMemory.uploadStaging` into a staging buffer), so the `NativeImage` is closed straight after. It throws inside a render pass; tooltip extraction isn't one (vanilla map textures are prepared there too).
- `GuiGraphicsExtractor#blit(GpuTextureView, GpuSampler, x0, y0, x1, y1, u0, u1, v0, v1)` is public and draws with `RenderPipelines.GUI_TEXTURED`; the `Identifier` overloads just look up the texture's view and sampler. `RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)` gives a smooth sampler.
- Previews are decoded at the GUI box × `Window#getGuiScale()` (physical pixels) and drawn at the box size in GUI units, so one texel ≈ one screen pixel. One texture per frame means no sprite-sheet height cap (was 8192 px, which forced ≤ 64 small frames).
- `NativeImage(int w, int h, boolean zero)` → RGBA format. `setPixel(x, y, argb)` takes **ARGB** (converts to ABGR internally), so `BufferedImage.getRGB` values go in unchanged. `close()` is idempotent.

### 7. Shift key — OK

`Minecraft#hasShiftDown()` is an instance method (checks GLFW keys 340/344): `Minecraft.getInstance().hasShiftDown()`.

### 8. Fabric tooltip callback — CHANGED (name)

It is **`net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback`** (fabric-rendering-v1), not `TooltipComponentCallback`:

```java
Event<ClientTooltipComponentCallback> EVENT;
ClientTooltipComponent getClientComponent(TooltipComponent data);   // return null if not ours
```

Without it, vanilla `ClientTooltipComponent.create(TooltipComponent)` throws `IllegalArgumentException("Unknown TooltipComponent")` for custom data.

### 9. Fabric client commands — OK

`net.fabricmc.fabric.api.client.command.v2`:

- `ClientCommands.literal(String)`, `ClientCommands.argument(String, ArgumentType<T>)`
- `ClientCommandRegistrationCallback.register(CommandDispatcher<FabricClientCommandSource>, CommandBuildContext)`
- `FabricClientCommandSource#attended(): boolean` exists (usable as `.requires(FabricClientCommandSource::attended)`), plus `sendFeedback`, `sendError`, `getClient`.

### Other API used by skeletons — OK

`Util.getMillis()`, `HoverEvent.ShowText(Component value)` (record), `Style#withColor(int)`, `Style#withHoverEvent(HoverEvent)`, `FormattedText#visit(StyledContentConsumer<T>, Style)`.

### Exercised in a running client (`./gradlew runClientGameTest`)

The client game tests in `src/gametest` drive a real 26.2 client and confirm the items above in practice: both mixins apply; tokens become labels for server system messages and player chat; hovering a label (real cursor input) goes through `componentHoverEffect` and shows the image via the custom tooltip component; Giphy and Tenor GIFs download, decode and animate; errors, the Discord expiry check, budget eviction, and every `/imgprev` setting behave as intended. Two things only showed up there:

- Chat hover uses `GuiGraphicsExtractor.mouseX/mouseY`, captured when the extractor is built each frame from `MouseHandler#getScaledXPos`, and hit-tests with `ActiveTextCollector.findElementUnderCursor`.
- `🖼` (U+1F5BC) renders from Minecraft's Unifont fallback as a framed-picture glyph, not the missing-glyph box.

AWT: the client's `Main` sets `java.awt.headless=true` unconditionally, so `javax.imageio` never touches a window system; `FrameDecoder` still composites with plain `int[]` (no `Graphics2D`).

## WebP (TwelveMonkeys 3.15.3, checked 2026-10-27)

- Runtime dependencies are exactly `imageio-webp` → `imageio-core`, `imageio-metadata` → `common-lang`, `common-io`, `common-image`. Loom's `include` isn't transitive, so all six are listed; each is nested under `META-INF/jars/` with a generated `fabric.mod.json`. Mod jar: ~600 KB (72 KB before).
- The reader is registered by hand (`IIORegistry#registerServiceProvider(new WebPImageReaderSpi())` in `FrameDecoder`'s static init); ImageIO's own plugin scan isn't reliable for jars nested in a mod.
- For animated files `getWidth(i)`/`getHeight(i)`/`read(i)` give each frame's **sub-rectangle**, not the composited canvas. The reader parses ANMF offset, duration and blend/dispose flags but doesn't expose them ("TODO" in `WebPImageReader`), and ignores ANIM. `WebPAnimation` reads those RIFF chunks itself; `FrameDecoder.animate` composites (source-over or replace, dispose to transparent) with the same frame-dropping as GIF.
- Checked with lossless fixtures built in the unit tests (`TestWebP`), a real lossy+alpha animated sample (gstatic.com/webp/animated/1.webp: 100 frames × 100 ms on 400×300, all frames clean), and an in-game decode in `ChatPreviewClientGameTest`.
- Not exercised: launching the packaged jar itself (Loom 1.17 has no production-run task), so nested-jar loading relies on Fabric Loader's standard jar-in-jar handling.
- License: BSD-3-Clause; the jars carry no license file, so its text ships as `META-INF/licenses/TwelveMonkeys-LICENSE.txt`.

## Klipy API (docs.klipy.com, checked 2026-10-27)

- Lookup: `GET https://api.klipy.com/api/v1/{app_key}/gifs/items?slugs={slug}` → `data.data[]`, each with `slug` and `file.{hd,md,sm,xs}.{gif,webp,jpg,mp4,webm}.{url,width,height,size}`. ImgPrev takes `sm.gif` (≈220 px), falling back to `md`, `xs`, `hd`.
- Media hosts (network-requirements page): `static.klipy.com`, `static1.klipy.com`, `static2.klipy.com`. The API host is `api.klipy.com`.
- Testing-mode keys: 100 requests/hour; ImgPrev caches slug → URL.
- Share links: `klipy.com/{gifs|stickers|clips|memes}/{slug}`; only `/gifs/` is supported (stickers would need the sticker endpoint, clips are video). Slugs are lowercase words and digits joined by `-`.
- GIF pages (`klipy.com/gifs/{slug}`) answer non-browser requests with **403 `cf-mitigated: challenge`** (Cloudflare), so a page link can only be resolved through the API.
- CDN addresses are `static{,1,2}.klipy.com/ii/{32 hex}/{2 hex}/{2 hex}/{name}.{ext}` (survey of ~4,000 on the homepage; GIF/WebP names are 8 characters). `static2` serves the same bytes as `static`; `static1` didn't resolve.
- **Design change from the handoff:** slug tokens (`k`) made every *viewer* need their own API key, so on a fresh install Klipy tokens just showed as raw text (seen on Hypixel). New tokens carry the CDN address instead (site `y`, no key for anyone); the *sender's* key turns a page link into that address in `/imglink` and auto-convert. `k` stays decode-only for tokens made by 0.1.0.

## Sending (Fabric API 0.161.0, `fabric-message-api-v1`)

`ClientPacketListener#sendChat` / `#sendCommand` fire `ALLOW_CHAT` → (if allowed) `MODIFY_CHAT` → `CHAT`, and the same for commands, all at HEAD before signing. Auto-convert replaces links in `MODIFY_*`; a message needing a Klipy lookup is cancelled in `ALLOW_*` and re-sent once converted (the re-send passes straight through, its links being tokens by then). The server refuses chat and commands over 256 characters.

## Settings screen (optional dependencies, checked 2026-10-27)

- Mod Menu **20.0.3** (`com.terraformersmc:modmenu`, maven.terraformersmc.com; depends on Minecraft `>=26.2 <26.3-alpha.3`) and Cloth Config **26.2.155** (`me.shedaniel.cloth:cloth-config-fabric`, maven.shedaniel.me; mod id `cloth-config`) are the 26.2 builds. Neither is bundled: `compileOnly` + `localRuntime` (dev runs and in-game tests), `suggests` in fabric.mod.json, and the `modmenu` entrypoint only touches Cloth classes when `cloth-config` is loaded.

## Live URL checks (from a GB connection, UA `ImgPrev/0.1 (Minecraft mod)`)

| Site | Result |
|---|---|
| Giphy | Previews use `https://i.giphy.com/media/{id}/giphy.gif`, the original upload (checked 2026-10-27: 492×298, 478×288 and 400×275, 63 KB–1 MB), not `200.gif` (200 px tall). Unknown id on `giphy.gif` (and `source.gif`, `giphy-downsized*.gif`) → **200 with a 480×270 "THIS CONTENT IS NOT AVAILABLE" GIF**, shown as is; only the small renditions (`200.gif`, `200w.gif`) answer 404. `giphy.webp` 404s for some real ids; `giphy-hd.gif` and `480w.gif` → 403. `i.giphy.com/{id}.gif` serves the same bytes as `/media/{id}/giphy.gif`. |
| Tenor | `media.tenor.com/{key}/{anything}.gif` serves identical bytes regardless of filename, so `tenor.gif` works. The key suffix picks the rendition: `…AAAAM` is the 220 px medium GIF, `…AAAAC` the full-size one (498×496 for `1S3s5iesWX0`); previews swap M for C. tenor.com's "copy image address" gives `media1.tenor.com/m/{key}/{name}.gif`, and `c.tenor.com/{key}/…` also works; both serve the same bytes as `media.tenor.com/{key}/…`. Unknown key → 404. |
| Imgur | **Not verifiable from the UK.** Imgur geo-blocks the UK: `i.imgur.com/{anything}` returns **200 `image/png`** (34,641-byte "not available" placeholder) for every hash and extension; `imgur.com` returns 403 "Content not available in your region". UK recipients will see that placeholder instead of the image. Extension swapping still needs a check from a non-UK connection. |
| Gyazo | Unknown id on `i.gyazo.com/{hex}.{ext}` → **503 `text/plain`** (not 404). Gyazo stores captures as PNG, switching to JPG when > 1 MB, so "page link without ext → png" may be wrong for large captures. Still needs a real capture URL to confirm. |
| Discord | Unknown emoji → 404 JSON; unsigned attachment URL → 404, as expected. |
| Postimages | Checked 2026-10-28, reachable from GB. `i.postimg.cc/{8-char id}/{name}` serves the file by id alone: any filename and any extension (`x.png`, `i.gif`, `….jpg` for a PNG) give identical bytes with the real content type. Unknown id → **404 `image/png`** placeholder. Share pages `postimg.cc/{id}` answer the ImgPrev UA with 200 HTML (no bot check); the page id differs from the CDN id, and `<meta property="og:image">` names the full-size CDN address. `www.postimg.cc` and a trailing slash 301 to the bare page. An unknown page id answers 200 with no `og:image`. |
| ImgBB | Checked 2026-10-28, reachable from GB. `i.ibb.co/{8-char id}/{name}` behaves like Postimages (filename and extension ignored; a bare `/{id}/` or `/{id}/i` with no extension 404s). Unknown or deleted id → **404 `image/png`**. Share pages `ibb.co/{id}` answer 200 HTML; the page id is the *thumbnail's* CDN id, and `og:image` names the full-size one (780×521 vs a 4 KB thumb for `S7csSRwd`). `www.ibb.co` and a trailing slash 301. |
| Catbox | Checked 2026-10-28, reachable from GB. `files.catbox.moe/{6 lowercase/digits}.{ext}` is case-sensitive in both id and extension, and a different extension (or none) → 404 HTML, so tokens keep the exact extension. `litter.catbox.moe` (temporary uploads) isn't supported. |
| Reddit | Not added. `i.redd.it` is reachable (unknown id → 404 `image/png`), but reddit.com pages give no image links to a non-browser client, so no real upload could be checked. |

## Releases (checked 2026-10-27)

- `.github/workflows/build.yml`: after the build job passes on a push to `main`, the release job reads `mod_version` from gradle.properties and, if there's no `v{version}` release yet, runs `gh release create v{version} build/libs/imgprev-{version}.jar --title "ImgPrev {version}" --target $GITHUB_SHA --generate-notes` with the jar the build job uploaded. Same scheme as the author's Bazaar Assist repo: bumping the version is what publishes a release; other pushes leave releases alone.
- Action versions current on 2026-10-27: `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions@v6`, `actions/upload-artifact@v7`, `actions/download-artifact@v8` (artifacts v4+ share one backend, so v7 uploads download with v8).
