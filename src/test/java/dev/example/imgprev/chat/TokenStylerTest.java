package dev.example.imgprev.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.example.imgprev.token.TokenCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class TokenStylerTest {
    static final String GIF = TokenCodec.encode("https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif").orElseThrow();
    static final String IMG = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();

    /** (text, hover-token-or-null, color) per styled segment */
    record Seg(String text, String hover, TextColor color) {}

    static List<Seg> segments(Component c) {
        List<Seg> out = new ArrayList<>();
        c.visit((style, text) -> {
            String hover = style.getHoverEvent() instanceof HoverEvent.ShowText(Component v) ? v.getString() : null;
            out.add(new Seg(text, hover, style.getColor()));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    @Test void twoTokensWithSurroundingTextKeepOrder() {
        Component out = TokenStyler.apply(Component.literal("a " + GIF + " b " + IMG + " c"), m -> true);
        assertEquals("a [🖼 GIF] b [🖼 Image] c", out.getString());
        List<String> hovers = segments(out).stream().map(Seg::hover).filter(h -> h != null).toList();
        assertEquals(List.of(GIF, IMG), hovers);
    }

    @Test void adjacentTokens() {
        assertEquals("[🖼 GIF][🖼 Image]", TokenStyler.apply(Component.literal(GIF + IMG), m -> true).getString());
    }

    @Test void untouchedWithoutTokens() {
        Component in = Component.literal("hello <~ not a token ~>");
        assertSame(in, TokenStyler.apply(in, m -> true));
    }

    @Test void disallowedOrInvalidTokensStayAsText() {
        Component blocked = Component.literal("x " + GIF);
        assertSame(blocked, TokenStyler.apply(blocked, m -> false));
        String badCheck = GIF.substring(0, GIF.length() - 3) + (GIF.charAt(GIF.length() - 3) == 'A' ? 'B' : 'A') + "~>";
        Component invalid = Component.literal("y " + badCheck);
        assertSame(invalid, TokenStyler.apply(invalid, m -> true));
    }

    @Test void mixedValidAndInvalidKeepsInvalidAsText() {
        Component out = TokenStyler.apply(Component.literal("<~1gabc~> " + GIF), m -> true);
        assertEquals("<~1gabc~> [🖼 GIF]", out.getString());
    }

    @Test void keepsSurroundingStyleAndColorsOnlyTheLabel() {
        Component in = Component.literal("<Steve> ").withStyle(ChatFormatting.RED).append(Component.literal("see " + GIF));
        List<Seg> segs = segments(TokenStyler.apply(in, m -> true));
        assertEquals("<Steve> see [🖼 GIF]", segs.stream().map(Seg::text).reduce("", String::concat));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), segs.getFirst().color());
        assertEquals(TextColor.fromRgb(TokenStyler.LABEL_COLOR), segs.getLast().color());
        assertEquals(GIF, segs.getLast().hover());
    }
}
