package io.github.cjwmdev.imgprev.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewportTest {
    static final double E = 1e-9;

    /** A 400×300 area at (10, 20) showing an image that fits it as 400×200, zoomable to 8×. */
    static Viewport wide() {
        Viewport v = new Viewport();
        v.layout(10, 20, 400, 300, 400, 200, 8);
        return v;
    }

    @Test void fitsKeepingShapeButLimitsEnlarging() {
        assertArrayEquals(new double[] {400, 200}, Viewport.fit(1600, 800, 400, 300, 4), E);
        assertArrayEquals(new double[] {150, 300}, Viewport.fit(100, 200, 400, 300, 4), E, "tall: height-limited");
        assertArrayEquals(new double[] {64, 32}, Viewport.fit(16, 8, 400, 300, 4), E, "tiny: at most 4× its size");
    }

    @Test void startsCentred() {
        Viewport.Rect r = wide().image();
        assertEquals(10, r.x(), E);
        assertEquals(20 + 50, r.y(), E, "letterboxed: 50 above and below");
        assertEquals(400, r.w(), E);
        assertEquals(200, r.h(), E);
    }

    @Test void zoomKeepsThePointUnderTheCursorStill() {
        Viewport v = wide();
        double px = 110, py = 120;   // a quarter across, halfway down the image
        Viewport.Rect before = v.image();
        double u = (px - before.x()) / before.w(), w = (py - before.y()) / before.h();
        v.zoomAt(2, px, py);
        Viewport.Rect after = v.image();
        assertEquals(800, after.w(), E);
        assertEquals(u, (px - after.x()) / after.w(), E);
        assertEquals(w, (py - after.y()) / after.h(), E);
        assertTrue(v.zoomedIn());
    }

    @Test void cantZoomOutPastFittingOrInPastTheLimit() {
        Viewport v = wide();
        v.zoomAt(0.25, 200, 150);
        assertEquals(1, v.zoom(), E);
        assertFalse(v.zoomedIn());
        for (int i = 0; i < 50; i++) v.zoomAt(Viewport.STEP, 200, 150);
        assertEquals(8, v.zoom(), E);
    }

    @Test void draggingStopsAtTheImagesEdges() {
        Viewport v = wide();
        v.zoomTo(4, 210, 170);   // 1600×800 in a 400×300 area
        v.pan(1e6, 1e6);   // dragged far right and down: the top-left corner meets the area's
        Viewport.Rect r = v.image();
        assertEquals(10, r.x(), E);
        assertEquals(20, r.y(), E);
        v.pan(-1e6, -1e6);
        r = v.image();
        assertEquals(10 + 400, r.x() + r.w(), E, "right edge meets the area's");
        assertEquals(20 + 300, r.y() + r.h(), E);
    }

    @Test void aSideSmallerThanTheAreaStaysCentred() {
        Viewport v = wide();
        v.zoomTo(1.2, 10, 20);   // 480×240: wider than the area, still shorter
        v.pan(0, 500);
        assertEquals(20 + (300 - 240) / 2.0, v.image().y(), E);
    }

    @Test void resetGoesBackToTheWholeImage() {
        Viewport v = wide();
        v.zoomTo(5, 50, 50);
        v.pan(30, -10);
        v.reset();
        assertEquals(10, v.image().x(), E);
        assertEquals(1, v.zoom(), E);
    }

    @Test void aSharperCopyOfTheImageDoesntMoveTheView() {
        Viewport v = wide();
        v.zoomAt(3, 300, 100);
        Viewport.Rect before = v.image();
        v.layout(10, 20, 400, 300, 400, 200, 16);   // same shape and fit; a bigger zoom limit
        Viewport.Rect after = v.image();
        assertEquals(before.x(), after.x(), E);
        assertEquals(before.w(), after.w(), E);
    }
}
