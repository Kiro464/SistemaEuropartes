# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Staff members using the company’s commercial operations app. Existing mobile role navigation includes Administrador General, Asesor Financiero, Vendedor, Soporte TI, Asesor de Marketing, Coordinador de Logística, and Oficial de Logística. Each user works within the destinations made available to their role.

## Product Purpose

EuropartesMobile gives staff native mobile access to the implemented Europartes commercial system. In this phase it supports JWT sign-in, remote product catalog maintenance, and local product drafts. Success means users can understand the data’s source and status while completing those existing tasks clearly on a phone.

## Positioning

The mobile app is a native Kotlin and Jetpack Compose client of the existing Django API and Room-backed local drafts. Django remains authoritative for remote product and business operations; Room stores local drafts and cached remote products.

## Operating Context

Staff use the app on Android phones. Existing workflows include signing in, viewing and maintaining remote products, and creating, editing, or deleting local drafts. The web application is an established reference for Europartes brand colors.

## Capabilities and Constraints

Preserve the existing API endpoints and payloads, JWT/session behavior, role-based destination visibility, Room persistence, product cache semantics, and the distinct `LOCAL_ONLY`, `REMOTE_CACHED`, and `PENDING_SYNC` states. Stock displayed in product maintenance is informational and is not editable there. Do not present local drafts as server-confirmed or synchronized.

## Brand Commitments

The company name is Europartes. The user requires the mobile palette to use the same tonal colors as the web brand. The current web tokens are defined in `Frontend/css/brand.css`.

## Evidence on Hand

Existing implementation and sample preview records are in `Android/app/src/main/java/com/europartes/mobile/`. No credentials or live API responses belong in previews.

## Product Principles

- Preserve server authority for remote business data.
- Show whether information is local, cached, or remote.
- Keep role-based navigation aligned with existing destinations.
- Make operational information legible and actions unambiguous on mobile.
