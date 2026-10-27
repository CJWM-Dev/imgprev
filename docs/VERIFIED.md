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

- `DynamicTexture(Supplier<String> label, NativeImage image)` **uploads in the constructor** via `RenderSystem.getDevice()`. Construct on the render thread only.
- `DynamicTexture` keeps the `NativeImage` (off-heap) until `close()`. Budget accounting of `sheetW*sheetH*4` reflects that CPU-side copy (GPU copy is extra, in VRAM).
- `NativeImage(int w, int h, boolean zero)` → RGBA format. `setPixel(x, y, argb)` takes **ARGB** (converts to ABGR internally), so `BufferedImage.getRGB` values go in unchanged.
- `TextureManager#register(Identifier, AbstractTexture)` closes any previous texture at that id. `TextureManager#release(Identifier)` removes and closes.
- `Identifier.fromNamespaceAndPath(String, String)` exists; path must be lowercase `[a-z0-9/._-]` (hash the key, as planned).

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

## Klipy API (docs.klipy.com, checked 2026-10-27)

- Lookup: `GET https://api.klipy.com/api/v1/{app_key}/gifs/items?slugs={slug}` → `data.data[]`, each with `slug` and `file.{hd,md,sm,xs}.{gif,webp,jpg,mp4,webm}.{url,width,height,size}`. ImgPrev takes `sm.gif` (≈220 px), falling back to `md`, `xs`, `hd`.
- Media hosts (network-requirements page): `static.klipy.com`, `static1.klipy.com`, `static2.klipy.com`. The API host is `api.klipy.com`.
- Testing-mode keys: 100 requests/hour; ImgPrev caches slug → URL.
- Share links: `klipy.com/{gifs|stickers|clips|memes}/{slug}`; only `/gifs/` is supported (stickers would need the sticker endpoint, clips are video). Slugs are lowercase words and digits joined by `-`.

## Live URL checks (from a GB connection, UA `ImgPrev/0.1 (Minecraft mod)`)

| Site | Result |
|---|---|
| Giphy | `https://i.giphy.com/media/{id}/200.gif` → 200 `image/gif`, no redirect. Unknown id → **404 with an `image/gif` body** (SafeHttp's status check must run before the content-type check, which it does). |
| Tenor | `media.tenor.com/{key}/{anything}.gif` serves identical bytes regardless of filename, so `tenor.gif` works. The key suffix picks the rendition (`…AAAAM` tinygif ~30 KB, `…AAAAC` gif ~120 KB); keeping the full key preserves the sender's rendition. Unknown key → 404. |
| Imgur | **Not verifiable from the UK.** Imgur geo-blocks the UK: `i.imgur.com/{anything}` returns **200 `image/png`** (34,641-byte "not available" placeholder) for every hash and extension; `imgur.com` returns 403 "Content not available in your region". UK recipients will see that placeholder instead of the image. Extension swapping still needs a check from a non-UK connection. |
| Gyazo | Unknown id on `i.gyazo.com/{hex}.{ext}` → **503 `text/plain`** (not 404). Gyazo stores captures as PNG, switching to JPG when > 1 MB, so "page link without ext → png" may be wrong for large captures. Still needs a real capture URL to confirm. |
| Discord | Unknown emoji → 404 JSON; unsigned attachment URL → 404, as expected. |
