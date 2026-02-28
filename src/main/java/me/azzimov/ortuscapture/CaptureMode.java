package me.azzimov.ortuscapture;

public enum CaptureMode {
    TIME,
    POINTS;

    public static CaptureMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return TIME;
        }
        try {
            return CaptureMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TIME;
        }
    }
}
