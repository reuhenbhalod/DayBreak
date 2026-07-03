# Product Requirements Document: "Daybreak"

An Oura-style sleep, recovery, and activity companion running on COLMI R09 smart-ring hardware. Local, offline, subscription-free. Built for one primary user (the maintainer's father) and deployed as a sideloaded Android app.

| | |
|---|---|
| **Working name** | Daybreak (placeholder; rename freely) |
| **Owner** | You (developer + maintainer) |
| **Primary user** | Your father |
| **Status** | Draft v0.1 |
| **Hardware** | COLMI R09 smart ring (BLE, BlueX SoC) |
| **Platform** | Native Android (Kotlin, Jetpack Compose) |
| **Date** | June 2026 |

> Daybreak is an independent, Oura-inspired app. It is not affiliated with or endorsed by Oura, does not use Oura's branding or algorithms, and reimplements a comparable experience with an open scoring model. It is a wellness tool, not a medical device.

---

## 1. Summary

Oura turns body signals into three daily 0-100 scores (Sleep, Readiness, Activity) plus plain-language guidance, behind a $300+ ring and a paid membership. Daybreak delivers the same glanceable daily experience on a ~$26 COLMI R09, with all data and computation staying on the phone. The hard differentiator is not raw sensor parity (the cheap ring can't match Oura's finger-grade temperature or HRV fidelity), it is a dead-simple, zero-maintenance experience tuned for an older non-technical user: open the app, see how you slept and whether to take it easy today, in one screen, in words.

## 2. Background and motivation

The COLMI ring family is fully reverse-engineered. Heart rate, SpO2, steps, and sleep staging are decodable over BLE, and open implementations already exist (Gadgetbridge for Android in Java, PulseLoop for iOS, the tahnok Python client). What does not exist is a clean, opinionated, *simple* app aimed at a single non-technical wearer. The COLMI's own QRing app is cluttered and cloud-dependent; Gadgetbridge is powerful but power-user oriented. The gap Daybreak fills is the last mile: a friendly daily summary on top of already-available data.

## 3. Goals

1. Replicate Oura's three-score daily experience (Sleep, Recovery, Activity) using only data the R09 exposes.
2. Be usable by a non-technical person with zero ongoing interaction beyond wearing and charging the ring.
3. Operate fully offline. No account, no cloud, no subscription. All data on-device.
4. Sync automatically in the background a few times a day, self-healing around missed windows; the user never presses "sync."
5. Present one plain-language daily summary ("You slept 7h 10m and recovered well. Good day to be active.").

## 4. Non-goals

1. Matching Oura's sensor accuracy or its temperature/HRV fidelity.
2. Temperature-based illness prediction, menstrual-cycle tracking, or fertility features (no reliable temp sensor).
3. Glucose, VO2 max, automatic workout detection, GPS, or 40+ activity recognition.
4. Multi-user accounts, social features, or data sharing.
5. Any cloud sync or companion web app in v1.
6. Cloning Oura branding, copy, or proprietary scoring weights.

## 5. Users and personas

**Primary: "Dad" (wearer).** Non-technical, wears the ring continuously, charges it every few days. Wants a simple read on sleep and energy. Will not open settings, will not troubleshoot Bluetooth, will not interpret graphs. Success = he glances once a day and understands it.

**Secondary: You (maintainer).** Technical. Perform one-time setup (pairing, permissions, battery exemption). Occasionally update the app. Want the architecture clean enough to extend (e.g., add the LLM daily-summary later).

## 6. Hardware capability matrix

Honest mapping of what Oura provides versus what the R09 can actually deliver. This drives every feature decision below.

| Signal | Oura | COLMI R09 | Daybreak decision |
|---|---|---|---|
| Resting heart rate | Yes, high fidelity | Yes (optical, lower fidelity) | **Use.** Core recovery input. |
| Heart rate (logged/real-time) | Yes | Yes | **Use.** |
| Sleep stages (wake/light/deep/REM) | 79% vs PSG | Yes, decodable (0xBC big-data), lower accuracy | **Use, with caveats.** |
| SpO2 (overnight) | Yes | Yes (background) | **Use** as a secondary signal. |
| Steps / movement | Yes | Yes (accelerometer) | **Use.** Core activity input. |
| HRV | Yes, 5-min granularity | Firmware-dependent, often unavailable/coarse | **Use if present; degrade gracefully if not.** |
| Body temperature | Yes, 0.13 °C precision | No reliable sensor | **Drop entirely.** |
| Respiratory rate | Yes | Unreliable/undecoded | **Out of scope v1.** |
| Battery life | ~7 days | Smaller; ~2-4 days | Design sync around frequent short connects. |

The two consequential losses are **temperature** (kills illness/cycle prediction) and **reliable HRV** (weakens recovery scoring). The scoring model in section 10 is designed to remain useful without either.

## 7. Product principles

1. **One screen is the product — for the wearer.** Everything essential is visible on the home screen without scrolling or tapping. Depth (charts, trends, raw metrics) lives one tap away on a separate Insights screen for the maintainer and the data-curious, and never intrudes on the wearer's glance.
2. **Words over numbers — on the home screen.** A sentence beats a dashboard for the primary user. The Insights screen is the opposite by design: rich numbers, graphs, and charts for whoever wants to dig in.
3. **Invisible failure.** Out-of-range ring, failed sync, or missing night shows yesterday's data quietly. Never an error dialog, never a stack trace, never a permission prompt after day one.
4. **Trends over single readings.** Scores are baseline-relative and emphasize multi-day direction, matching Oura's philosophy and hiding noisy single-night data.
5. **Honest, non-medical framing.** Phrase everything as trends and suggestions, never diagnosis.

## 8. Feature requirements

Priority: **P0** = MVP, must ship. **P1** = v1.0. **P2** = later.

### 8.1 Data acquisition and sync
- **P0** Background sync via WorkManager, with a guaranteed morning window (so the night is ready when the wearer wakes) plus a midday/evening top-up — 2-3 syncs/day pulling overnight HR, sleep, SpO2, and steps since last sync.
- **P0** Overnight HR logging configured dense enough to locate the nightly RHR low and its timing (needed for the §10.2 recovery index); set the ring's logging interval during pairing and confirm during the §16 packet capture.
- **P0** Robust BLE connect/retry with automatic reconnection; survive Android Doze (battery-optimization exemption).
- **P0** One-time pairing flow (performed by maintainer).
- **P1** Opportunistic refresh: trigger a sync when the ring comes back into BLE range (proximity-based), so a missed window self-heals without waiting for the next scheduled run.
- **P1** Catch-up sync: after any missed or failed window (out of range, ring dead, Doze deferral), backfill all gap data on the next successful connect; never lose a night.
- **P1** Adaptive cadence with exponential backoff on repeated failures, and a short foreground service during an active sync so a transfer in progress survives Doze.
- **P1** Manual "refresh now" affordance (hidden/secondary, for the maintainer).

### 8.2 The three scores
- **P0** Sleep Score (0-100), computed overnight.
- **P0** Recovery Score (0-100), Daybreak's Readiness analog.
- **P0** Activity Score (0-100).
- **P0** Per-score contributor breakdown available on tap (for the maintainer; primary user can ignore).
- **P0** Calibration guard: suppress/flag scores until baselines stabilize, so the MVP's first ~14 nights never surface a misleading number.
- **P1** 14-night baseline warmup *display* with a richer "calibrating" state and progress indication.

### 8.3 Daily summary
- **P0** Rule-based plain-language summary sentence generated from the three scores and their main drivers.
- **P2** Optional on-device/local LLM-generated summary (ties into your prior VisionClaw/Gemini work). Off by default; rule-based remains the fallback.

### 8.4 Home screen and history
- **P0** Single home screen: today's three scores + summary sentence + last-updated line.
- **P1** Seven-day trend sparkline under each score on the home screen (glanceable; tap opens the Insights screen, §8.6).
- **P2** Tag/annotation ("slept poorly," "late dinner") to correlate with scores; surfaced as markers on the Insights charts.

### 8.5 Onboarding and notifications
- **P0** Maintainer onboarding: pair ring, grant Bluetooth/nearby-devices/location permissions, set battery exemption, set the wearer's age/sleep-need.
- **P1** One gentle morning notification: "Your night is ready." Nothing else.

### 8.6 Detailed insights (charts & graphs)
A dedicated, scrollable Insights screen reached from the home screen (tap a score or an "Insights" affordance). It is the data-rich counterpart to the glanceable home screen and the home of every graph. Default to the **30-day** range with a 7 / 30 / 90-day selector. Every chart has a text/value fallback for accessibility and degrades gracefully when a signal (e.g. HRV) is absent.

- **P1** Score trends: line chart of Sleep, Recovery, and Activity over the selected range, with the personal baseline band shaded and the calibration period marked.
- **P1** Sleep detail: last-night **hypnogram** (deep/light/REM/awake timeline); trends for total sleep vs need, efficiency, deep- and REM-minutes, latency, and bedtime/wake-time regularity.
- **P1** Recovery detail: trends for resting HR and HRV (HRV panel hidden when unavailable); the **overnight HR curve** with the nightly low and its timing marked (the recovery-index visual, §10.2).
- **P1** Activity detail: steps-vs-goal bar chart, active-minutes trend, and longest-sedentary-stretch trend.
- **P1** Per-score **contributor breakdown** (the maintainer view from §8.2) shown as horizontal bars with each contributor's normalized value and weight.
- **P2** SpO2 overnight range chart (secondary signal, §6).
- **P2** Correlation/marker overlays from tags (§8.4) on any trend chart.
- **P2** Tap any data point for that day's raw values; long-press a chart to export its data as CSV (local-only, §13).

## 9. UX and screen specs

**Home (the only screen the wearer needs).** Top to bottom: large Recovery Score with a one-word label (e.g., "Recovered" / "Take it easy"); beneath it, Sleep and Activity as two smaller dials; below that, the plain-language summary sentence; a faint "Updated 7:14 AM" line at the bottom. Big type, high contrast, color-coded (green/amber/red) with a non-color cue (icon or label) for accessibility. No menus, no tabs visible to the wearer.

**Insights (charts & graphs, reached by tapping a score or the "Insights" affordance on home).** A scrollable, data-rich screen (spec'd in §8.6). Top: a 7 / 30 / 90-day range selector. Then, grouped by score: the combined score-trend line chart with a shaded baseline band; the sleep section (last-night hypnogram + duration/efficiency/stage trends); the recovery section (resting-HR and HRV trends + the overnight HR curve with the nightly low marked); the activity section (steps-vs-goal bars + active-minutes and sedentary trends); and each score's contributor-breakdown bars. Charts use clear axes and labels, color with non-color cues, and a value/text fallback per chart; absent signals (e.g. HRV) hide their panel rather than showing an empty axis. This screen is for the maintainer and the data-curious — it intentionally trades the home screen's minimalism for depth.

**Settings (from Insights).** Permissions, battery exemption, baseline reset, summary mode (rule-based / LLM), wearer age & sleep-need, sync log, and local CSV export.

## 10. Scoring model (open, on-device, baseline-relative)

All scores are 0-100 weighted sums of normalized contributors, each contributor scored against the wearer's own rolling baseline. A 14-night warmup establishes baselines; before then the app shows "Calibrating" rather than a possibly-misleading number. This mirrors Oura's personal-baseline philosophy without copying its weights.

### 10.1 Sleep Score
| Contributor | Weight | Source |
|---|---|---|
| Total sleep vs need (age-based 7-9h target) | 25% | Sleep stages sum |
| Sleep efficiency (asleep / time in bed) | 15% | Sleep + wake |
| Deep sleep proportion vs baseline | 15% | Stages |
| REM proportion vs baseline | 15% | Stages |
| Restfulness (wake events, movement) | 10% | Accelerometer + wake |
| Timing regularity (bed/wake vs personal pattern) | 10% | Sleep window |
| Sleep latency (time to fall asleep) | 10% | Sleep onset |

Caveat: deep/REM split inherits the ring's staging accuracy, which is below Oura's. Weight these as directional, not precise.

### 10.2 Recovery Score (Readiness analog)
| Contributor | Weight | Fallback if missing |
|---|---|---|
| Previous-night Sleep Score | 30% | — |
| Resting HR vs 14-day baseline (lower = better) | 25% | — |
| HRV balance vs baseline | 20% | **If HRV unavailable: +10% to RHR (→35%) and +10% to previous-night Sleep (→40%), total still 100%** |
| Recovery index (how early in the night RHR hit its low) | 15% | — |
| Prior-day activity load (overreaching lowers it) | 10% | — |

This is the score most affected by hardware limits. With no temperature contributor and possibly no HRV, recovery leans on RHR trend, sleep, and recovery-index timing, which on their own still carry real recovery signal. The model must explicitly handle the HRV-absent case rather than zero-filling it.

### 10.3 Activity Score
| Contributor | Weight | Source |
|---|---|---|
| Steps vs personalized goal (goal lowers on low-recovery days) | 35% | Steps |
| Active minutes | 25% | Accelerometer |
| Sedentary penalty (long inactive stretches) | 20% | Accelerometer |
| Activity balance vs recent baseline (not too little/much) | 20% | History |

### 10.4 Daily summary generation (rule-based)
Template selection keyed off the three scores and their dominant driver. Example logic: if Recovery < 60 and the main driver is elevated RHR, output a "your heart rate stayed up last night, consider an easy day" message; if Sleep < 60 driven by short duration, output a "you slept less than usual" message; otherwise a positive default. Keep the library small, warm, and non-clinical.

## 11. Data model and storage

Local SQLite via Room. Suggested tables: `nightly_sleep` (date, stages, efficiency, latency, score), `hr_samples` (timestamp, bpm), `daily_activity` (date, steps, active_min, score), `recovery` (date, score, contributors json), `baselines` (metric, rolling values), `sync_log` (timestamp, result). No PII beyond age and sleep-need. Nothing leaves the device.

## 12. Technical architecture (summary)

Native Kotlin app, per the build plan already underway: Nordic Android BLE library for the connection state machine; a ported protocol layer (16-byte packets, checksum = sum of first 15 bytes mod 256 (`sum & 0xFF`); sleep over the 0xBC big-data characteristic, reassembled; HR/steps/battery over the standard RX/TX). Room for storage, WorkManager for scheduled sync, Jetpack Compose for UI. For the Insights charts (§8.6 / §9), use a Compose-native charting library (e.g. Vico) or hand-drawn `Canvas` for the bespoke hypnogram and overnight-HR curve; keep chart inputs as plain data emitted by the scoring/repository layer so charts stay testable and offline. Gadgetbridge's Colmi parser and the tahnok Python client are the protocol references. The scoring engine is a pure-Kotlin module with no Android dependencies (unit-testable, portable).

> **Note:** Checksum is the low byte of the sum of the first 15 bytes (mod 256). Verify against Gadgetbridge's `ColmiR0x...PacketHandler` during implementation, as this constant is correctness-critical.

## 13. Privacy and safety

- 100% local. No network permission required in v1.
- Not a medical device. No diagnosis, no treatment guidance. All output framed as wellness trends.
- No alarming language. A low Recovery Score suggests rest, it does not warn of illness.
- Data export is local-only (e.g., share a CSV — a P2 capability, see §15), never automatic.

## 14. Success metrics

Because this is a one-user product, success is qualitative and behavioral, not analytics-driven:
1. The wearer checks the app unprompted most mornings.
2. He can correctly state, without help, whether today is a "go" or "rest" day.
3. Zero maintenance events per month after setup (no re-pairing, no permission re-grants).
4. Sync succeeds on >90% of mornings.
5. The wearer keeps wearing the ring after 30 days.

## 15. Phased roadmap

- **MVP (P0):** Pairing + background sync + the three scores + calibration guard + rule-based summary + single home screen. Sleep, RHR, steps wired end to end. HRV best-effort, temperature omitted.
- **v1.0 (P1):** Baseline warmup display, opportunistic/catch-up sync, home-screen trend sparklines, morning notification, and the Insights screen (§8.6) — score trends, sleep hypnogram, overnight HR curve, activity charts, and contributor breakdowns.
- **Later (P2):** Tags/annotations (with chart markers), optional LLM summaries, SpO2 chart, per-point drill-down, CSV export, second wearer support.

## 16. Risks and open questions

1. **HRV availability.** May be absent or coarse on this firmware. *Mitigation:* recovery model degrades gracefully; validate against the actual R09 early.
2. **Sleep-staging accuracy.** Cheap-ring staging is noisy. *Mitigation:* weight stage-split contributors as directional; lean on duration and efficiency.
3. **No temperature.** Removes Oura's illness/cycle headline features. *Accepted, documented as a non-goal.*
4. **Connection reliability and battery.** Small battery, tiny BLE range. *Mitigation:* short, infrequent connects; reconnection logic; battery-optimization exemption. *Note:* the §8.1 cadence (morning + midday/evening top-ups, plus proximity-triggered and catch-up syncs) increases connect frequency and will draw more from the R09's 2-4 day battery. Keep each connect short (delta-only transfers since last sync), cap proximity-triggered syncs with a minimum interval so re-entering range doesn't thrash the radio, and treat the cadence as a tunable to balance freshness against ring battery life — validate the real drain during the early packet capture.
5. **Scoring trust.** Our open weights are not clinically validated. *Mitigation:* honest framing; tune against the wearer's subjective morning feel over the first weeks.
6. **Open:** Does the R09 expose RR-intervals at all? Determines whether HRV is even possible. Resolve with a packet capture before committing to the 20% HRV weight.

## 17. Out of scope (explicit)

Temperature, cycle/fertility, glucose, VO2 max, workout auto-detection, GPS, respiratory rate, cloud sync, accounts, web app, social, and any medical claim.

## Appendix: glossary

- **Baseline:** the wearer's own rolling average for a metric (14 or 28 nights), used to normalize scores.
- **Recovery index:** how early in the night resting HR reaches its nightly low; earlier suggests better recovery.
- **Big-data characteristic:** the COLMI BLE channel (`de5bf729-...`) carrying multi-packet payloads like full-day sleep.
