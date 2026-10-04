package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.cosmetics.ParticleSystem;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Live thumbnail of a {@link MenuParticles} kind: a deterministic {@link ParticleSystem} advanced once per tick,
 * drawn over the dark-gradient menu background. The system is pre-warmed on the first layout so the card never
 * starts empty.
 */
public final class ParticlePreview extends UiNode {
    /** Milliseconds simulated per game tick. */
    public static final long TICK_MS = 50L;
    /**
     * The simulation runs on a canvas this many times larger than the thumbnail and positions are scaled down when
     * drawing: the particle system sizes its population by area, so a tiny thumbnail would otherwise show only the
     * minimum handful of particles.
     */
    public static final int VIRTUAL_SCALE = 5;
    private static final int WARM_UP_TICKS = 80;

    private final ParticleSystem system;
    private boolean warmed;

    public ParticlePreview(MenuParticles kind, long seed) {
        this.system = new ParticleSystem(kind, seed);
    }

    /** The simulation. */
    public ParticleSystem system() {
        return system;
    }

    @Override
    public void layout(UiContext ctx) {
        super.layout(ctx);
        Rect b = bounds();
        if (!warmed && b.w() > 0 && b.h() > 0) {
            warmed = true;
            for (int i = 0; i < WARM_UP_TICKS; i++) {
                system.update(TICK_MS, b.w() * VIRTUAL_SCALE, b.h() * VIRTUAL_SCALE);
            }
        }
    }

    @Override
    public void onTick(UiContext ctx) {
        super.onTick(ctx);
        Rect b = bounds();
        if (b.w() > 0 && b.h() > 0 && !ctx.theme().reducedMotion()) {
            system.update(TICK_MS, b.w() * VIRTUAL_SCALE, b.h() * VIRTUAL_SCALE);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Rect b = bounds();
        Thumbnails.background(canvas, b, MenuBackground.GRADIENT_DARK, ctx.theme());
        List<ParticleSystem.Particle> scaled = new ArrayList<>();
        for (ParticleSystem.Particle p : system.particles()) {
            scaled.add(new ParticleSystem.Particle(p.x() / VIRTUAL_SCALE, p.y() / VIRTUAL_SCALE, p.size(), p.alpha(),
                    p.color()));
        }
        Thumbnails.particles(canvas, b, scaled);
    }
}
