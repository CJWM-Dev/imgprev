package io.github.cjwmdev.imgprev.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.image.MediaException;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uploads go to a local server here; nothing is sent anywhere else. */
class UploaderTest {
    @TempDir Path dir;
    HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    static byte[] png(int w, int h, boolean noise) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, noise ? r.nextInt() | 0xFF000000 : 0xFF336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Answers every upload with {@code status} and {@code reply}, remembering the last request's body and content type. */
    URI serve(int status, String reply, AtomicReference<byte[]> body, AtomicReference<String> type) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api.php", ex -> {
            body.set(ex.getRequestBody().readAllBytes());
            type.set(ex.getRequestHeaders().getFirst("Content-Type"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api.php");
    }

    @Test void temporaryUploadsSendTheLifetimeAndBecomeLitterboxTokens() throws Exception {
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicReference<String> type = new AtomicReference<>();
        URI endpoint = serve(200, "https://litter.catbox.moe/toh6ua.png\n", body, type);
        byte[] png = png(8, 8, false);
        String reply = Uploader.post(endpoint, UploadMode.TEMPORARY, "24h", Uploader.prepare(png, 1 << 20, 64));
        String token = Uploader.token(UploadMode.TEMPORARY, reply);
        assertEquals(TokenCodec.encode("https://litter.catbox.moe/toh6ua.png").orElseThrow(), token);
        assertEquals("litterbox", TokenCodec.decode(token).orElseThrow().codec().name());

        String sent = new String(body.get(), StandardCharsets.ISO_8859_1);
        String boundary = type.get().substring(type.get().indexOf("boundary=") + 9);
        assertTrue(type.get().startsWith("multipart/form-data"), type.get());
        assertTrue(sent.contains("name=\"reqtype\"\r\n\r\nfileupload\r\n"), sent);
        assertTrue(sent.contains("name=\"time\"\r\n\r\n24h\r\n"), sent);
        assertTrue(sent.contains("name=\"fileToUpload\"; filename=\"image.png\""), "neutral file name");
        assertTrue(sent.endsWith("\r\n--" + boundary + "--\r\n"));
        int start = sent.indexOf("\r\n\r\n", sent.indexOf("fileToUpload")) + 4;
        byte[] file = java.util.Arrays.copyOfRange(body.get(), start, start + png.length);
        assertArrayEquals(png, file, "the file's bytes, unchanged");
    }

    @Test void permanentUploadsSendNoLifetime() throws Exception {
        AtomicReference<byte[]> body = new AtomicReference<>();
        URI endpoint = serve(200, "https://files.catbox.moe/abc123.png", body, new AtomicReference<>());
        String reply = Uploader.post(endpoint, UploadMode.PERMANENT, "24h", Uploader.prepare(png(4, 4, false), 1 << 20, 64));
        assertEquals("catbox", TokenCodec.decode(Uploader.token(UploadMode.PERMANENT, reply)).orElseThrow().codec().name());
        assertFalse(new String(body.get(), StandardCharsets.ISO_8859_1).contains("name=\"time\""));
    }

    @Test void refusalsAndOddAnswersBecomeMessages() throws Exception {
        URI endpoint = serve(412, "<b>File too large</b>", new AtomicReference<>(), new AtomicReference<>());
        Uploader.Prepared p = Uploader.prepare(png(4, 4, false), 1 << 20, 64);
        MediaException refused = assertThrows(MediaException.class, () -> Uploader.post(endpoint, UploadMode.TEMPORARY, "1h", p));
        assertEquals("Litterbox refused the upload (HTTP 412): File too large", refused.getMessage());

        assertEquals("Catbox didn't send back a link: Something went wrong",
            assertThrows(MediaException.class, () -> Uploader.token(UploadMode.PERMANENT, "Something went wrong")).getMessage());
        assertThrows(MediaException.class, () -> Uploader.token(UploadMode.TEMPORARY, "https://files.catbox.moe/abc123.png"), "wrong host for the mode");
        assertThrows(MediaException.class, () -> Uploader.token(UploadMode.PERMANENT, "https://files.catbox.moe/abc123.exe"), "not an image link");
        assertThrows(MediaException.class, () -> Uploader.token(UploadMode.PERMANENT, ""));
    }

    @Test void onlyImagesAreSentAndBigStillsAreShrunk() throws Exception {
        assertThrows(MediaException.class, () -> Uploader.prepare("not an image".getBytes(StandardCharsets.UTF_8), 1 << 20, 64));
        byte[] small = png(8, 8, false);
        assertEquals("png", Uploader.prepare(small, 1 << 20, 64).ext());
        assertArrayEquals(small, Uploader.prepare(small, 1 << 20, 64).bytes(), "small files are sent as they are");

        byte[] big = png(300, 200, true);   // noise doesn't compress
        Uploader.Prepared shrunk = Uploader.prepare(big, big.length - 1, 64);
        assertEquals("jpg", shrunk.ext());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(shrunk.bytes()));
        assertEquals(64, out.getWidth());
        assertEquals(43, out.getHeight());

        byte[] gif = "GIF89a....".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("gif", Uploader.prepare(gif, 1 << 20, 64).ext());
        assertThrows(MediaException.class, () -> Uploader.prepare(gif, 4, 64), "animations aren't shrunk");
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("webp", Uploader.prepare(webp, 1 << 20, 64).ext());
    }

    @Test void onlyImageFilesDirectlyInTheFolderCanBeFound() throws IOException {
        Path shots = Files.createDirectories(dir.resolve("screenshots"));
        Files.write(shots.resolve("old.png"), png(2, 2, false));
        Files.write(shots.resolve("new.jpg"), png(2, 2, false));
        Files.writeString(shots.resolve("notes.txt"), "x");
        Files.createDirectories(shots.resolve("sub"));
        Files.write(shots.resolve("sub/inner.png"), png(2, 2, false));
        Files.write(dir.resolve("outside.png"), png(2, 2, false));
        Files.setLastModifiedTime(shots.resolve("old.png"), FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(shots.resolve("new.jpg"), FileTime.fromMillis(2_000_000));

        assertEquals(List.of("new.jpg", "old.png"), Uploader.images(shots), "newest first, images only");
        assertTrue(Uploader.find(shots, "old.png").isPresent());
        for (String bad : new String[] {"notes.txt", "sub/inner.png", "sub\\inner.png", "../outside.png", "..", "missing.png", "",
                dir.resolve("outside.png").toString(), "C:outside.png"}) {
            assertTrue(Uploader.find(shots, bad).isEmpty(), bad);
        }
        assertEquals(List.of(), Uploader.images(dir.resolve("none")), "no folder yet");
    }

    @Test void errorAnswersAreShortenedToOneLine() {
        assertEquals("a b", Uploader.shorten("  a\n\n<br>b "));
        assertEquals(80, Uploader.shorten("x".repeat(500)).length());
    }

    @Test void screenshotMessagesGetAShareButton() {
        ImgPrevConfig cfg = new ImgPrevConfig();
        Component msg = Component.translatable("screenshot.success", Component.literal("2026-11-01_12.00.00.png"));
        Component shared = ScreenshotShare.apply(msg, cfg);
        String command = shared.visit((style, text) -> style.getClickEvent() instanceof ClickEvent.SuggestCommand s
            ? java.util.Optional.of(s.command()) : java.util.Optional.<String>empty(), Style.EMPTY).orElseThrow();
        assertEquals("/img upload 2026-11-01_12.00.00.png", command);

        Component other = Component.literal("Saved screenshot as x.png");
        assertTrue(ScreenshotShare.apply(other, cfg) == other, "only the real screenshot message");
        Component odd = Component.translatable("screenshot.success", Component.literal("x.txt"));
        assertTrue(ScreenshotShare.apply(odd, cfg) == odd, "only images");
    }
}
