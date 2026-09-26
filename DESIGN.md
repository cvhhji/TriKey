---
version: alpha
name: TriKey
description: Compact Android hardware-shortcut settings with a restrained cobalt accent and clear gesture hierarchy.
colors:
  primary: "#3568D4"
  on-primary: "#FFFFFF"
  page-background: "#F5F6F8"
  card-background: "#FFFFFF"
  field-background: "#F8F9FB"
  outline: "#E2E5EA"
  text-primary: "#17191D"
  text-secondary: "#5D626B"
  text-tertiary: "#68707C"
  activation-active-background: "#EAF5ED"
  activation-active-border: "#B9DDC0"
  activation-active-text: "#1F6B37"
  activation-inactive-background: "#FFF4E5"
  activation-inactive-border: "#F2CE97"
  activation-inactive-text: "#8A5200"
typography:
  sans:
    fontFamily: "Android system default (Typeface.DEFAULT) with OEM CJK fallback"
  mono:
    fontFamily: "monospace"
rounded:
  field: "10dp"
  card: "16dp"
  brand-mark: "14dp"
spacing:
  page-horizontal: "18dp"
  card-inset: "16dp"
  section-gap: "14dp"
components:
  button: {}
  card: {}
  input: {}
  select: {}
---

# TriKey Design System

## Overview

### Creative North Star

TriKey is a small hardware control panel: a physical key has three deliberate press patterns, each mapped to one outcome. The interface should read like clear device settings, not a launcher or a marketing page. A quiet cobalt accent carries the one primary save action; compact form sections make the configuration easy to scan.

### Product context and register

- **Audience and primary job:** Android users configuring a system shortcut module to map one physical key to single, double, and long presses.
- **Target market(s) and evidence:** Android devices with a configurable shortcut key; repository actions include ColorOS-specific applications and an AOSP policy fallback.
- **Locale(s) and language policy:** The current interface is Simplified Chinese. Android's system sans family provides CJK fallback; additional locales are not implemented.
- **Usage scene:** A phone settings screen used occasionally, often while testing a physical key. The screen is a vertical, scrollable form with no persistent navigation.
- **Register:** Direct system utility. Labels name settings and outcomes without promotional copy.
- **Memorable signature:** Three compact gesture sections pair each press pattern with an explicit action selector.
- **Restraint:** Preserve native Android control behavior and platform-owned selector menus; avoid decorative gradients, shadows, and motion.
- **Anti-references:** Launcher grids, gamer dashboards, and generic marketing hero layouts obscure the settings task and are not used.
- **Token ownership/runtime mapping:** Model B. Android color resources in `app/src/main/res/values/colors.xml` and `values-night/colors.xml` are canonical; this document mirrors them. `MainActivity.java` consumes those resources and owns layout dimensions. Review this mapping when either runtime palette changes.

## Colors

The light theme uses cobalt `primary` (#3568D4) for the app mark, save action, and Android accent. White cards sit on a cool-gray page; outlined fields remain distinct without elevation. Text uses three readable levels, with tertiary text set to #68707C for small helper copy. The night palette is supplied by `values-night/colors.xml` and keeps the same semantic roles with lighter primary and text colors on charcoal surfaces. System focus and selected states use Android's native control behavior.

## Typography

Use Android's default system typeface for all app-owned text and allow the device's normal CJK fallback and user font settings to apply. Do not load a font file or override the system family. The app name is the strongest page heading, section titles are medium-sized and bold, and body/helper text is kept to concise Chinese sentences. Technical values such as key codes and milliseconds remain plain text fields; no custom numeric font is required.

## Layout

The page is one natural-height `ScrollView`; it does not fix content to the viewport. The compact app identity header is followed by a full-width module activation verification card, then the editable settings sections. The verification card aligns to the same 18dp page inset as the form and displays only the concise activation state; it checks automatically and is not interactive. Section inset is 16dp, and major sections are separated by 14dp. Gesture selectors and important toggles have at least 48dp height. The save action stays in document flow after all gesture settings so short screens remain scrollable.

## Elevation & Depth

Hierarchy comes from page/card surface contrast and a 1dp outline rather than shadows. Avoid floating or sticky surfaces; they would compete with the settings form and can cover controls while the keyboard is open.

## Shapes

Settings cards use a 16dp radius, the activation status card uses an 18dp radius, fields use a 10dp radius, and the compact TriKey mark uses 14dp. Fields retain a fine outline in light and dark themes. Avoid pill-shaped containers for ordinary settings.

## Components

### Foundational visual states

Android owns pressed, focused, checked, and disabled semantics for CheckBox, Switch, Spinner, EditText, and Button. The custom save background changes surface shape and color but keeps the control as a native Button. Module activation is verified from the running system target and loaded version; status is expressed with text as well as color. App-owned text follows the platform's current default typeface.

### Buttons and actions

There is one primary action, “保存设置”. Native selectors keep platform popup behavior and system typography with readable field contrast. Gesture names are visible above their action selectors, and custom values gain a visible label when the corresponding custom action is selected.

### Navigation and data display

The screen has no route navigation, lists, or tables. The top identity block presents the app name, followed by the live module activation state.

### Forms and overlays

Settings remain in one scrollable form. Native Switch and CheckBox rows use a 48dp minimum touch height. Numeric inputs have visible labels; custom app and Intent fields expose a visible label and an accessible name. Screen-off wake and secure system authentication are always-on behavior, not a setting and not a dedicated explanatory card. The activation card shows only “已激活” or “未激活”, updates automatically, and has no click action. No modal or custom popup is introduced.

### Iconography

No icon set is used in the settings form. The letter mark is a text label; every important action remains labeled in text.

### Motion

Use native state transitions only. Do not animate layout changes; expanding a custom parameter field should preserve the current control state and remain reachable by scrolling.

### Content and data visualization

Use concise Simplified Chinese copy and explicit action names. Key codes and durations use Arabic numerals with units in field labels. No data visualization is present.

## Do's and Don'ts

- **Do:** Keep the selector menus native and keyboard/touch operable.
- **Do:** Keep secure lock-screen authentication system-owned; document behavior outside the settings form.
- **Don't:** Imply that TriKey bypasses or supplies lock-screen credentials.
- **Don't:** Hide control labels in placeholders or rely on color alone for module status.
