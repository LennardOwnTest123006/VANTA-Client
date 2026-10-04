package dev.vanta.core.bridge;

/**
 * Immutable 3D position in world coordinates (double precision, like Minecraft's {@code Vec3}).
 */
public record Vec3d(double x, double y, double z) {
    /** Origin. */
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    /** Euclidean distance to {@code other}. */
    public double distanceTo(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Horizontal (XZ plane) distance to {@code other}. */
    public double horizontalDistanceTo(Vec3d other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Block coordinate of {@link #x()} (floor). */
    public int blockX() {
        return (int) Math.floor(x);
    }

    /** Block coordinate of {@link #y()} (floor). */
    public int blockY() {
        return (int) Math.floor(y);
    }

    /** Block coordinate of {@link #z()} (floor). */
    public int blockZ() {
        return (int) Math.floor(z);
    }
}
