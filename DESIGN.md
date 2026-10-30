# EuropartesMobile design system

## Product direction

EuropartesMobile is a native operations app for commercial and inventory workflows. Its interface uses the same visual identity as the Europartes web application while adapting information density and interactions to a phone. The visual character is precise, calm, and operational: deep brand blue, a restrained yellow accent, clean light surfaces, and clear status colors.

## Color

The canonical Compose values live in `Android/app/src/main/java/com/europartes/mobile/ui/theme/Color.kt`; Material 3 roles are mapped in `Theme.kt`.

| Role | Light | Dark |
| --- | --- | --- |
| Brand primary | `#003399` | `#8FB0FF` |
| Brand accent | `#FFCC00` | `#FFCC00` |
| App background | `#F2F5F9` | `#101722` |
| Main surface | `#FFFFFF` | `#172131` |
| Raised/muted surface | `#F6F8FB` | `#1D2A3C` |
| Primary text | `#1C2738` | `#EDF2FA` |
| Secondary text | `#4F5D70` | `#BAC6D7` |
| Divider/border | `#E1E7EF` | `#2A384B` |

Semantic success, warning, and error colors are defined centrally and mapped to container/on-container roles. Status is also expressed with text or icons; color alone never carries its meaning.

## Typography and spacing

Typography is centralized in `ui/theme/Type.kt` and uses the system sans-serif family with an explicit scale for display, headings, titles, body, and labels. Prefer the semantic Material 3 styles over ad-hoc sizes. Use the shared spacing values and component dimensions already established in Compose layouts; do not introduce fixed screen widths. Content should remain readable with larger system font scales.

## Components

`ui/components/DesignComponents.kt` is the shared entry point for the brand mark, labeled fields, primary/loading action, status message, section and outlined cards, metric and neutral badges, and state badges. Keep actions at least 48dp where practical, give destructive actions an explicit label and confirmation, and make loading/error/empty states understandable without color.

Use one primary action per screen region. Group related product data in a single mobile card, rather than compressing web tables or nesting decorative cards. Keep form fields labeled, provide keyboard types appropriate to their values, expose server validation errors, and preserve cancel/back behavior.

## Screen patterns

- **Login:** branded identity panel, concise context, labeled credentials, password visibility action, explicit processing and authentication error states. Keep the form scrollable/IME-safe on compact devices.
- **Products:** title and context, search/filter controls, explicit result counts, clear empty/error/loading states, and product cards that prioritize name, ID, brand, availability, price, supplier, and active status. Retain all server-driven permissions and operations.
- **Product form:** group editable catalog and price data, show stock as informational/read-only, distinguish save from cancel, disable repeated submission while processing, and present field/server errors next to the relevant context.
- **Local products:** state provenance explicitly (`LOCAL_ONLY`, `REMOTE_CACHED`, `PENDING_SYNC`); do not imply synchronization that does not exist.
- **Navigation:** retain only implemented destinations, show current section and authenticated identity/role, and preserve the existing authorization-driven destination list.

## Accessibility and adaptive layout

Use Material semantics and content descriptions for icon-only controls, meaningful field labels, readable contrast, sufficient touch targets, and keyboard-safe scrolling. Validate compact and regular phone widths, larger text, light/dark schemes, long product names, and each meaningful UI state through Compose previews and emulator checks. Previews use clearly fictitious data and must not read credentials, tokens, or call repositories/network services.

## Source of truth and constraints

The web brand palette is sourced from `Frontend/css/brand.css` and semantic states from the existing web styles. Compose tokens are the Android expression of that identity, not a reason to duplicate web layouts. UI changes must preserve API routes and payloads, role rules, authentication, Room persistence, product origin/sync states, and server ownership of business calculations.
