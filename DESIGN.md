---
version: alpha
name: TriKey
description: Compact Android hardware-shortcut settings with muted sage tones and clear gesture hierarchy.
colors:
  primary: "#51625C"
  on-primary: "#FFFFFF"
  primary-container: "#D4E7DF"
  on-primary-container: "#0F1E1A"
  warning: "#98621E"
  on-warning: "#FFFFFF"
  warning-container: "#F5E7D2"
  on-warning-container: "#4E361A"
  error: "#BA1A1A"
  on-error: "#FFFFFF"
  error-container: "#FFDAD6"
  on-error-container: "#410002"
  page-background: "#EFEDEC"
  card-background: "#FAF9F7"
  field-background: "#F5F3F2"
  text-primary: "#1B1C1B"
  text-secondary: "#464746"
  text-tertiary: "#686A68"
typography:
  sans:
    fontFamily: "Android system default (Typeface.DEFAULT) with OEM CJK fallback"
  mono:
    fontFamily: "monospace"
rounded:
  field: "14px"
  card: "20px"
  activation-card: "24px"
  brand-mark: "16px"
spacing:
  page-horizontal: "20px"
  card-inset: "20px"
  section-gap: "14px"
components:
  page:
    backgroundColor: "#EFEDEC"
    textColor: "#1B1C1B"
  app-mark:
    backgroundColor: "#D4E7DF"
    textColor: "#51625C"
    rounded: "16px"
    size: "52px"
  activation-status-card:
    backgroundColor: "#D4E7DF"
    textColor: "#0F1E1A"
    rounded: "24px"
    padding: "20px"
    height: "96px"
  activation-restart-card:
    backgroundColor: "#F5E7D2"
    textColor: "#4E361A"
    rounded: "24px"
    padding: "20px"
    height: "96px"
  activation-inactive-card:
    backgroundColor: "#FFDAD6"
    textColor: "#410002"
    rounded: "24px"
    padding: "20px"
    height: "96px"
  activation-symbol:
    backgroundColor: "#51625C"
    textColor: "#FFFFFF"
    rounded: "999px"
    size: "52px"
  activation-symbol-warning:
    backgroundColor: "#98621E"
    textColor: "#FFFFFF"
    rounded: "999px"
    size: "52px"
  activation-symbol-inactive:
    backgroundColor: "#BA1A1A"
    textColor: "#FFFFFF"
    rounded: "999px"
    size: "52px"
  settings-card:
    backgroundColor: "#FAF9F7"
    textColor: "#1B1C1B"
    rounded: "20px"
    padding: "20px"
  form-field:
    backgroundColor: "#F5F3F2"
    textColor: "#1B1C1B"
    rounded: "14px"
    padding: "12px"
  save-button:
    backgroundColor: "#51625C"
    textColor: "#FFFFFF"
    rounded: "16px"
    height: "56px"
---

# TriKey Design System

## Overview

### Creative North Star

TriKey is a small hardware control panel: a physical key has three deliberate press patterns, each mapped to one outcome. The interface should read like considered Android settings, not a launcher or a marketing page. A muted sage accent and warm tonal surfaces make state and control hierarchy easy to scan while the system typeface preserves the device's own character.

### Product context and register

- **Audience and primary job:** Android users configuring a system shortcut module to map one physical key to single, double, and long presses.
- **Target market(s) and evidence:** Android devices with a configurable shortcut key; repository actions include ColorOS-specific applications and an AOSP policy fallback.
- **Locale(s) and language policy:** The current interface is Simplified Chinese. Android's system sans family provides CJK fallback; additional locales are not implemented.
- **Usage scene:** A phone settings screen used occasionally, often while testing a physical key. The screen is a vertical, scrollable form with no persistent navigation.
- **Register:** Direct system utility. Labels name settings and outcomes without promotional copy.
- **Memorable signature:** A generous, color-coded module status surface pairs a native-feeling symbol with one concise state label; the gesture sections remain calm and easy to compare.
- **Restraint:** Preserve native Android control behavior and platform-owned selector menus; avoid decorative gradients, heavy shadows, and motion.
- **Anti-references:** Launcher grids, gamer dashboards, and generic marketing hero layouts obscure the settings task and are not used.
- **Token ownership/runtime mapping:** Model B. Android color resources in `app/src/main/res/values/colors.xml` and `values-night/colors.xml` are canonical; this document mirrors them. `MainActivity.java` consumes those resources and owns layout dimensions. Review this mapping when either runtime palette changes.

## Colors

The light palette adapts the supplied Material You reference: muted sage `primary` (#51625C), pale green `primary_container` (#D4E7DF), warm-gray page and field surfaces, and restrained outlines. The active status card uses the sage container; restart-required uses amber; inactive uses the Android error container. Each state has a matching icon so color is never the only signal. The night palette in `values-night/colors.xml` preserves those semantic roles on charcoal surfaces. System focus and selected states use Android's native control behavior.

## Typography

Use Android's default system typeface for all app-owned text and allow the device's normal CJK fallback and user font settings to apply. Do not load a font file or override the system family. The app name is the strongest page heading, section titles are medium-sized and bold, and body/helper text is kept to concise Chinese sentences. Technical values such as key codes and milliseconds remain plain text fields; no custom numeric font is required.

## Layout

The page is one natural-height `ScrollView`; it does not fix content to the viewport. A compact app identity header is followed by a full-width, 96dp-minimum activation card, then the editable settings sections. The status card aligns with the 20dp page inset, uses a 52dp icon beside a 22sp label, checks automatically, and is not interactive. It contains no secondary activation details. Section cards use 20dp insets and are separated by 14dp. Gesture selectors, toggles, and the save action retain comfortable Android touch targets. The save action stays in document flow after all gesture settings so short screens remain scrollable.

## Elevation & Depth

Hierarchy comes from tonal page/card contrast and outlined input fields rather than shadows. Avoid floating or sticky surfaces; they would compete with the settings form and can cover controls while the keyboard is open.

## Shapes

Settings cards use a 20dp radius, the activation status card uses a 24dp radius, fields use a 14dp radius, and the compact TriKey mark uses 16dp. The activation card is filled rather than outlined; fields retain a fine #C7C6C5 outline in light mode and a #424943 outline in dark mode. Frontmatter uses CSS-pixel design units; native layout values are applied as the same numeric Android dp values. Avoid pill-shaped containers for ordinary settings.

## Components

### Foundational visual states

Android owns pressed, focused, checked, and disabled semantics for CheckBox, Switch, Spinner, EditText, and Button. The custom save background changes surface shape and color but keeps the control as a native Button. Module activation is verified from the running system target and loaded version. The status card remains read-only: active shows a check and “已激活”; stale module code shows an exclamation and “需要重启”; absent or failed activation shows an exclamation and “未激活”. App-owned text follows the platform's current default typeface.

### Buttons and actions

There is one primary action, “保存设置”. Native selectors keep platform popup behavior and system typography with readable field contrast. Gesture names are visible above their action selectors, and custom values gain a visible label when the corresponding custom action is selected.

### Navigation and data display

The screen has no route navigation, lists, or tables. The top identity block presents the app name, followed by the live module activation state.

### Forms and overlays

Settings remain in one scrollable form. Native Switch and CheckBox rows use a 48dp minimum touch height. Numeric inputs have visible labels; custom app and Intent fields expose a visible label and an accessible name. Screen-off wake and secure system authentication are always-on behavior, not a setting and not a dedicated explanatory card. The activation card shows only “已激活” or “未激活”, updates automatically, and has no click action. No modal or custom popup is introduced.

### Iconography

The compact letter mark remains text. The activation card uses small vector check/exclamation symbols with adjacent status labels; symbols reinforce rather than replace the Chinese state text. Settings controls retain their Android-native icons and semantics.

### Motion

Use native state transitions only. Do not animate layout changes; expanding a custom parameter field should preserve the current control state and remain reachable by scrolling.

### Content and data visualization

Use concise Simplified Chinese copy and explicit action names. Key codes and durations use Arabic numerals with units in field labels. No data visualization is present.

## Do's and Don'ts

- **Do:** Keep the selector menus native and keyboard/touch operable.
- **Do:** Keep activation color semantic, pair each icon with text, and keep the status card read-only.
- **Do:** Keep secure lock-screen authentication system-owned; document behavior outside the settings form.
- **Don't:** Imply that TriKey bypasses or supplies lock-screen credentials.
- **Don't:** Hide control labels in placeholders or rely on color alone for module status.
