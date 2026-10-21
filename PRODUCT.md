# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
People who love Japanese video-game history and can't read Japanese: retro collectors, translators and researchers, and fans who grew up on these consoles. They read on a phone, often one-handed in bed or on the commute, in sessions that range from a five-minute flick through ads to an hour inside a strategy guide. (Inferred from the brief and the existing app; not interviewed.)

## Product Purpose
Gloss is an open-source Android reader for Gaming Alexandria's archive of scanned Japanese game magazines (Famitsu, Beep, LOGiN and more) with AI transcriptions and English translations. You browse the catalog, download an issue, and read the original scan with its translation one tap away. Success: someone opens a 1994 Famitsu and understands a page within seconds of tapping it.

## Positioning
The scan stays the hero. Translation is a gloss laid over the original rather than a replacement for it: tap any text on the page and its English appears beside it, or paint English into every box at once. Search works across English and Japanese inside every downloaded issue, including mid-sentence Japanese.

## Operating Context
- Data comes from the same catalog and per-issue files as Gaming Alexandria's desktop app, Rosetta Magazine Researcher. Scans are PDFs, some hosted on Mega.
- Issues are read offline after download; downloads can be large (hundreds of MB).
- Distributed as a sideloaded APK from GitHub Releases; no Play Store.

## Capabilities and Constraints
- Kotlin + Jetpack Compose, minSdk 33, single-activity.
- Reader: paged scans, tap-to-translate, manga mode (English set into each box), zoom, RTL paging, text-only installs, bookmarks, continue reading, text size.
- Search: on-device index, CJK bigrams, phrases, exclusions, prefixes.
- Translations are AI-generated and can be wrong; the app says so.
- No accounts, no analytics, no ads.

## Brand Commitments
- Name: Gloss. Chosen by the owner over Back Issue, Spread and Folio for the double meaning: a glossy magazine page, and a gloss (a translation note).
- Owner's brief: less Japanese-coded than the previous private build, more complete, animated, coherent identity, iOS-like feel.
- Credit Gaming Alexandria clearly; never imply affiliation.

## Evidence on Hand
Real catalog data and scans are fetched at runtime. There are no screenshots, testimonials or user numbers; do not fabricate any.

## Product Principles
1. The page leads. Chrome gets out of the way while reading.
2. Translation is one tap, never a mode switch you have to hunt for.
3. Quick and no-nonsense: no onboarding walls, no accounts, nothing between launch and the last page you read.
4. Offline is the normal case, not an error state.
5. Honest about machine translation.

## Accessibility & Inclusion
Large system font sizes must not break layouts; touch targets at least 48dp; respect reduced-motion (animator duration scale 0).
