# Artwork handoff

OpenDesign is producing the new brand assets separately. Integration begins
after the owner selects a direction and the handoff is complete.

## Files to request

- Editable source with named layers, plus SVG masters for vector artwork and
  transparent PNG exports. Avoid flattening the only copy of the design.
- Separate symbol, wordmark, and combined logo; keep app-name text editable
  until the visible brand name is agreed.
- Separate foreground and opaque background layers for the adaptive icon.
- A 16:9 TV launcher banner with the app name, plus editable adaptive layers.
- Colour, single-colour, and reversed logo variants for light and dark surfaces.
- A small brand specification: colour values, typography, font files or sources,
  spacing, minimum sizes, and allowed backgrounds.
- A manifest naming each file, intended use, export dimensions, colour space,
  revision, source, and licence or usage terms, including fonts and stock assets.

## Android TV export targets

| Resource | Delivery target |
| --- | --- |
| TV launcher banner | 16:9; 160x90, 240x135, 320x180, 480x270, and 640x360 px exports |
| Square TV icon | 80, 120, 160, 240, and 320 px exports |
| Adaptive icon | Separate foreground/background; show safe-area and mask previews |
| Design masters | Editable vectors where possible; sRGB raster exports |

Use the [official TV icon and banner template](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines)
for TV-specific safe areas. Keep critical details inside those guides and supply
an unmasked master. Square adaptive icon layers follow the
[adaptive-icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
Keep TV banner and square-icon templates distinct.

## If screen designs are also being produced

Request 1920x1080 TV frames, reusable component definitions, spacing/type/colour
tokens, and normal, focused, pressed, selected, disabled, loading, empty, and
error states. Include remote focus order, Back behaviour, long titles, subtitle
legibility, and contrast on variable backgrounds. Identify any animation's
duration and reduced-motion alternative.

## Acceptance before integration

- Review at actual launcher size and typical TV viewing distance.
- Verify clean edges, safe areas, masked previews, and dark/light backgrounds.
- Verify naming, dimensions, colour values, transparency, and source ownership.
- Confirm fonts and any third-party material have documented usage terms.
- Keep an export manifest and distinguish editable masters from runtime assets.

Apple TV, Fire TV store artwork, and other distribution-specific exports will be
specified after platform and distribution decisions. Preserve editable masters
so those exports can be made without recreating the brand.
