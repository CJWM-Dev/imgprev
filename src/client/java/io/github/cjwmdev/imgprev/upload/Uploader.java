package io.github.cjwmdev.imgprev.upload;

import io.github.cjwmdev.imgprev.image.MediaException;
import io.github.cjwmdev.imgprev.image.SafeHttp;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * {@code /img upload}: sends an image from the screenshots folder to Litterbox (temporary) or Catbox (permanent).
 * Both take anonymous uploads with no key and answer with the file's link, which becomes the token. Only image files
 * directly in that folder are sent, checked by their first bytes; big stills are scaled down and sent as JPEG, so
 * previews stay quick to load for whoever hovers them.
 */
public final class Uploader {
    static final URI LITTERBOX = URI.create("https://litterbox.catbox.moe/resources/internals/api.php");
    static final URI CATBOX = URI.create("https://catbox.moe/user/api.php");
    static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "webp");
    /** PNG and JPEG files bigger than this are shrunk; GIF and WebP ones are refused, since shrinking would stop them moving. */
    static final int SHRINK_OVER = 8 << 20;
    /** Longest side of a shrunk image; previews are drawn far smaller than this anyway. */
    static final int MAX_SIDE = 2560;
    /** Files bigger than this aren't even read. */
    static final long MAX_FILE = 64L << 20;
    /** From sending the request to the answer; an 8 MB upload needs about 0.4 Mbit/s to make it. */
    static final Duration WAIT = Duration.ofSeconds(180);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NEVER).build();
    /** One upload at a time, in the order asked for. */
    private static final Executor POOL = Executors.newSingleThreadExecutor(SafeHttp.daemon("imgprev-upload"));
    private static volatile @Nullable URI testServer;

    record Prepared(byte[] bytes, String ext) {}

    /** An upload's address on the site, and the token for it. */
    public record Uploaded(String link, String token) {}

    private Uploader() {}

    /** Game tests only: uploads go to this local server, whichever mode is chosen, until it's set back to null. */
    public static void useTestServer(@Nullable URI endpoint) { testServer = endpoint; }

    /** Image files directly in {@code dir}, newest first; empty if there's no such folder yet. */
    public static List<String> images(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> extension(p.getFileName().toString()).isPresent() && Files.isRegularFile(p))
                .sorted(Comparator.comparing(Uploader::modified).reversed())
                .map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static FileTime modified(Path p) {
        try { return Files.getLastModifiedTime(p); } catch (IOException e) { return FileTime.fromMillis(0); }
    }

    /** {@code name} in {@code dir}, only if it's an image file directly inside it: never another folder, "..", or another kind of file. */
    public static Optional<Path> find(Path dir, String name) {
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":") || extension(name).isEmpty()) return Optional.empty();
        Path folder = dir.toAbsolutePath().normalize(), p = folder.resolve(name).normalize();
        return folder.equals(p.getParent()) && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    static Optional<String> extension(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.contains(ext) ? Optional.of(ext) : Optional.empty();
    }

    /** Completes with the uploaded file's link and token; fails with an exception {@link #describe} turns into a message. */
    public static CompletableFuture<Uploaded> upload(Path file, UploadMode mode, String lifetime) {
        URI endpoint = Optional.ofNullable(testServer).orElse(mode == UploadMode.TEMPORARY ? LITTERBOX : CATBOX);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.size(file) > MAX_FILE) throw new MediaException("That file is too big to upload (over " + (MAX_FILE >> 20) + " MB)");
                Prepared p = prepare(Files.readAllBytes(file), SHRINK_OVER, MAX_SIDE);
                String link = post(endpoint, mode, lifetime, p);
                return new Uploaded(link, token(mode, link));
            } catch (IOException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, POOL);
    }

    /** A message for a failed {@link #upload}. */
    public static String describe(Throwable err, UploadMode mode) {
        String site = mode.site();
        return switch (SafeHttp.unwrap(err)) {
            case MediaException e -> e.getMessage();
            case HttpConnectTimeoutException e -> "Couldn't connect to " + site + " in time";
            case HttpTimeoutException e -> site + " took too long to answer";
            case UnknownHostException e -> "Couldn't find " + site + ". Check your connection";
            case ConnectException e -> "Couldn't connect to " + site + ". Check your connection";
            case IOException e -> "Lost the connection to " + site;
            default -> "The upload failed";
        };
    }

    /** The file as it will be sent, named for what its bytes are rather than what its name says. */
    static Prepared prepare(byte[] bytes, int shrinkOver, int maxSide) throws IOException {
        String type = sniff(bytes).orElseThrow(() -> new MediaException("That file isn't a PNG, JPEG, GIF or WebP image"));
        if (bytes.length <= shrinkOver) return new Prepared(bytes, type);
        if (type.equals("gif") || type.equals("webp")) {
            throw new MediaException("Animations can be at most " + mb(shrinkOver) + " to upload; this one is " + mb(bytes.length));
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new MediaException("Couldn't read the image");
        byte[] jpeg = jpeg(scaled(img, maxSide), 0.9f);
        if (jpeg.length > shrinkOver) throw new MediaException("The image is too big to upload, even made smaller (" + mb(jpeg.length) + ")");
        return new Prepared(jpeg, "jpg");
    }

    private static String mb(long bytes) { return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0); }

    /** "png", "jpg", "gif" or "webp" from the file's first bytes. */
    static Optional<String> sniff(byte[] b) {
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) return Optional.of("png");
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return Optional.of("jpg");
        if (startsWith(b, 0, 'G', 'I', 'F', '8')) return Optional.of("gif");
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return Optional.of("webp");
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, int at, int... expected) {
        if (b.length < at + expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((b[at + i] & 0xFF) != expected[i]) return false;
        return true;
    }

    /** No bigger than {@code maxSide} on its longest side, without transparency (JPEG has none). */
    static BufferedImage scaled(BufferedImage src, int maxSide) {
        double f = Math.min(1.0, (double) maxSide / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * f)), h = Math.max(1, (int) Math.round(src.getHeight() * f));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Sends the file; returns the site's answer, which on success is the file's link. */
    static String post(URI endpoint, UploadMode mode, String lifetime, Prepared p) throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("reqtype", "fileupload");
        if (mode == UploadMode.TEMPORARY) fields.put("time", lifetime);
        String boundary = "ImgPrev" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest req = HttpRequest.newBuilder(endpoint).timeout(WAIT)
            .header("User-Agent", SafeHttp.USER_AGENT)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, fields, "image." + p.ext(), p.bytes())))
            .build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String reply = r.body() == null ? "" : r.body().strip();
        if (r.statusCode() != 200) throw new MediaException(refusal(mode, r.statusCode(), reply));
        return reply;
    }

    /**
     * Why an upload was turned away, for chat. The sites' own messages ("File too large") are quoted; error pages
     * from their servers or firewall aren't, since their text is mostly styling, so those get a plain explanation.
     */
    static String refusal(UploadMode mode, int status, String reply) {
        String site = mode.site(), code = " (HTTP " + status + ")";
        if (status >= 500) {
            return site + " is having problems" + code + ". Try again in a few minutes"
                + (mode == UploadMode.TEMPORARY ? ", or upload to Catbox instead, which keeps it for good (/imgprev uploads permanent)" : "");
        }
        if (status == 403 || status == 429) return site + " is turning uploads away for now" + code + ". Wait a few minutes and try again";
        boolean page = reply.regionMatches(true, 0, "<!doctype", 0, 9) || reply.regionMatches(true, 0, "<html", 0, 5);
        return site + " refused the upload" + code + (reply.isEmpty() || page ? "" : ": " + shorten(reply));
    }

    /** The upload sends a neutral file name, never the one on disk. */
    static byte[] multipart(String boundary, Map<String, String> fields, String fileName, byte[] file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 512);
        fields.forEach((k, v) -> write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + k + "\"\r\n\r\n" + v + "\r\n"));
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName
            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        out.writeBytes(file);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) { out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); }

    /** The token for the site's answer, if it's a link to the file on the host this mode uploads to. */
    static String token(UploadMode mode, String reply) throws MediaException {
        String host = mode == UploadMode.TEMPORARY ? "litter.catbox.moe" : "files.catbox.moe";
        URI u;
        try { u = new URI(reply); } catch (URISyntaxException e) { u = null; }
        if (u == null || !"https".equals(u.getScheme()) || !host.equals(u.getHost())) {
            throw new MediaException(mode.site() + " didn't send back a link" + (reply.isEmpty() ? "" : ": " + shorten(reply)));
        }
        return TokenCodec.encode(reply).orElseThrow(() -> new MediaException(mode.site() + " sent back a link ImgPrev can't use: " + shorten(reply)));
    }

    /** One line of at most 80 characters, for error messages quoting a site's answer. */
    static String shorten(String s) {
        String line = s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
        return line.length() <= 80 ? line : line.substring(0, 79) + "…";
    }
}
