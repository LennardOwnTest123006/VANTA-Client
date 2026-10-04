package dev.vanta.core.perf;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Everything the Performance Center shows, captured once per frame.
 *
 * @param fps                    frames per second reported by the game
 * @param frameTimeAvgMs         mean frame time over the tracker window
 * @param frameTimeOnePercentMs  mean of the slowest 1 % of frames
 * @param frameTimeMaxMs         slowest frame in the window
 * @param onePercentLowFps       fps equivalent of {@code frameTimeOnePercentMs}
 * @param memory                 heap reading
 * @param renderDistance         chunks
 * @param simulationDistance     chunks
 * @param entityCount            loaded entities
 * @param cpuLoad                process CPU load when known
 * @param activePreset           preset matching the current options, if any
 * @param suggestion             pending render distance suggestion, if any
 */
public record PerformanceSnapshot(int fps, double frameTimeAvgMs, double frameTimeOnePercentMs, double frameTimeMaxMs,
                                  double onePercentLowFps, MemorySampler.MemorySample memory, int renderDistance,
                                  int simulationDistance, int entityCount, OptionalDouble cpuLoad,
                                  Optional<PerformancePreset> activePreset,
                                  Optional<RenderDistanceAdvisor.Suggestion> suggestion) {
}
