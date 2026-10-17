# Playback sources and health proposal

Status: research commissioned on 2026-10-17. Provider selection and
implementation are pending. This proposal does not close gate 0.

## Research home

The dedicated "Research and maintain SaikouTV2 sources" chat belongs to
Saikou TV Revival. Its inventory, reports, captures and isolated experiments
belong in `C:\Users\Windows\Documents\SaikouTV2-sources`, outside the app checkout.

Inventory all applicable anime streaming entries from
[Wotaku](https://wotaku.wiki/websites#anime) and
[EverythingMoe](https://everythingmoe.com/), including collapsed entries.
Distinguish scraped/aggregated streaming sites from licensed storefronts,
torrents/downloads and unrelated categories. Deduplicate aliases/mirrors and
record directory provenance and observed canonical domains. Listing rank is
not integration evidence.

Target about ten qualified sources. Evaluate catalogue coverage, sub/dub,
tracks, quality/bitrate, latency, reliability, common dependencies and
maintenance burden. Unknown scores stay unknown. Report fewer qualifying
sources if necessary rather than claiming ten integrations work.

Date observations and record region/network vantage, adapter revision,
representative titles/episodes, coverage limits, failures and evidence.
Keep catalogue, extraction, media and native-player results separate.
No Android application exists yet; native playback has not been tested.

## Legacy observations

At commit `6bd86354278d4c7816658361dbfdbd311f42538b`:

- [AnimeSources.kt](https://github.com/kemuriXD/SaikouTV/blob/6bd86354278d4c7816658361dbfdbd311f42538b/app/src/main/java/ani/saikou/parsers/AnimeSources.kt)
  enables AnimePahe and Gogo in the ordinary anime list. Other parsers being
  present in the tree does not establish that they are enabled.
- [AnimeParser.kt](https://github.com/kemuriXD/SaikouTV/blob/6bd86354278d4c7816658361dbfdbd311f42538b/app/src/main/java/ani/saikou/parsers/AnimeParser.kt)
  separates episode listing, server discovery and extractor selection.
- [VideoExtractor.kt](https://github.com/kemuriXD/SaikouTV/blob/6bd86354278d4c7816658361dbfdbd311f42538b/app/src/main/java/ani/saikou/parsers/VideoExtractor.kt)
  returns videos and subtitles rather than a single URL.
- [AnimePahe.kt](https://github.com/kemuriXD/SaikouTV/blob/6bd86354278d4c7816658361dbfdbd311f42538b/app/src/main/java/ani/saikou/parsers/anime/AnimePahe.kt)
  depends on page patterns, redirects and a session/form exchange.
- [GogoCDN.kt](https://github.com/kemuriXD/SaikouTV/blob/6bd86354278d4c7816658361dbfdbd311f42538b/app/src/main/java/ani/saikou/parsers/anime/extractors/GogoCDN.kt)
  uses hard-coded decryption parameters and a media Referer.

These are static observations, not current compatibility results. Compare
current public player requests before implementing adapters. REA can establish
browser/network/runtime evidence; ordinary tools suffice for source inspection.
Distinguish URL obfuscation from media encryption/DRM. Use supported, authorised
player paths; record login, region, challenge or protection barriers instead
of treating access-control bypass as a resolver feature. Legacy reuse still
requires the open licence decision and provenance review.

## Proposed integration contract

Separate site adapters (search/title/episodes/servers), host resolvers, playback
coordination, and the native player. Multiple sites may share a resolver or
upstream host. Keep identifiers, errors, fixtures and contracts independent of
Android UI objects. Code-sharing choices for Apple TV remain deferred.

A resolver returns a typed playback descriptor, not just a URL:

| Area | Information |
| --- | --- |
| Identity | Provider, title/edition, episode and server IDs; language/sub/dub mapping and match confidence |
| Media | URI candidates, protocol/MIME, variants, bitrate/quality and codecs where known |
| Tracks | Audio/subtitle languages, formats, embedded/external tracks and defaults |
| Requests | Required headers/session context, allowed origins and redirect policy, including manifest/segment/subtitle requests |
| Freshness | Resolution time, known expiry, refresh strategy and observed session/IP binding; unknown expiry stays unknown |
| Outcome | Structured error, retry guidance and adapter/host versions |

Resolve near playback time. Persist episode identity/progress, not a signed
media URL as permanent identity. Keep tokens and signed URLs out of logs,
fixtures and status feeds. A checker must not distribute its own session-bound
stream URLs to viewers.

[Media3 support](https://developer.android.com/media/media3/exoplayer/supported-formats)
depends on protocol, container, codecs and device decoders.
[Request customization](https://developer.android.com/media/media3/exoplayer/customization)
can supply per-request headers and URI resolution. Scope credentials to known
origins rather than blindly forwarding them across redirects/CDNs. Media3
remains the proposed Android player until the foundation decision is accepted.

## Health checks

Recommend a hybrid design: deterministic adapter tests, low-rate live checks
when a service is introduced, and local results from actual playback attempts.
The app remains usable if the central status service is unavailable.

| Layer | What a pass establishes | Remaining uncertainty |
| --- | --- | --- |
| Offline fixture/contract | Parser, decoder and mapping match sanitised examples | Live site may have changed |
| Catalogue canary | Known search and episode identities are correct | Host/media may fail |
| Resolution canary | Sample episode produces a valid descriptor | Another session/IP/device may fail |
| Media probe | Bounded manifest and sample/init/segment reads contain plausible media | Full playback, seeking and tracks unproven |
| Native player canary | Tested app/device plays and exercises sampled media | Other titles, codecs, regions and hardware unproven |

HTTP 200 can contain an HTML error/challenge. Validate content, not just status;
do not rely solely on HEAD. Use bounded reads and request/byte/time limits,
safe public destinations, backoff/jitter and Retry-After. Do not download whole
episodes for probes. Blocked/challenged checks are distinct from dead sources.

An observation includes capability, provider/host, network/region, app/adapter
version, sample coverage, timestamp, expiry, outcome and confidence. Track
healthy, degraded, failing, blocked and unknown separately from an operator's
enabled/disabled override. Expired evidence becomes unknown. A local network
failure or one unavailable title must not disable an entire provider globally.

Require repeated evidence for failure and recovery. Choose thresholds,
freshness and cadence using measurements and operating costs; no numeric
defaults are accepted yet. Track common APIs/players/hosts/CDNs so ten sites
sharing one host are not counted as independent fallbacks.

If a central service is approved, publish a small signed/versioned status
document with expiry, compatible provider/adapter IDs and rollback. It carries
data, not executable extractor code, secrets or cached stream URLs. Define
trust-key provisioning/rotation before deployment. Reject invalid/incompatible
updates and keep usable last-known-good configuration. Stale health cannot
permanently disable everything. Emergency disabling needs separate scope,
reason, owner, expiry/review and recovery rules.

## Fallback and user experience

Prefer the user's compatible source when usable. Rank alternatives by fresh
capability evidence, performance and dependency independence. Try another
suitable variant/server, then an equivalent provider, within one total
deadline/attempt budget. Coordinate resolver and player retries so nested
policies cannot multiply attempts. Cancellation/Back stops pending work.

Classify mapping ambiguity, not-found, parser change, unsupported media,
expired URL, denial/challenge, rate limit, network and upstream failures.
An expired URL may justify one fresh resolution; it does not prove a site died.

Cross-provider fallback must verify title, season, episode/special, edition
and audio language. Preserve time/track preferences only for compatible cuts.
Do not silently switch episodes or dub based on display number. Ask the viewer
to select when equivalence is uncertain. Provide a readable failure reason,
manual source chooser and predictable remote focus after bounded attempts.

Proposed app area: source capabilities, readable health, last checked time,
optional refresh and manual selection. Maintainer tooling adds adapter/host
versions, sample coverage, regional failures, fixture results, owner and
fix/rollback state. Keep parser internals out of the normal viewing flow.

## Maintenance and verification

Keep live probes separate from deterministic PR checks. Upstream outages
produce source incidents, not flaky application CI. Parser fixes need a
sanitised regression fixture, focused checks, reviewed adapter version and
relevant playback evidence before re-enabling a path. Initially distribute
adapter code through app releases; a plugin update system is a separate
architecture/security decision. Define privacy rules before central telemetry;
do not send credentials, watch history or raw stream URLs.

All cases below are **Not run**. Use the commit/device/result template in
[testing](testing.md); distinguish emulator and physical-device evidence.

| Case | Required evidence |
| --- | --- |
| SRC-001 | Full applicable directory inventory, mirror deduplication, exclusions, provenance and bounded coverage |
| SRC-002 | Search/title/edition/episode mapping, including specials and sub/dub |
| SRC-003 | Resolution across at least two episodes from different titles; classified errors, cancellation and request limits |
| SRC-004 | Manifest/container/codec, scoped headers and fresh versus expired/session-bound URL behaviour |
| SRC-005 | Native TV start, pause, seek, tracks, subtitles, resume and failure recovery on supported hardware |
| SRC-006 | Malformed/changed responses produce safe errors; fixtures prevent confirmed regressions |
| SRC-007 | Local outage, regional block, rate limit and shared-host failure do not globally misclassify healthy paths |
| SRC-008 | Stale/invalid/incompatible updates, unavailable status service, repeated failure/recovery and disable rollback |
| SRC-009 | Bounded fallback preserves verified episode/language/timeline and offers a usable remote source chooser |
| SRC-010 | Logs, fixtures, status and telemetry do not leak tokens, signed URLs, unrelated cookies or viewing history |

## Sequence and open decisions

1. Gate 0: inventory, evidence-backed shortlist, content/format/account boundaries,
   licence/provenance review and accepted provider contract.
2. Gates 1-2: fixture-backed native TV playback proof and deterministic failure
   recovery; settle descriptor/error contracts before live integration.
3. Gate 3: one researched live provider end to end, then an independent fallback;
   add measured health decisions and grow toward the qualified set.
4. Gate 4: physical Android TV and confirmed Android-based Fire TV evidence,
   repeated source regression runs, upgrades and outage drills.
5. Gate 5: operating ownership, privacy, status signing/update/rollback rules,
   deployment/cost and monitoring budgets plus release evidence.

Provider selection, health architecture, hosting, operator, cadence and code
update model are still open. No checker service, automatic probe schedule,
deployment or ongoing monitoring has been created. Research may continue
alongside gate 0; production integration waits for the relevant gate.
