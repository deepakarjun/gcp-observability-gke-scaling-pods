package com.costco.foundation.integration.framework.gcp.scaling.enums.gke;

import java.time.Duration;

/**
 * Selectable time windows for the utilization widgets. {@code CUSTOM} indicates
 * an explicit start/end supplied by the caller.
 */
public enum UtilizationTimeRange {

    ONE_MIN(Duration.ofMinutes(1)),
    FIVE_MINS(Duration.ofMinutes(5)),
    TEN_MINS(Duration.ofMinutes(10)),
    THIRTY_MINS(Duration.ofMinutes(30)),
    CUSTOM(null);

    /** Default range applied when none is supplied on the initial call. */
    public static final UtilizationTimeRange DEFAULT = THIRTY_MINS;

    private final Duration _duration;

    UtilizationTimeRange(Duration duration) {
        _duration = duration;
    }

    /** @return the fixed lookback duration, or {@code null} for {@code CUSTOM} */
    public Duration duration() {
        return _duration;
    }
}