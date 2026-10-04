package dev.vanta.core.bridge;

import java.util.Optional;

/**
 * Typed access to vanilla options, implemented by the client on top of {@code net.minecraft.client.Options}.
 * <p>
 * Values are exchanged as {@link Integer}, {@link Double}, {@link Boolean} or {@link String} (upper-case enum constant
 * name) according to {@link VanillaOption#kind()}. Implementations should ignore writes of unsupported options.
 */
public interface OptionsBridge {

    /** True when the running game exposes this option. */
    boolean supports(VanillaOption option);

    /**
     * Current value, or empty when unsupported.
     */
    Optional<Object> get(VanillaOption option);

    /**
     * Sets a value. Implementations normalise with {@link VanillaOption#normalize(Object)} and apply side effects
     * (for example a render distance change reloads chunks).
     *
     * @return true when the value was applied
     */
    boolean set(VanillaOption option, Object value);

    /** Persists {@code options.txt}. */
    void save();

    /** Integer value with fallback. */
    default int getInt(VanillaOption option, int fallback) {
        return get(option).filter(v -> v instanceof Number).map(v -> ((Number) v).intValue()).orElse(fallback);
    }

    /** Double value with fallback. */
    default double getDouble(VanillaOption option, double fallback) {
        return get(option).filter(v -> v instanceof Number).map(v -> ((Number) v).doubleValue()).orElse(fallback);
    }

    /** Boolean value with fallback. */
    default boolean getBoolean(VanillaOption option, boolean fallback) {
        return get(option).filter(v -> v instanceof Boolean).map(v -> (Boolean) v).orElse(fallback);
    }

    /** Enum value (upper-case constant name) with fallback. */
    default String getEnum(VanillaOption option, String fallback) {
        return get(option).filter(v -> v instanceof String).map(v -> (String) v).orElse(fallback);
    }
}
