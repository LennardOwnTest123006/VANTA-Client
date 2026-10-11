package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.timer.TimerReading;
import dev.vanta.core.timer.TimerState;
import dev.vanta.core.ui.Canvas;

/**
 * The countdown of VANTA's timer: the player's label (or "Timer", {@code showLabel}) and the time left as
 * {@code m:ss} or {@code h:mm:ss}; "Paused" next to a paused timer and "Time is up" in the accent colour while the
 * alarm rings. While no timer is set the widget draws nothing at all, not even its panel; the HUD editor shows the
 * {@link TimerReading#SAMPLE} ({@code 9:59}) marked as a preview so the widget can be placed.
 */
public final class TimerWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.TIMER;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        if (!state.propBool("showLabel")) {
            return null;
        }
        String label = data.timer().label();
        return label.isEmpty() ? Lang.tr("vanta.hud.label.timer") : label;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        TimerReading timer = data.timer();
        return timer.state() == TimerState.RINGING ? Lang.tr("vanta.hud.label.timer_ringing") : timer.remainingText();
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        TimerReading timer = data.timer();
        if (timer.state() == TimerState.PAUSED) {
            return Lang.tr("vanta.hud.label.timer_paused");
        }
        return timer.sample() ? Lang.tr("vanta.hud.label.timer_preview") : null;
    }

    @Override
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        return switch (data.timer().state()) {
            case RINGING -> state.accentColor();
            case PAUSED -> paint.muted(state);
            default -> state.textColor();
        };
    }

    /** Nothing at all while no timer is set; otherwise the panel and the line. */
    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        if (!data.timer().isActive()) {
            return;
        }
        super.render(canvas, state, data, paint);
    }
}
