package io.github.cjwmdev.imgprev.config;

import io.github.cjwmdev.imgprev.site.Label;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * One kind of label's colour on the settings screen: a hex box, then a small button that opens a grid of preset swatches.
 * The hex box starts blank. Picking a preset clears it; a hex colour typed there takes priority over the preset. The
 * row's name is the label itself, drawn in the colour it will get. Only loaded when Cloth Config is installed.
 */
final class LabelColourEntry extends TooltipListEntry<String> {
    static final int CELL = 14, SWATCH = 12, COLUMNS = 6, PAD = 3;
    static final List<TextColor> PRESETS = ImgPrevConfig.PRESET_COLORS;
    static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private final Label label;
    private final String original, defaultPreset;
    private final EditBox hex;
    private final Button presetButton, reset;
    private String preset;
    private boolean open;
    private int popupX, popupY;

    /** {@code current} and {@code defaultPreset} are saved forms such as "aqua" or "#FF8800". */
    LabelColourEntry(Label label, String current, String defaultPreset, Consumer<String> save) {
        super(Component.literal(label.labelText()), () -> Optional.of(new Component[] {
            Component.literal("Type a hex colour such as #FF8800, or pick a preset with the button."),
            Component.literal("A hex colour takes priority; picking a preset clears it.")}));
        Font font = Minecraft.getInstance().font;
        this.label = label;
        this.original = current;
        this.defaultPreset = defaultPreset;
        this.saveCallback = save;
        boolean isPreset = PRESETS.stream().anyMatch(p -> p.serialize().equals(current));
        preset = isPreset ? current : defaultPreset;
        hex = new EditBox(font, 0, 0, 60, 18, Component.literal("Hex colour"));
        hex.setMaxLength(7);
        hex.setHint(Component.literal("#RRGGBB").withStyle(s -> s.withColor(0x707070)));
        hex.setValue(isPreset ? "" : current);
        presetButton = Button.builder(Component.empty(), b -> open = !open).bounds(0, 0, 20, 20).build();
        presetButton.setTooltip(Tooltip.create(Component.literal("Presets")));
        Component resetText = Component.translatable("text.cloth-config.reset_value");
        reset = Button.builder(resetText, b -> { preset = defaultPreset; hex.setValue(""); open = false; })
            .bounds(0, 0, font.width(resetText) + 6, 20).build();
    }

    /** The typed colour, when the box holds a whole hex colour. */
    Optional<TextColor> typed() {
        String s = hex.getValue().strip();
        return HEX.matcher(s).matches() ? ImgPrevConfig.parseColor(s) : Optional.empty();
    }

    /** Something typed that isn't a whole hex colour. */
    boolean badHex() { return !hex.getValue().isBlank() && typed().isEmpty(); }

    TextColor colour() { return typed().orElseGet(() -> ImgPrevConfig.parseColor(preset).orElse(TextColor.WHITE)); }

    @Override public String getValue() { return colour().serialize(); }
    @Override public Optional<String> getDefaultValue() { return Optional.of(defaultPreset); }
    @Override public boolean isEdited() { return super.isEdited() || !getValue().equals(original); }

    @Override public Optional<Component> getError() {
        return badHex() ? Optional.of(Component.literal(label.text() + ": hex colours look like #FF8800")) : super.getError();
    }

    @Override public List<? extends GuiEventListener> children() { return List.of(hex, presetButton, reset); }
    @Override public List<? extends NarratableEntry> narratables() { return List.of(hex, presetButton, reset); }

    /** Laid out like Cloth's own rows: the controls fill the 150 px at the right, Reset last. */
    @Override public void extractRenderState(GuiGraphicsExtractor g, int index, int y, int x, int entryWidth, int entryHeight,
                                             int mouseX, int mouseY, boolean hovered, float delta) {
        super.extractRenderState(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
        Font font = Minecraft.getInstance().font;
        TextColor colour = colour();
        boolean overridden = typed().isPresent();

        g.text(font, Component.literal(label.labelText()).withStyle(s -> s.withColor(colour)), x, y + 6, 0xFFFFFFFF);

        reset.setX(x + entryWidth - reset.getWidth());
        reset.setY(y);
        reset.active = isEditable() && (!preset.equals(defaultPreset) || !hex.getValue().isEmpty());
        presetButton.setX(reset.getX() - 2 - presetButton.getWidth());
        presetButton.setY(y);
        hex.setX(x + entryWidth - 150 + 1);
        hex.setY(y + 1);
        hex.setWidth(presetButton.getX() - 3 - hex.getX());
        hex.setEditable(isEditable());
        hex.setTextColor(badHex() ? 0xFFFF5555 : 0xFF000000 | colour.getValue());

        hex.extractRenderState(g, mouseX, mouseY, delta);
        presetButton.extractRenderState(g, mouseX, mouseY, delta);
        reset.extractRenderState(g, mouseX, mouseY, delta);

        // The preset button is a swatch of the preset, dimmed while a typed hex colour overrides it.
        int sx = presetButton.getX() + 4, sy = y + 4;
        g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | ImgPrevConfig.parseColor(preset).orElse(TextColor.WHITE).getValue());
        if (overridden) g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xB0202020);

        popupX = presetButton.getX() + presetButton.getWidth() - popupWidth();
        popupY = y + 22;
    }

    static int popupWidth() { return COLUMNS * CELL + 2 * PAD - (CELL - SWATCH); }
    static int popupHeight() { int rows = (PRESETS.size() + COLUMNS - 1) / COLUMNS; return rows * CELL + 2 * PAD - (CELL - SWATCH); }

    /** Drawn after the whole list, so the swatch grid sits on top of the rows below. */
    @Override public void lateRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!open) return;
        g.fill(popupX - 1, popupY - 1, popupX + popupWidth() + 1, popupY + popupHeight() + 1, 0xFFA0A0A0);
        g.fill(popupX, popupY, popupX + popupWidth(), popupY + popupHeight(), 0xFF000000);
        for (int i = 0; i < PRESETS.size(); i++) {
            TextColor c = PRESETS.get(i);
            int cx = popupX + PAD + (i % COLUMNS) * CELL, cy = popupY + PAD + (i / COLUMNS) * CELL;
            boolean under = mouseX >= cx && mouseX < cx + SWATCH && mouseY >= cy && mouseY < cy + SWATCH;
            if (under || c.serialize().equals(preset)) g.fill(cx - 1, cy - 1, cx + SWATCH + 1, cy + SWATCH + 1, under ? 0xFFFFFFFF : 0xFF808080);
            g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | c.getValue());
            if (under) g.setTooltipForNextFrame(Minecraft.getInstance().font,
                Component.literal(ImgPrevConfig.colorName(c)).withStyle(s -> s.withColor(c)), mouseX, mouseY);
        }
    }

    /** The preset under the mouse in the open grid, or -1. */
    int presetAt(double mx, double my) {
        if (!open || mx < popupX + PAD || my < popupY + PAD) return -1;
        int col = (int) (mx - popupX - PAD) / CELL, row = (int) (my - popupY - PAD) / CELL, i = row * COLUMNS + col;
        return col < COLUMNS && i < PRESETS.size() ? i : -1;
    }

    boolean overPopup(double mx, double my) {
        return open && mx >= popupX && mx < popupX + popupWidth() && my >= popupY && my < popupY + popupHeight();
    }

    @Override public boolean isMouseOver(double mx, double my) { return overPopup(mx, my) || super.isMouseOver(mx, my); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overPopup(event.x(), event.y())) {
            int i = presetAt(event.x(), event.y());
            if (i >= 0) {
                preset = PRESETS.get(i).serialize();
                hex.setValue("");   // a picked preset replaces any typed colour
                open = false;
            }
            return true;
        }
        if (open && !presetButton.isMouseOver(event.x(), event.y())) open = false;
        return super.mouseClicked(event, doubleClick);
    }

    /** Lets the list scroll far enough to show the grid under the last row. */
    @Override public int getMorePossibleHeight() { return open ? popupHeight() + 4 : super.getMorePossibleHeight(); }

    @Override public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        if (!selected) {
            open = false;
            hex.setFocused(false);
        }
    }
}
