---
name: Gloss
description: Japanese game magazines, with the English one tap away.
colors:
  lapis: "#2338C2"
  lapis-night: "#5068FF"
  lapis-deep: "#141E78"
  lapis-tint: "#E4E7F9"
  lapis-tint-night: "#1B2358"
  gold-leaf-hi: "#F6D886"
  gold-leaf: "#E3B341"
  gold-leaf-lo: "#B98320"
  gold: "#DDA623"
  gold-night: "#F2C14E"
  gold-ink: "#8A6300"
  vermilion: "#D5412A"
  vermilion-night: "#FF6B4F"
  danger: "#D12F3A"
  ground: "#F2F3F8"
  surface: "#FFFFFF"
  fill: "#E8EAF2"
  separator: "#D5D8E4"
  text: "#0B0D1C"
  secondary: "#5B5F7E"
  tertiary: "#8D91AB"
  ground-night: "#070A1C"
  surface-night: "#11142A"
  raised-night: "#181C37"
  fill-night: "#1F2446"
  separator-night: "#292E52"
  text-night: "#EEF0FA"
  secondary-night: "#9DA2C6"
  pigment-malachite: "#1E8F5E"
  pigment-orpiment: "#E1A91A"
  pigment-murex: "#8A2D8F"
  pigment-azurite: "#2F7FD1"
  pigment-minium: "#E0662D"
  pigment-verdigris: "#2A9D8F"
  pigment-madder: "#C23152"
typography:
  large-title:
    fontFamily: "Inter, opsz 32"
    fontSize: "34sp"
    fontWeight: 800
    lineHeight: "41sp"
    letterSpacing: "-0.022em"
  title1:
    fontFamily: "Inter, opsz 32"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "34sp"
    letterSpacing: "-0.02em"
  title2:
    fontFamily: "Inter, opsz 32"
    fontSize: "22sp"
    fontWeight: 700
    lineHeight: "28sp"
  headline:
    fontFamily: "Inter, opsz 14"
    fontSize: "17sp"
    fontWeight: 600
    lineHeight: "22sp"
  body:
    fontFamily: "Inter, opsz 14"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "24sp"
  subhead:
    fontFamily: "Inter, opsz 14"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "20sp"
  footnote:
    fontFamily: "Inter, opsz 14"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
  caption:
    fontFamily: "Inter, opsz 14"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
  gloss-display:
    fontFamily: "Instrument Serif Italic"
    fontSize: "44sp"
    lineHeight: "46sp"
  gloss-title:
    fontFamily: "Instrument Serif Italic"
    fontSize: "26sp"
    lineHeight: "30sp"
rounded:
  cover: "6dp"
  cell-group: "12dp"
  popover: "16dp"
  callout: "18dp"
  sheet: "22dp"
  pill: "999dp"
spacing:
  gutter: "20dp"
  section: "26dp"
  cell-x: "16dp"
components:
  pill-filled:
    backgroundColor: "{colors.lapis}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: "50dp"
  pill-tinted:
    backgroundColor: "{colors.lapis-tint}"
    textColor: "{colors.lapis}"
    rounded: "{rounded.pill}"
  gloss-callout:
    backgroundColor: "#141832F2"
    textColor: "#FFFFFF"
    typography: "{typography.body}"
    rounded: "{rounded.callout}"
    padding: "14dp 16dp"
  tab-bar:
    rounded: "{rounded.pill}"
    height: "62dp"
  cell:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    padding: "11dp 16dp"
---

# Design System: Gloss

## Overview

**Creative North Star: Illuminated gloss.** A translation is a gloss, written between the lines of the original. The scan is always the hero; English arrives as gold-leaf annotation laid on the page itself, never as a separate screen you switch to.

The shell is a calm, familiar iOS-style app: large collapsing titles, frosted bars, a floating capsule tab bar, inset grouped lists, springs on every touch. The identity comes from a manuscript illuminator's palette applied with restraint: lapis owns the shell, gold leaf marks anything glossed, vermilion marks the reader's own marks.

Anti-references: a grey utility reader with a "Translation" tab; anything that reads as Japanese pastiche (the brief asked for less of that); Material ripples and FABs.

## Colors

- **Lapis** (`#2338C2`, night `#5068FF`) is the shell's one strong colour: primary pills, links, the Continue Reading card, the app icon. Lapis tint is for tinted buttons and selected rows.
- **Gold leaf** is reserved for glossing: the sweep under a tapped box, search highlights (`goldWash`), selected passages in the sheet, page numbers in the scribe's hand, the wordmark's bar. It is always a gradient (`gold-leaf-hi → gold-leaf → gold-leaf-lo`) when it is a line or fill; flat `gold-ink` only when it has to carry text on a light ground.
- **Vermilion** is the rubric: bookmarks and "new". Never used for errors (that's `danger`).
- **Grounds** are cool lapis-tinted grey in light and lapis-black in dark, never pure grey or pure black. The reader is always dark, whatever the app theme, so scans sit on near-black.
- **Pigments** give each magazine series its own colour, chosen by name hash (`Pigment.forSeries`). They tint cover placeholders, chips and the issue header wash, and nothing else.

## Typography

Inter (variable, optical size axis) sets the interface on an iOS-like scale: `opsz 32` for titles, `opsz 14` for text. Instrument Serif Italic is the scribe's hand: the wordmark, page numbers, "p. 12 of 180", the year on covers. It never sets UI labels or body copy. Japanese falls back to the system CJK font.

Large system font sizes must keep working: no fixed-height text containers, and every row grows.

## Layout

20dp page gutters; grouped sections separated by 26dp with uppercase-free footnote headers. Lists are inset grouped cells (12dp radius group, hairline separators inset from the leading edge). Grids of covers size from the screen width (`screenWidthDp / 128` columns). The floating tab bar reserves `TabBarClearance` at the bottom of every home tab.

In the reader, the page fills the screen; chrome sits over it and hides with a tap. Landscape with spreads on shows facing pages meeting at the gutter, ordered by reading direction.

## Elevation & Depth

Depth comes from material, not shadow. Bars, the tab bar, reader controls and the reader sheet are frosted glass (Haze, `CupertinoMaterials.regular`) with a 0.6dp light edge. Real shadows appear in two places only: covers (`coverShadow`) and the gloss callout, both because they are physical objects lifted off the page.

## Shapes

Capsules for anything you press (pills, tab bar, reader title capsule, round icon buttons). Covers are 6dp, close to a real magazine's corner. The callout is 18dp and the reader sheet 22dp at the top. Gold leaf bars are skewed parallelograms that lean like the italic.

## Components

- **Pressable** (`Modifier.pressable`): no ripple; the element scales to ~0.96 on a `Motion.press()` spring. Rows grey out like table cells (`rowPress`).
- **PillButton**: Filled (lapis), Tinted (lapis tint), Plain. The issue screen's Download pill morphs into a progress ring with a stop square, then into Read.
- **SegmentedControl**: a thumb that springs between segments.
- **GlassTabBar**: Discover and Library in a frosted capsule, Search as a separate round button.
- **LargeTitleScreen**: the iOS large title that scrolls under a frosted bar where a small centred title fades in.
- **IssueCover / PigmentCover**: scans' first pages, or a pigment gradient with masthead and the year in the scribe's hand. Covers fly into the issue screen as a shared element.
- **Gloss callout** (the signature): tapping a box sweeps gold leaf under it left to right, the box fills with a gold wash, and a dark card with the English (and the Japanese, smaller) grows out of it, placed below the box when there's room and above otherwise. It stays crisp at any zoom and opens the full page on tap.

## Motion

Springs everywhere, nothing linear, nothing that bounces twice (`Motion.press / move / settle / offset`). Navigation is an iOS push with a parallax underlay; the reader opens with a fade and scale. When the system animator scale is 0, animations resolve instantly.

## Do's and Don'ts

- Do keep gold for glossing only. If something isn't a translation, highlight or page number, it isn't gold.
- Do let the scan lead: hide chrome, never cover the selected box with the callout.
- Do say plainly that translations are machine-made.
- Don't add Material ripples, FABs or elevation shadows on cards.
- Don't use Japanese decorative motifs, seals or brush textures as ornament.
- Don't set UI labels in Instrument Serif.
