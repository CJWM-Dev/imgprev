package io.github.cjwmdev.imgprev.saved;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.cjwmdev.imgprev.ImgPrevClient;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/imgprev-saved.json}: images saved with {@code /img save}, by name. Kept apart from imgprev.json so the
 * settings screen and {@code /imgprev reload} never touch it. Names are stored in lower case; each entry keeps the
 * token it was converted to when saved, so sending never waits on a lookup, and the link it came from.
 */
public final class SavedImages {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int MAX_NAME = 32;
    static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1," + MAX_NAME + "}");
    /** The words {@code /img} uses itself. */
    public static final Set<String> RESERVED = Set.of("save", "list", "remove");
    /** {@code :funnycat:} in a message, for {@link #expand}. */
    static final Pattern SHORTCUT = Pattern.compile(":([A-Za-z0-9_-]{1," + MAX_NAME + "}):");

    /** {@code link} is what was saved: a link, or a token that was pasted in. */
    public record Entry(String token, String link) {}

    /** The file's layout. */
    private record Contents(@Nullable Map<String, Entry> images) {}

    private final Path file;
    private final TreeMap<String, Entry> images = new TreeMap<>();

    SavedImages(Path file) { this.file = file; }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("imgprev-saved.json"); }

    public static SavedImages load() { return load(path()); }

    /**
     * A file that can't be read is copied to {@code imgprev-saved.json.broken} first, so the next save can't lose what
     * was in it. Entries with a bad name are dropped; ones without a usable token get one from their link if it converts
     * without a lookup, so the file can be written by hand.
     */
    static SavedImages load(Path p) {
        SavedImages saved = new SavedImages(p);
        if (!Files.exists(p)) return saved;
        Contents contents;
        try {
            contents = GSON.fromJson(Files.readString(p), Contents.class);
        } catch (IOException | JsonParseException e) {
            Path broken = p.resolveSibling(p.getFileName() + ".broken");
            ImgPrevClient.LOG.warn("Couldn't read {}, copied it to {} and started empty: {}", p, broken.getFileName(), e.toString());
            try { Files.copy(p, broken, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException copy) { ImgPrevClient.LOG.warn("Couldn't copy {}: {}", p, copy.toString()); }
            return saved;
        }
        if (contents == null || contents.images() == null) return saved;
        contents.images().forEach((rawName, e) -> {
            String name = normalise(rawName);
            if (e == null || nameProblem(name).isPresent()) {
                ImgPrevClient.LOG.warn("Skipped saved image {} in {}", rawName, p.getFileName());
                return;
            }
            String link = e.link() == null ? "" : e.link().strip();
            Optional<String> token = Optional.ofNullable(e.token()).map(String::strip).filter(t -> TokenCodec.decode(t).isPresent()).or(() -> tokenNow(link));
            if (token.isPresent()) saved.images.put(name, new Entry(token.get(), link.isEmpty() ? token.get() : link));
            else ImgPrevClient.LOG.warn("Skipped saved image {} in {}: no usable token or link", rawName, p.getFileName());
        });
        return saved;
    }

    /** {@code input}'s token when it's a token or a link that converts without a lookup. */
    public static Optional<String> tokenNow(String input) {
        return TokenCodec.decode(input).isPresent() ? Optional.of(input) : TokenCodec.encode(input);
    }

    public static String normalise(String name) { return name.strip().toLowerCase(Locale.ROOT); }

    /** Why {@code name} (already {@link #normalise normalised}) can't be used, or empty if it can. */
    public static Optional<String> nameProblem(String name) {
        if (name.length() > MAX_NAME) return Optional.of("Names can be at most " + MAX_NAME + " characters long.");
        if (!NAME.matcher(name).matches()) return Optional.of("Names can only use letters, numbers, _ and -.");
        if (RESERVED.contains(name)) return Optional.of("\"" + name + "\" is part of the /img command, so pick another name.");
        return Optional.empty();
    }

    public Optional<Entry> get(String name) { return Optional.ofNullable(images.get(normalise(name))); }

    /** Saves straight away; returns what {@code name} held before, if anything. */
    public Optional<Entry> put(String name, Entry entry) {
        Optional<Entry> old = Optional.ofNullable(images.put(normalise(name), entry));
        save();
        return old;
    }

    /** Saves straight away; returns what was removed, if anything. */
    public Optional<Entry> remove(String name) {
        Optional<Entry> old = Optional.ofNullable(images.remove(normalise(name)));
        if (old.isPresent()) save();
        return old;
    }

    /** By name, alphabetically. */
    public SortedMap<String, Entry> all() { return Collections.unmodifiableSortedMap(images); }

    /** Reads the file again into this object. */
    public void reload() {
        images.clear();
        images.putAll(load(file).images);
    }

    /**
     * {@code text} with each {@code :name:} of a saved image replaced by its token. Unknown names stay as typed, and
     * so does anything inside a link ({@code https://host/a:b:c}).
     */
    public String expand(String text) {
        if (images.isEmpty() || text.indexOf(':') < 0) return text;
        Matcher m = SHORTCUT.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (int from = 0; from < text.length() && m.find(from); ) {
            Entry e = images.get(m.group(1).toLowerCase(Locale.ROOT));
            if (e == null || insideLink(text, m.start())) {
                from = m.end() - 1;   // that closing colon may open the next name ("12:30:cat:")
                continue;
            }
            out.append(text, last, m.start()).append(e.token());
            last = from = m.end();
        }
        return last == 0 ? text : out.append(text, last, text.length()).toString();
    }

    /** Whether the space-separated word around {@code at} contains "://". */
    static boolean insideLink(String text, int at) {
        int start = text.lastIndexOf(' ', at) + 1, end = text.indexOf(' ', at);
        return text.substring(start, end < 0 ? text.length() : end).contains("://");
    }

    /** Written beside the file, then moved over it, as the config is. */
    void save() {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, GSON.toJson(new Contents(images)));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ImgPrevClient.LOG.warn("Couldn't save {}: {}", file, e.toString());
        }
    }
}
