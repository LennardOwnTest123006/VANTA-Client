package dev.vanta.core.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic ambient particle simulation for the main menu. Pure model: {@link #update} advances the state and
 * {@link #particles()} returns what to draw. The same seed and the same sequence of {@code update} calls always
 * produce the same frames, which keeps screenshots and tests stable.
 */
public final class ParticleSystem {

    /**
     * One particle in screen pixels.
     *
     * @param x     centre x
     * @param y     centre y
     * @param size  diameter
     * @param alpha 0–1
     * @param color RGB (alpha applied separately)
     */
    public record Particle(double x, double y, double size, double alpha, int color) {
    }

    private static final class Body {
        double x;
        double y;
        double vx;
        double vy;
        double size;
        double ageMs;
        double lifeMs;
        double phase;
        int color;
    }

    private final Random random;
    private final List<Body> bodies = new ArrayList<>();
    private MenuParticles kind;
    private double spawnCarry;

    public ParticleSystem(MenuParticles kind, long seed) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.random = new Random(seed);
    }

    /** Current particle kind. */
    public MenuParticles kind() {
        return kind;
    }

    /** Switches the kind; existing particles fade out naturally. */
    public void setKind(MenuParticles next) {
        Objects.requireNonNull(next, "next");
        if (next != kind) {
            kind = next;
            if (next == MenuParticles.NONE) {
                bodies.clear();
            }
        }
    }

    /** Target particle count for the kind and screen area. */
    public int targetCount(int width, int height) {
        if (kind == MenuParticles.NONE) {
            return 0;
        }
        double area = Math.max(1, width) * (double) Math.max(1, height);
        int base = kind == MenuParticles.EMBERS ? 48 : 72;
        return (int) Math.max(8, Math.min(base, Math.round(base * area / (854.0 * 480.0))));
    }

    /**
     * Advances the simulation.
     *
     * @param dtMs   elapsed milliseconds (clamped to 100 ms to stay stable after a pause)
     * @param width  screen width in pixels
     * @param height screen height in pixels
     */
    public void update(double dtMs, int width, int height) {
        double dt = Math.max(0, Math.min(100, dtMs));
        if (kind == MenuParticles.NONE) {
            bodies.clear();
            return;
        }
        int target = targetCount(width, height);
        spawnCarry += dt / 40.0;
        while (bodies.size() < target && spawnCarry >= 1) {
            bodies.add(spawn(width, height, true));
            spawnCarry -= 1;
        }
        for (int i = bodies.size() - 1; i >= 0; i--) {
            Body b = bodies.get(i);
            b.ageMs += dt;
            double t = dt / 1000.0;
            b.x += (b.vx + Math.sin((b.ageMs / 1000.0) * 1.3 + b.phase) * (kind == MenuParticles.EMBERS ? 6 : 3)) * t;
            b.y += b.vy * t;
            boolean offscreen = b.y < -b.size || b.y > height + b.size || b.x < -b.size || b.x > width + b.size;
            if (b.ageMs >= b.lifeMs || offscreen) {
                bodies.remove(i);
            }
        }
        while (bodies.size() > target) {
            bodies.remove(bodies.size() - 1);
        }
    }

    private Body spawn(int width, int height, boolean anywhere) {
        Body b = new Body();
        b.x = random.nextDouble() * width;
        if (kind == MenuParticles.EMBERS) {
            b.y = anywhere ? random.nextDouble() * height : height + 4;
            b.vx = (random.nextDouble() - 0.5) * 8;
            b.vy = -(10 + random.nextDouble() * 18);
            b.size = 1 + random.nextDouble() * 2;
            b.lifeMs = 6000 + random.nextDouble() * 6000;
            b.color = random.nextDouble() < 0.7 ? 0xF5B942 : 0x7C5CFF;
        } else {
            b.y = random.nextDouble() * height;
            b.vx = (random.nextDouble() - 0.5) * 5;
            b.vy = (random.nextDouble() - 0.5) * 4;
            b.size = 0.8 + random.nextDouble() * 1.6;
            b.lifeMs = 8000 + random.nextDouble() * 8000;
            b.color = random.nextDouble() < 0.6 ? 0xA1A1AA : 0x4F8DFF;
        }
        b.phase = random.nextDouble() * Math.PI * 2;
        return b;
    }

    /** Particles to draw, with a fade in/out envelope applied to alpha. */
    public List<Particle> particles() {
        List<Particle> out = new ArrayList<>(bodies.size());
        for (Body b : bodies) {
            double life = b.lifeMs <= 0 ? 1 : b.ageMs / b.lifeMs;
            double envelope = Math.min(1.0, Math.min(life * 6, (1 - life) * 4));
            double base = kind == MenuParticles.EMBERS ? 0.85 : 0.5;
            out.add(new Particle(b.x, b.y, b.size, Math.max(0, Math.min(1, envelope * base)), b.color));
        }
        return Collections.unmodifiableList(out);
    }

    /** Number of live particles. */
    public int count() {
        return bodies.size();
    }
}
