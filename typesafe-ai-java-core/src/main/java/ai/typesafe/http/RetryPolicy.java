package ai.typesafe.http;

import java.time.Duration;
import java.util.Set;

/**
 * Retry configuration, mirroring the official SDK defaults:
 * 2 retries, 0.5s initial backoff doubling to 5s with jitter,
 * retrying 408/429/5xx and honoring Retry-After headers.
 */
public record RetryPolicy(
        int maxRetries,
        Duration backoffInitial,
        Duration backoffMax,
        double backoffJitter,
        Set<Integer> httpStatuses,
        boolean respectRetryAfter,
        boolean retryConnectionErrors,
        boolean retryTimeoutErrors,
        Double totalBudgetSeconds) {

    /** Retries the documented transient statuses plus all 5xx. */
    public static final Set<Integer> DEFAULT_RETRY_STATUSES =
            Set.of(408, 429, 500, 501, 502, 503, 504, 505, 506, 507, 508, 510, 511, 529);

    public RetryPolicy {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        if (backoffJitter < 0.0 || backoffJitter > 1.0) {
            throw new IllegalArgumentException("backoffJitter must be between 0 and 1");
        }
        httpStatuses = Set.copyOf(httpStatuses);
    }

    /** The official SDK default policy. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(
                2,
                Duration.ofMillis(500),
                Duration.ofSeconds(5),
                0.25,
                DEFAULT_RETRY_STATUSES,
                true,
                true,
                true,
                30.0);
    }

    /** Never retry; surface the first error immediately. */
    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, Duration.ZERO, 0.0,
                Set.of(), false, false, false, null);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private int maxRetries = 2;
        private Duration backoffInitial = Duration.ofMillis(500);
        private Duration backoffMax = Duration.ofSeconds(5);
        private double backoffJitter = 0.25;
        private Set<Integer> httpStatuses = DEFAULT_RETRY_STATUSES;
        private boolean respectRetryAfter = true;
        private boolean retryConnectionErrors = true;
        private boolean retryTimeoutErrors = true;
        private Double totalBudgetSeconds = 30.0;

        public Builder() {
        }

        private Builder(RetryPolicy p) {
            this.maxRetries = p.maxRetries;
            this.backoffInitial = p.backoffInitial;
            this.backoffMax = p.backoffMax;
            this.backoffJitter = p.backoffJitter;
            this.httpStatuses = p.httpStatuses;
            this.respectRetryAfter = p.respectRetryAfter;
            this.retryConnectionErrors = p.retryConnectionErrors;
            this.retryTimeoutErrors = p.retryTimeoutErrors;
            this.totalBudgetSeconds = p.totalBudgetSeconds;
        }

        public Builder maxRetries(int v) {
            this.maxRetries = v;
            return this;
        }

        public Builder backoffInitial(Duration v) {
            this.backoffInitial = v;
            return this;
        }

        public Builder backoffMax(Duration v) {
            this.backoffMax = v;
            return this;
        }

        public Builder backoffJitter(double v) {
            this.backoffJitter = v;
            return this;
        }

        public Builder httpStatuses(Set<Integer> v) {
            this.httpStatuses = v;
            return this;
        }

        public Builder respectRetryAfter(boolean v) {
            this.respectRetryAfter = v;
            return this;
        }

        public Builder totalBudgetSeconds(Double v) {
            this.totalBudgetSeconds = v;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter,
                    httpStatuses, respectRetryAfter, retryConnectionErrors, retryTimeoutErrors,
                    totalBudgetSeconds);
        }
    }
}
