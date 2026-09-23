# Design: typesafe-ai-java-micrometer

Jev-style metric triage for JVM applications, in the spirit of ishantanu/jevmetrics
(Go OTel Collector processor) but in-process for Micrometer/`MeterRegistry`, built on
`typesafe-ai-java`. New Maven module `typesafe-ai-java-micrometer` in the existing repo.

## 1. Problem

jevmetrics sits in an OTel Collector and asks Jev whether a metric is worth keeping:
4 questions (relevance, redundancy, keep, recommended action), deterministic local
policy on the answers, cache by metric identity, `annotate` mode first and `reduce`
(keep-score filtering) second. Nothing equivalent exists for the JVM. Micrometer is the
natural in-process analog: every Spring Boot app already has a `MeterRegistry`, and the
`Meter.Id` (name, tags, unit, baseUnit, description) carries the same metadata evidence
jevmetrics feeds to the model.

## 2. Goal / non-goals

Goal: a library that assesses an app's meters through Jev and either (annotate mode)
emits companion `jev.metric.*` gauges for review, or (reduce mode) denies meter
registration below a keep threshold, with an explicit protection allowlist.

Non-goals (match jevmetrics honesty):
- No downsampling, no aggregation, no dimension reduction. Filtering = deny/allow a
  whole meter identity, nothing else.
- No fleet-level telemetry control. In-process only; it affects this app's registry.
  Complementary to a Collector-level processor like jevmetrics, not a replacement.
- No model training, no re-scoring of values. Only meter metadata is assessed.

## 3. Module placement

`typesafe-ai-java-micrometer`, sibling of core / kotlin / spring-ai / starter:
- `io.micrometer:micrometer-core` as `provided` scope (do not force it on consumers).
- Depends on `typesafe-ai-java-core` only. No Spring inside this module: keep it the
  zero-Spring lightweight piece, consistent with our positioning vs the community SDK.
- Spring Boot auto-config lives in the existing `spring-boot-starter` module
  (new auto-configuration class + properties, registered in
  `AutoConfiguration.imports`).

## 4. Core API (micrometer module)

```
JevMetricAssessor                  // orchestrates: extract -> dedupe -> evaluate -> cache
JevMeterAssessment                 // record: Meter.Id coordinates, 4 answer values, timestamp
JevMeterFilter implements MeterFilter  // the Micrometer entry point
JevAssessmentCache                 // bounded LRU + TTL, keyed by stable meter identity
JevProtectedMetrics                // explicit allowlist, bypasses inference entirely
```

Key decisions:

- **Identity = normalized `Meter.Id`.** Name + tag key/value pairs sorted, plus unit.
  Stable string key (like jevmetrics' cache identity) so the same meter never triggers
  a second inference call.
- **Jev call: all four questions in ONE evaluate** (per API reference; ~500-800 input
  tokens, ~0.6-2.4s via gateway). Relevance and redundancy are informational;
  `keep` noul drives policy; recommended `action` is exposed but never overrides
  the keep-score threshold.
- **Async by design.** The filter must never block meter registration on a network
  call. On first sight of a new meter: admit it (annotate) or apply cached/default
  policy, enqueue the assessment, background worker calls Jev, cache updated.
  Subsequent batches/registrations use the cached decision.
- **annotate mode**: original meter always registered. Companion gauges
  `jev.metric.relevance`, `jev.metric.redundancy`, `jev.metric.keep_probability`
  registered under the original meter's tags plus `metric.name` and `jev.model`
  tags, mirroring jevmetrics. `jev.metric.recommended_action` as a gauge with an
  `action` tag (keep=1). Protected meters get no companions.
- **reduce mode**: `MeterFilter.reply(deny)` when cached keepProbability <
  threshold. Before the first assessment lands, default is ADMIT (fail-open), so a
  Jev outage can never blackhole telemetry. Protection allowlist always wins.
- **Failure policy**: any Jev error → log at WARN once per identity, meter stays
  admitted, retry with exponential backoff. Never throw out of `accept()`.

## 5. Spring Boot auto-config (starter module)

```
typesafe.jev.metrics.enabled            (default true when a key exists)
typesafe.jev.metrics.mode               annotate | reduce   (default annotate)
typesafe.jev.metrics.keep-threshold     0.0-1.0, default 0.5 (reduce mode only)
typesafe.jev.metrics.protected-names    list of exact meter names, always admitted
typesafe.jev.metrics.cache-max          LRU size, default 1000
typesafe.jev.metrics.cache-ttl          default 24h
typesafe.jev.metrics.worker-threads     default 1
```

Backs off with the same `TypeSafeApiKeyCondition` pattern as the existing client
bean (property OR `TYPESAFE_API_KEY` env), `@ConditionalOnMissingBean` on the filter
so user beans win. `ApplicationContextRunner` tests for both modes and for backoff.

## 6. Testing

Mirror jevmetrics' test list, mapped to Micrometer, on the existing fake-transport
pattern (no key, no network):
- response validation: malformed/missing keep answer -> failure path, meter admitted
- mode normalization: unknown mode string -> annotate + WARN
- policy: threshold boundary (== threshold keeps), protected-name bypass
- payload preservation: counter/gauge/timer/long-task-timer all pass through
  untouched in annotate mode; companion gauges appear with correct tags
- cache: identity dedupe (two `Meter.Id`s differing only in tag ORDER share one
  entry), TTL expiry, eviction at max size
- queue saturation: unbounded burst of new meters -> bounded work queue, oldest
  dropped, no OOM, no registration blocking
- failure recovery: transport throws -> WARN once, backoff, later success caches
- auto-config: runner tests for both modes, backoff without key, user bean wins
- Surefire 3.2.5 pinned in the module pom; assert on "Tests run" counts, not exit code

Live smoke test env-gated (`TYPESAFE_API_KEY` + `assumeTrue`), same as core.

## 7. Milestones

1. M1: micrometer module - identity, cache, assessor, filter, annotate mode, tests
2. M2: reduce mode + protection rules + policy tests
3. M3: starter auto-config + runner tests + README section
4. M4: examples app + "I built jevmetrics-style triage for Spring Boot" article arc
   (gate through check_article_safety.py like every article)

## 8. Open questions

- Version line: this is a new module; propose shipping inside 0.3.0 (module addition
  is minor, not patch).
- Optional second target later: an OpenTelemetry Java SDK extension for non-Micrometer
  apps; explicitly out of scope for M1-M3.
- Naming: `jev.metric.*` companion gauge names copied from jevmetrics for ecosystem
  recognizability; alternative is a `typesafe.metric.*` prefix. Default proposal:
  keep `jev.metric.*` (the model is Jev, regardless of serving backend).
