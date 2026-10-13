package ai.pollinations.minecraft;

import org.bukkit.configuration.file.FileConfiguration;

/** Values read from config.yml. Rebuilt on /polli reload. */
record Settings(
        String apiKey,
        String textModel,
        String imageModel,
        int maxTokens,
        long cooldownMillis,
        int maxRequestsPerHour,
        int memoryMessages) {

    static Settings from(FileConfiguration config) {
        return new Settings(
                config.getString("api-key", "").trim(),
                config.getString("text-model", "amazon/nova-micro-v1").trim(),
                config.getString("image-model", "black-forest-labs/flux.1-schnell").trim(),
                clamp(config.getInt("max-tokens", 120), 16, 512),
                Math.max(0, config.getLong("cooldown-seconds", 5)) * 1000L,
                clamp(config.getInt("max-requests-per-hour", 60), 1, 10_000),
                clamp(config.getInt("memory-messages", 12), 0, 40));
    }

    boolean hasKey() {
        return !apiKey.isEmpty();
    }

    /** Never prints the key, so a stray log line cannot leak it. */
    @Override
    public String toString() {
        return "Settings[textModel=" + textModel + ", imageModel=" + imageModel + ", apiKeySet=" + hasKey() + "]";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
