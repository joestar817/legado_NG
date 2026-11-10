package io.legado.themes.rain_night;

/**
 * JVM regression for the accepted rain-night profile, moved from the former host
 * ListeningMotionConfigTest. Run against this author's compiled production classes.
 * The renderer/Android framework is never instantiated and no Gradle task is needed.
 */
public final class RainNightProfileTest {
    private static int assertions;

    public static void main(String[] args) {
        fullStrengthProfileRemainsAccepted();
        dropletsStayWithinAnimationCycleBudget();
        shaderHashTableCoversEveryRequiredIndex();
        System.out.println("RainNightProfileTest: 3 cases, " + assertions + " assertions passed");
    }

    private static void fullStrengthProfileRemainsAccepted() {
        // Keep the previously accepted visual tuning, not a second implementation.
        equal(20f, RainNightProfile.DURATION_SECONDS, "duration");
        equal(2.4f, RainNightProfile.RAIN_DENSITY, "rain density");
        equal(1.6f, RainNightProfile.DROP_LEVEL, "droplet level");
        equal(1.6f, RainNightProfile.FOG_LEVEL, "fog level");
        equal(.6f, RainNightProfile.LEAF_LEVEL, "leaf level");
        equal(1_272, RainNightProfile.RAIN_LINE_COUNT, "total rain lines");
        equal(504, RainNightProfile.INSTANCE.rainCount(210), "back rain layer");
        equal(384, RainNightProfile.INSTANCE.rainCount(160), "middle rain layer");
        equal(252, RainNightProfile.INSTANCE.rainCount(105), "front rain layer");
        equal(132, RainNightProfile.INSTANCE.rainCount(55), "nearest rain layer");
        equal(48, RainNightProfile.DROPLET_COUNT, "droplets");
        equal(6, RainNightProfile.FOG_COUNT, "fog layers");
        equal(25, RainNightProfile.LEAF_COUNT, "leaf instances");
        equal(25, RainNightProfile.INSTANCE.leafCount(), "computed leaf instances");
    }

    private static void dropletsStayWithinAnimationCycleBudget() {
        for (int index = 0; index < RainNightProfile.DROPLET_COUNT; index++) {
            int cycles = RainNightProfile.INSTANCE.dropletCycles(index);
            require(cycles >= 2 && cycles <= 6, "droplet cycle range at " + index);
        }
    }

    private static void shaderHashTableCoversEveryRequiredIndex() {
        equal(RainNightProfile.HASH_ENTRY_COUNT,
                RainNightProfile.HASH_TEXTURE_WIDTH * RainNightProfile.HASH_TEXTURE_HEIGHT,
                "hash texture dimensions");
        equal(RainNightProfile.MAX_USED_HASH_INDEX,
                RainNightProfile.INSTANCE.computedMaxUsedHashIndex(), "shader maximum index");
        require(RainNightProfile.MAX_USED_HASH_INDEX < RainNightProfile.HASH_ENTRY_COUNT,
                "hash texture must cover every shader sample");
        for (int index = 0; index < RainNightProfile.HASH_ENTRY_COUNT; index++) {
            double value = RainNightProfile.INSTANCE.hash(index);
            require(Double.isFinite(value) && value >= 0.0 && value < 1.0,
                    "finite unit hash at " + index);
            require(Double.compare(value, RainNightProfile.INSTANCE.hash(index)) == 0,
                    "deterministic hash at " + index);
        }
    }

    private static void equal(float expected, float actual, String description) {
        require(Float.compare(expected, actual) == 0,
                description + ": expected " + expected + ", actual " + actual);
    }

    private static void equal(int expected, int actual, String description) {
        require(expected == actual,
                description + ": expected " + expected + ", actual " + actual);
    }

    private static void require(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}