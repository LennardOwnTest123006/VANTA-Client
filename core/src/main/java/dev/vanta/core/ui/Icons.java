package dev.vanta.core.ui;

/**
 * The VANTA icon set, drawn from primitives at any pixel size so icons stay crisp at every GUI scale and need no
 * texture. Designed on a 16-unit grid with a 1.5-unit stroke; at the usual 8-12 px sizes strokes are 1 px.
 */
public enum Icons {
    CLOSE, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, CHECK, PLUS, MINUS, SEARCH, GEAR, DRAG, RESET,
    INFO, WARNING, ERROR, SUCCESS, EYE, EYE_OFF, LOCK, PLAY, FOLDER, DOWNLOAD, UPLOAD, TRASH, COPY, EDIT, STAR,
    GRID, LIST, ARROW_UP, ARROW_DOWN, EXTERNAL_LINK, SLIDERS, PALETTE, KEYBOARD, CHART, CROSSHAIR, PROFILE,
    PACKAGE, ACCESSIBILITY;

    /** Draws the icon into the square {@code (x, y, size, size)}. */
    public void draw(Canvas canvas, int x, int y, int size, int argb) {
        if (size <= 0 || Colors.alpha(argb) == 0) {
            return;
        }
        IconPen p = new IconPen(canvas, x, y, size, argb);
        switch (this) {
            case CLOSE -> {
                p.line(4f, 4f, 12f, 12f);
                p.line(12f, 4f, 4f, 12f);
            }
            case CHEVRON_LEFT -> {
                p.line(10f, 3f, 5f, 8f);
                p.line(5f, 8f, 10f, 13f);
            }
            case CHEVRON_RIGHT -> {
                p.line(6f, 3f, 11f, 8f);
                p.line(11f, 8f, 6f, 13f);
            }
            case CHEVRON_UP -> {
                p.line(3f, 10.5f, 8f, 5.5f);
                p.line(8f, 5.5f, 13f, 10.5f);
            }
            case CHEVRON_DOWN -> {
                p.line(3f, 5.5f, 8f, 10.5f);
                p.line(8f, 10.5f, 13f, 5.5f);
            }
            case CHECK -> {
                p.line(3f, 8.5f, 6.5f, 12f);
                p.line(6.5f, 12f, 13f, 4.5f);
            }
            case PLUS -> {
                p.hline(3f, 13f, 8f);
                p.vline(8f, 3f, 13f);
            }
            case MINUS -> p.hline(3f, 13f, 8f);
            case SEARCH -> {
                p.ring(7f, 7f, 4.5f);
                p.line(10.5f, 10.5f, 13.5f, 13.5f);
            }
            case GEAR -> {
                p.ring(8f, 8f, 4f);
                p.vline(8f, 1.5f, 4f);
                p.vline(8f, 12f, 14.5f);
                p.hline(1.5f, 4f, 8f);
                p.hline(12f, 14.5f, 8f);
                p.line(3.4f, 3.4f, 5.2f, 5.2f);
                p.line(12.6f, 3.4f, 10.8f, 5.2f);
                p.line(3.4f, 12.6f, 5.2f, 10.8f);
                p.line(12.6f, 12.6f, 10.8f, 10.8f);
            }
            case DRAG -> {
                for (int row = 0; row < 3; row++) {
                    float v = 3f + row * 4f;
                    p.rect(5f, v, 2f, 2f);
                    p.rect(9f, v, 2f, 2f);
                }
            }
            case RESET -> {
                p.arc(8f, 8f, 5.5f, -40f, 230f);
                p.triangle(9f, 1.5f, 13.5f, 1.5f, 13.5f, 6f);
            }
            case INFO -> {
                p.ring(8f, 8f, 6.5f);
                p.vline(8f, 7f, 11.5f);
                p.rect(7.2f, 4.3f, 1.6f, 1.6f);
            }
            case WARNING -> {
                p.line(8f, 2f, 14.5f, 14f);
                p.hline(1.5f, 14.5f, 14f);
                p.line(8f, 2f, 1.5f, 14f);
                p.vline(8f, 6f, 10f);
                p.rect(7.2f, 11.2f, 1.6f, 1.6f);
            }
            case ERROR -> {
                p.ring(8f, 8f, 6.5f);
                p.line(5.5f, 5.5f, 10.5f, 10.5f);
                p.line(10.5f, 5.5f, 5.5f, 10.5f);
            }
            case SUCCESS -> {
                p.ring(8f, 8f, 6.5f);
                p.line(5f, 8.2f, 7.2f, 10.4f);
                p.line(7.2f, 10.4f, 11.2f, 5.8f);
            }
            case EYE -> {
                eyeOutline(p);
                p.disc(8f, 8f, 2.2f);
            }
            case EYE_OFF -> {
                eyeOutline(p);
                p.disc(8f, 8f, 2.2f);
                p.line(2.5f, 13.5f, 13.5f, 2.5f);
            }
            case LOCK -> {
                // Solid body reads better than an outline below 14 px; the keyhole needs the extra room.
                if (size >= 14) {
                    p.rectOutline(3.5f, 7.5f, 9f, 7f);
                    p.rect(7.2f, 10f, 1.6f, 2.2f);
                } else {
                    p.rect(3.5f, 7.5f, 9f, 7f);
                }
                p.arc(8f, 7.5f, 3.2f, 180f, 360f);
            }
            case PLAY -> p.triangle(4f, 2.5f, 13.5f, 8f, 4f, 13.5f);
            case FOLDER -> {
                p.rectOutline(1.5f, 4.5f, 13f, 9f);
                p.rect(1.5f, 2.5f, 5.5f, 2f);
            }
            case DOWNLOAD -> {
                p.vline(8f, 2f, 9.5f);
                p.line(4.5f, 6.5f, 8f, 10f);
                p.line(11.5f, 6.5f, 8f, 10f);
                tray(p);
            }
            case UPLOAD -> {
                p.vline(8f, 2.5f, 10f);
                p.line(4.5f, 6f, 8f, 2.5f);
                p.line(11.5f, 6f, 8f, 2.5f);
                tray(p);
            }
            case TRASH -> {
                p.hline(2.5f, 13.5f, 3.5f);
                p.rect(6f, 1.5f, 4f, 2f);
                p.vline(3.8f, 3.5f, 14.5f);
                p.vline(12.2f, 3.5f, 14.5f);
                p.hline(3.8f, 12.2f, 14.5f);
                p.vline(6.5f, 6f, 12f);
                p.vline(9.5f, 6f, 12f);
            }
            case COPY -> {
                p.rectOutline(5.5f, 5.5f, 8.5f, 8.5f);
                p.hline(2.5f, 10f, 2.5f);
                p.vline(2.5f, 2.5f, 10f);
                p.vline(10f, 2.5f, 4.5f);
                p.hline(2.5f, 4.5f, 10f);
            }
            case EDIT -> {
                p.line(3f, 13f, 11.5f, 4.5f);
                p.line(4.5f, 14.5f, 13f, 6f);
                p.line(11.5f, 4.5f, 13f, 6f);
                p.line(3f, 13f, 2.5f, 14.5f);
                p.line(2.5f, 14.5f, 4.5f, 14.5f);
            }
            case STAR -> star(p);
            case GRID -> {
                p.rect(2.5f, 2.5f, 4.5f, 4.5f);
                p.rect(9f, 2.5f, 4.5f, 4.5f);
                p.rect(2.5f, 9f, 4.5f, 4.5f);
                p.rect(9f, 9f, 4.5f, 4.5f);
            }
            case LIST -> {
                for (int row = 0; row < 3; row++) {
                    float v = 4f + row * 4f;
                    p.rect(2.5f, v - 1f, 2f, 2f);
                    p.hline(6f, 13.5f, v);
                }
            }
            case ARROW_UP -> {
                p.vline(8f, 2.5f, 13.5f);
                p.line(3.5f, 7f, 8f, 2.5f);
                p.line(12.5f, 7f, 8f, 2.5f);
            }
            case ARROW_DOWN -> {
                p.vline(8f, 2.5f, 13.5f);
                p.line(3.5f, 9f, 8f, 13.5f);
                p.line(12.5f, 9f, 8f, 13.5f);
            }
            case EXTERNAL_LINK -> {
                p.vline(2.5f, 5f, 13.5f);
                p.hline(2.5f, 11f, 13.5f);
                p.vline(11f, 9f, 13.5f);
                p.hline(2.5f, 7f, 5f);
                p.line(7f, 9f, 13.5f, 2.5f);
                p.hline(9f, 13.5f, 2.5f);
                p.vline(13.5f, 2.5f, 7f);
            }
            case SLIDERS -> {
                p.hline(2f, 14f, 4f);
                p.rect(9f, 2.5f, 3f, 3f);
                p.hline(2f, 14f, 8f);
                p.rect(4f, 6.5f, 3f, 3f);
                p.hline(2f, 14f, 12f);
                p.rect(10f, 10.5f, 3f, 3f);
            }
            case PALETTE -> {
                p.ring(8f, 8f, 6.5f);
                p.disc(5f, 6.5f, 1.4f);
                p.disc(8.2f, 4.6f, 1.4f);
                p.disc(11.2f, 6.6f, 1.4f);
                p.disc(5.4f, 10.2f, 1.4f);
                if (size >= 14) {
                    p.ring(10.3f, 10.8f, 1.9f);
                }
            }
            case KEYBOARD -> {
                p.rectOutline(1.5f, 4f, 13f, 8.5f);
                p.rect(3.8f, 6.3f, 1.6f, 1.6f);
                p.rect(7.2f, 6.3f, 1.6f, 1.6f);
                p.rect(10.6f, 6.3f, 1.6f, 1.6f);
                p.hline(5f, 11f, 10.2f);
            }
            case CHART -> {
                p.rect(2.5f, 9f, 2.6f, 4.5f);
                p.rect(6.7f, 5.5f, 2.6f, 8f);
                p.rect(10.9f, 2.5f, 2.6f, 11f);
                p.hline(1.5f, 14.5f, 14.3f);
            }
            case CROSSHAIR -> {
                p.ring(8f, 8f, 5f);
                p.hline(1.5f, 4.5f, 8f);
                p.hline(11.5f, 14.5f, 8f);
                p.vline(8f, 1.5f, 4.5f);
                p.vline(8f, 11.5f, 14.5f);
                p.rect(7.4f, 7.4f, 1.2f, 1.2f);
            }
            case PROFILE -> {
                p.ring(8f, 5.5f, 3f);
                p.arc(8f, 16f, 6.5f, 200f, 340f);
            }
            case PACKAGE -> {
                p.line(2.5f, 5f, 8f, 2f);
                p.line(8f, 2f, 13.5f, 5f);
                p.line(13.5f, 5f, 8f, 8f);
                p.line(8f, 8f, 2.5f, 5f);
                p.vline(2.5f, 5f, 11.5f);
                p.vline(13.5f, 5f, 11.5f);
                p.line(2.5f, 11.5f, 8f, 14.5f);
                p.line(13.5f, 11.5f, 8f, 14.5f);
                p.vline(8f, 8f, 14.5f);
            }
            case ACCESSIBILITY -> {
                p.disc(8f, 3f, 1.8f);
                p.hline(3f, 13f, 6.5f);
                p.vline(8f, 6.5f, 9.5f);
                p.line(8f, 9.5f, 5f, 14f);
                p.line(8f, 9.5f, 11f, 14f);
            }
        }
    }

    private static void eyeOutline(IconPen p) {
        p.line(1.5f, 8f, 5f, 4.5f);
        p.hline(5f, 11f, 4.5f);
        p.line(11f, 4.5f, 14.5f, 8f);
        p.line(14.5f, 8f, 11f, 11.5f);
        p.hline(5f, 11f, 11.5f);
        p.line(5f, 11.5f, 1.5f, 8f);
    }

    private static void tray(IconPen p) {
        p.vline(2.5f, 10.5f, 14f);
        p.hline(2.5f, 13.5f, 14f);
        p.vline(13.5f, 10.5f, 14f);
    }

    private static void star(IconPen p) {
        float cx = 8f;
        float cy = 8.6f;
        float outer = 6.8f;
        float inner = 3f;
        float[] xs = new float[10];
        float[] ys = new float[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float r = (i % 2 == 0) ? outer : inner;
            xs[i] = cx + (float) Math.cos(angle) * r;
            ys[i] = cy + (float) Math.sin(angle) * r;
        }
        for (int i = 0; i < 10; i++) {
            int next = (i + 1) % 10;
            p.triangle(cx, cy, xs[i], ys[i], xs[next], ys[next]);
        }
    }
}
