package io.github.cjwmdev.imgprev.render;

/**
 * The full-screen viewer's camera, in GUI units: how far in it's zoomed, and which point of the image sits in the
 * middle of the viewing area. Kept in image-relative terms (zoom 1 = the whole image fits, points 0..1 across and
 * down), so swapping in a sharper copy of the image mid-view doesn't move anything. Pure maths, for unit tests.
 */
final class Viewport {
    /** Each scroll notch or +/- press zooms by this much. */
    static final double STEP = 1.25;

    /** Where something is drawn: top-left corner and size. */
    record Rect(double x, double y, double w, double h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private double ax, ay, aw = 1, ah = 1, fitW = 1, fitH = 1;
    private double zoom = 1, maxZoom = 1;
    /** The image point at the middle of the area. */
    private double cu = 0.5, cv = 0.5;

    /**
     * The area the image is shown in, the image's size when zoom is 1, and how far in it may zoom. Called every
     * frame; the window or image may have changed since the last.
     */
    void layout(double x, double y, double w, double h, double fittedW, double fittedH, double maxZoom) {
        ax = x; ay = y; aw = Math.max(1, w); ah = Math.max(1, h);
        fitW = Math.max(1e-6, fittedW); fitH = Math.max(1e-6, fittedH);
        this.maxZoom = Math.max(1, maxZoom);
        zoom = Math.clamp(zoom, 1, this.maxZoom);
        clamp();
    }

    /** {@code w×h} scaled to fit inside {@code areaW×areaH}, keeping its shape, but at most {@code maxUpscale} times bigger. */
    static double[] fit(double w, double h, double areaW, double areaH, double maxUpscale) {
        double s = Math.min(Math.min(areaW / w, areaH / h), maxUpscale);
        return new double[] {w * s, h * s};
    }

    /** Where the image goes now. */
    Rect image() {
        double w = fitW * zoom, h = fitH * zoom;
        return new Rect(ax + aw / 2 - cu * w, ay + ah / 2 - cv * h, w, h);
    }

    double zoom() { return zoom; }
    boolean zoomedIn() { return zoom > 1 + 1e-9; }

    /** Zooms by {@code factor}, keeping the image point under {@code (px, py)} where it is (as far as the edges allow). */
    void zoomAt(double factor, double px, double py) {
        Rect r = image();
        double u = (px - r.x()) / r.w(), v = (py - r.y()) / r.h();
        zoom = Math.clamp(zoom * factor, 1, maxZoom);
        double w = fitW * zoom, h = fitH * zoom;
        cu = (ax + aw / 2 - (px - u * w)) / w;
        cv = (ay + ah / 2 - (py - v * h)) / h;
        clamp();
    }

    /** Zooms to exactly {@code target} (clamped), about {@code (px, py)}. */
    void zoomTo(double target, double px, double py) { zoomAt(target / zoom, px, py); }

    /** Moves the image by {@code (dx, dy)}, as when it's dragged. */
    void pan(double dx, double dy) {
        cu -= dx / (fitW * zoom);
        cv -= dy / (fitH * zoom);
        clamp();
    }

    void reset() {
        zoom = 1;
        cu = cv = 0.5;
    }

    /** An image smaller than the area stays centred on that axis; a bigger one can't be dragged past its edges. */
    private void clamp() {
        double w = fitW * zoom, h = fitH * zoom;
        cu = w <= aw ? 0.5 : Math.clamp(cu, aw / 2 / w, 1 - aw / 2 / w);
        cv = h <= ah ? 0.5 : Math.clamp(cv, ah / 2 / h, 1 - ah / 2 / h);
    }
}
