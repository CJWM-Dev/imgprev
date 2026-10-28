package io.github.cjwmdev.imgprev.site;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of label a token becomes in chat. Each has one symbol from Minecraft's own bitmap font (include/default.json),
 * so it stays crisp at every GUI scale, and a default colour. {@link #text} is also the key in config/imgprev.json.
 */
public enum Label {
    GIF("GIF", "▶", "aqua"),
    IMAGE("Image", "⧈", "green"),   // a framed picture
    EMOJI("Emoji", "☺", "yellow"),
    /** Discord's older, lighter blurple, which reads better on chat's dark background than the current one. */
    DISCORD("Discord", "✉", "#7289DA");

    private final String text, icon, defaultColor;

    Label(String text, String icon, String defaultColor) {
        this.text = text;
        this.icon = icon;
        this.defaultColor = defaultColor;
    }

    public String text() { return text; }
    public String icon() { return icon; }
    /** A chat colour name or {@code #RRGGBB}, as saved in the config. */
    public String defaultColor() { return defaultColor; }
    /** "[▶ GIF]". */
    public String labelText() { return "[" + icon + " " + text + "]"; }

    public static Optional<Label> byText(String text) { return Arrays.stream(values()).filter(l -> l.text.equals(text)).findFirst(); }
}
