# Open Listener Design System

## Direction

**Ambient Precision** combines low-frequency sky light with unusually clear transcript typography. The app is an operating surface, not a mood board: one recording action stays dominant, while text remains readable during a lecture.

## Scene

The listener is seated in a classroom or lecture hall, often under uneven indoor light and sometimes with the phone screen dimmed. Light mode uses mist blue, warm cloud and soft ink; dark mode uses deep blue-violet and graphite. Background motion stops when the app is not visible, battery is low, or reduced motion is enabled.

## Tokens

- ink.primary: `#182532` / `#F5F6FA`
- ink.secondary: `#5C6B79` / `#ADB6C3`
- sky.top: `#B7CDDC` / `#111B37`
- sky.mid: `#E0E8ED` / `#253456`
- sky.bottom: `#F8EEE8` / `#3F405A`
- surface.reading: `#D9FFFFFF` / `#E6111827`
- accent: `#406B88` / `#A5C8E3`
- signal.live: `#D3646A` / `#F18F91`

## Composition

A compact branded header sits above a Material 3 top segmented navigation row. Listen is built around the recording affordance and a reading surface. Library and Settings use spaced list rows and restrained surfaces rather than nested card grids.

## Type

Roboto/system fallback through the Material 3 type scale. Original text uses body-large or body-medium; translated text uses body-large with slightly stronger contrast; timestamps and provider labels use label-small.

## Motion

Use Compose fade-through and short press transitions. Ambient background drift is deliberately slow and disabled outside the foreground. Partial text is lighter than final text, translation fades in without moving the segment layout, and only a successful persistence operation reports completion.

## Native Rules

Material 3 structure, system Back, edge-to-edge insets, 48dp touch targets, semantic labels, dynamic color only where it preserves the static direction, and no bottom navigation. The top tabs remain visible and are horizontally scrollable on narrow widths.
