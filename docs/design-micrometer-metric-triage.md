# Design: typesafe-ai-java-micrometer

Jev-powered instrumentation review for JVM applications, in the spirit of
ishantanu/jevmetrics (Go OTel Collector processor) but in-process for
Micrometer/`MeterRegistry`, built on `typesafe-ai-java`. New Maven module
`typesafe-ai-java-micrometer` in the existing repo.

## 0. Repositioning after critique (Sep 23, 2026)

The original draft framed this as a metric-storage cost tool. That framing is weak
for an in-process library and the critique stands; the framing is now:

**Primary value: instrumentation review.** The model reads meter metadata and
surfaces dead-looking, redundant-looking, or badly-described meters as a REPORT.
The artifact is a lint for metrics, consumed at review time, not a pipeline that
saves storage bills.

**Secondary value (opt-in experiment): reduce mode.** Retained because it is the
jevmetrics-parity feature and worth evaluating, but the docs must state its
structural limits (see 4). It is not the headline.

What we are NOT claiming, ever: downsampling, aggregation, fleet-level cost
savings, or that a model judgment replaces usage-based retention signals.

## 1. Problem

jevmetrics sits in an OTel Collector and asks Jev whether a metric is worth keeping:
4 questions (relevance, redundancy, keep, recommended action), deterministic local
policy on the answers, cache by metric identity. Nothing equivalent exists for the
JVM. Micrometer is the natural in-process analog: every Spring Boot app already has
a `MeterRegistry`, and `Meter.Id` (name, tags, unit, baseUnit, description) carries
the metadata evidence the model can judge.

The honest gap: usage-based systems (Grafana Adaptive Metrics) decide from what is
actually QUERIED, which is stronger evidence than any static-metadata judgment.
Metadata inference is a prior. It is most useful exactly where usage signals do not
exist yet: a brand-new service whose meters nobody has queried or named carefully.
Position the tool there, not against Adaptive Metrics.

## 2. Goal / non-goals

Goal (primary): a review/lint library. On startup (and on demand), assess the app's
meter names through Jev, cache the verdicts, and expose them as (a) companion
`jev.metric.*` gauges for live inspection, (b) a structured report (log + JSON file)
listing meters worth a human look, with the model's reasoning.

Goal (secondary): `reduce` mode, a `MeterFilter` denying registration below a keep
threshold, protection allowlist, explicitly documented as experimental.

Non-goals:
- No downsampling, no aggregation, no dimension reduction. A verdict applies to a
  whole meter name, nothing else.
- No fleet-level telemetry control; in-process only. Complementary to a
  Collector-level processor like jevmetrics, not a replacement.
- No model training, no re-scoring of values, no usage tracking.
- No claim that metadata inference beats usage-based retention where usage exists.

## 3. Module placement

`typesafe-ai-java-micrometer`, sibling of core / kotlin / spring-ai / starter:
- `io.micrometer:micrometer-core` as `provided` scope (do not force it on consumers).
- Depends on `typesafe-ai-java-core` only. No Spring inside this module: keep it the
  zero-Spring lightweight piece, consistent with our positioning vs the community SDK.
- Spring Boot auto-config lives in the existing `spring-boot-starter` module
  (new auto-configuration class + properties, registered in
  `AutoConfiguration.imports`).

## 4. Identity and the cardinality trap

**Assessment identity = meter NAME only (default), tags excluded.** Critique point:
keying by full `Meter.Id` makes the tool spend Jev calls proportional to tag
cardinality, exactly the disease it exists to treat. One gauge tagged by user ID
would trigger thousands of assessments. Assess the name once; the verdict covers
every meter registered under it. A `per-identity` option stays for the rare case
(tag-set-specific verdicts), default off.

Cache TTL: with name-level static metadata, re-running inference after TTL expiry
produces no new information. Default TTL is effectively infinite for a JVM lifetime;
the property exists for long-running processes during development iterations, and
the docs should not pretend re-scoring discovers anything new.

## 5. Core API (micrometer module)

```
JevMetricAssessor                  // orchestrates: extract name -> dedupe -> evaluate -> cache
JevMeterAssessment                 // record: meter name, 4 answer values, timestamp
JevMeterFilter implements MeterFilter  // reduce-mode entry point
JevAssessmentCache                 // bounded, keyed by meter name
JevProtectedMetrics                // explicit allowlist, bypasses inference entirely
JevMetricReport                    // structured review artifact (the primary output)
JevMetricReviewListener            // hook: fires per verdict + on report completion
```

Key decisions:

- **Jev call: all four questions in ONE evaluate** (per API reference; ~500-800
  input tokens, ~0.6-2.4s via gateway). Relevance and redundancy are informational;
  `keep` noul drives policy; recommended `action` is exposed but never overrides
  the keep-score threshold.
- **Async always.** Registration never blocks on a network call. First sight of a
  new name: admit, enqueue, background worker calls Jev, listener + cache update.
- **Review mode (default)**: everything registered; companion gauges
  `jev.metric.relevance`, `jev.metric.redundancy`, `jev.metric.keep_probability`
  (tags: `metric.name`, `jev.model`) plus `jev.metric.recommended_action` (tag:
  `action`). AND the report: at a configurable point (default: 60s after startup
  or on `JevMetricReport.request()`), emit one WARN-group log with the flagged
  meters, and write `jev-metric-report.json` (assessments, reasons, model id,
  timestamp) for humans to read in a PR or code review.
- **reduce mode**: `MeterFilter.reply(deny)` when cached keepProbability <
  threshold. Documented limits, stated in the README, not buried:
  (a) most meters register at startup before any verdict exists, so with fail-open
  defaults the filter only bites on registrations AFTER the first assessment wave
  (dynamic meters, second phase of a lazy-init app) or on the next boot with a
  persistent cache; (b) fail-closed would make a Jev outage a telemetry blackhole
  during incidents, so fail-open is mandatory, which caps reduce mode at
  "gradual + advisory". An in-memory cache seed file (optional, off by default)
  can carry verdicts across restarts for teams who want filtering from boot.
- **Failure policy**: any Jev error -> log WARN once per name, meter stays
  admitted, exponential backoff. Never throw out of `accept()`.

## 6. Spring Boot auto-config (starter module)

```
typesafe.jev.metrics.enabled            (default true when a key exists)
typesafe.jev.metrics.mode               review | reduce   (default review; old name
                                        "annotate" aliased)
typesafe.jev.metrics.keep-threshold     0.0-1.0, default 0.5 (reduce mode only)
typesafe.jev.metrics.protected-names    exact meter names, always admitted
typesafe.jev.metrics.cache-max          default 500 (name-level)
typesafe.jev.metrics.cache-ttl          default: no expiry
typesafe.jev.metrics.report-delay       default 60s; 0 disables scheduled report
typesafe.jev.metrics.report-file        default none (log-only)
typesafe.jev.metrics.seed-file          optional verdict cache across restarts
typesafe.jev.metrics.worker-threads     default 1
```

Backs off with the same `TypeSafeApiKeyCondition` pattern as the existing client
bean (property OR `TYPESAFE_API_KEY` env), `@ConditionalOnMissingBean` on the
filter/listener beans so user beans win. `ApplicationContextRunner` tests for both
modes and backoff.

## 7. Testing

Mirror the jevmetrics test list, mapped to Micrometer, on the existing
fake-transport pattern (no key, no network):
- response validation: malformed/missing keep answer -> failure path, meter admitted
- mode normalization: unknown/legacy mode string -> review + WARN
- policy: threshold boundary (== threshold keeps), protected-name bypass
- payload preservation: counter/gauge/timer/long-task-timer untouched in review
  mode; companion gauges appear once per NAME, not per tag set
- identity: two Meter.Ids same name different tags share one assessment
- cache: eviction at max, TTL only when explicitly configured
- queue saturation: burst of new names -> bounded queue, no OOM, no blocking
- failure recovery: transport throws -> WARN once, backoff, later success caches
- report: JSON shape, flagged-list correctness vs raw answers, report-delay=0 off
- reduce limits (regression for honesty): startup-burst meters NOT denied on boot 1
  with empty cache; denied on boot 2 with seed file; Jev outage admits everything
- auto-config: runner tests for both modes, backoff without key, user bean wins
- Surefire 3.2.5 pinned in the module pom; assert on "Tests run" counts

Live smoke test env-gated (`TYPESAFE_API_KEY` + `assumeTrue`), same as core.

## 8. Milestones

1. M1: micrometer module - name-level identity, cache, assessor, review mode,
   report, tests
2. M2: reduce mode + protection rules + seed file + the honesty regression tests
3. M3: starter auto-config + runner tests + README (with the reduce-limits section
   as a first-class section, not a footnote)
4. M4: examples app + article arc. Angle: "metric linting with a decision model",
   not "cut your metrics bill". Gate through check_article_safety.py.

## 9. Open questions

- Version line: new module; propose shipping inside 0.3.0.
- Optional second target later: an OpenTelemetry Java SDK extension for
  non-Micrometer apps, and/or an OTel Collector route if we ever want the
  fleet-level vantage point jevmetrics has. Out of scope for M1-M3.
- Companion gauge naming: keep jevmetrics' `jev.metric.*` for recognizability
  (recommended); alternative `typesafe.metric.*`.
- Report delivery: log + JSON file for M1; a build/CI plugin (fail the build on a
  new "dead" meter) is a plausible M5 if the report proves useful in practice.
