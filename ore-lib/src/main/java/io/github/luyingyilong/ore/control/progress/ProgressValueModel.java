package io.github.luyingyilong.ore.control.progress;

/** Shared, notification-free range arithmetic for determinate progress views. */
final class ProgressValueModel {
    private double min, max = 100, value;

    double getMin() { return min; }
    double getMax() { return max; }
    double getValue() { return value; }
    double getFraction() { return (value - min) / (max - min); }

    static void validateLimits(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max || !Double.isFinite(max - min))
            throw new IllegalArgumentException("Invalid progress limits");
    }

    void setLimits(double min, double max) {
        validateLimits(min, max);
        this.min = min;
        this.max = max;
        setValue(value);
    }

    void setValue(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid progress value");
        this.value = Math.max(min, Math.min(max, value));
    }
}
