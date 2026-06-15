# Frontend guide — top-up payment modes (Humo + UZ)

Use this file for **admin/settings UI** and **typing** `GET/PUT /api/config`. Full API and card CRUD details: [ADMIN_CARD_AND_CONFIG_API.md](./ADMIN_CARD_AND_CONFIG_API.md). Narrative changelog: [WHAT_WE_CHANGED_TOPUP.md](./WHAT_WE_CHANGED_TOPUP.md).

---

## Three services (siblings — not nested)

In the product there are **three ways** money can be checked:

| Concept | What it is | API |
|--------|----------------|-----|
| **Humo** | Humo flow (Telegram-side) | `humoEnabled` + admin cards with `paymentSystem: "HUMO"` |
| **Oson** | Uzcard verified via **Oson API** (not Telegram) | System `uzcardRail: "OSON"` + Uzcard rows with `uzcardRail: "OSON"` |
| **CardXabar** | Uzcard verified via **CardXabar / bot / 2805** path | System `uzcardRail: "CARDXABAR"` + Uzcard rows with `uzcardRail: "CARDXABAR"` |

**Oson and CardXabar are not parent/child.** CardXabar is **not** “inside” Oson. They are **two parallel verification backends** for the **same card type** (Uzcard / `UZCARD`). Only **one** of them can be active for Uzcard payouts at a time (`uzcardRail` is exactly one of `OSON` | `CARDXABAR` | `OFF`).

### UI anti-pattern (do not build this)

- Do **not** show a control labeled **“Lane”** **under** or **inside** an “Oson” section with **CardXabar** as an option. That implies CardXabar is a sub-option of Oson, which is **wrong**.
- Do **not** nest CardXabar under Oson in the navigation or form hierarchy.

### Recommended settings layout (wireframe)

Two **separate** blocks at the same level:

```
┌ Top-up payment sources ─────────────────────┐
│ [ ] Humo cards in rotation                    │  → humoEnabled
│                                               │
│ Uzcard verification (pick one):               │  → uzcardRail
│   ( ) Oson (API)                              │
│   ( ) CardXabar (bot / 2805)                  │
│   ( ) Off — no Uzcard in rotation             │
└───────────────────────────────────────────────┘
```

Humo is toggle **A**. Uz verification is **one tri-state control B**. They sit **side by side** (or stacked), not B inside “Oson”.

---

## What you must show in settings (field reference)

### 1. Humo in rotation

- **Field:** `humoEnabled` (boolean)
- **Meaning:** If `false`, **Humo** admin cards are not offered for top-up.

### 2. Uzcard verification — Oson / CardXabar / off (one choice)

- **Field:** `uzcardRail` (string enum)
- **Allowed values:** `"OSON"` | `"CARDXABAR"` | `"OFF"`
- **Meaning:**
  - `"OSON"` — only **UZCARD** admin cards whose row has `uzcardRail === "OSON"` are used; payments verified via **Oson API**.
  - `"CARDXABAR"` — only UZCARD rows with `uzcardRail === "CARDXABAR"`; verified via **CardXabar**.
  - `"OFF"` — **no** UZCARD cards in rotation. Humo can still work if `humoEnabled` is true.

**Labels:** Prefer **“Oson (API)”** and **“CardXabar (bot)”** so it matches ops language (Telegram bots vs external API).

### 3. Humo verification

- **Always** CardXabar-then-Humo (dual check). There is **no** configurable cutover date anymore; ignore any old UI for “Humo dual check end”.

---

## TypeScript-style shape (config)

```ts
type UzcardRail = "OSON" | "CARDXABAR" | "OFF";

interface SystemConfiguration {
  // ... all other numeric/boolean fields from GET ...
  humoEnabled: boolean;
  uzcardRail: UzcardRail;
}
```

---

## Admin card create / edit — dropdown hierarchy (required UX)

**Backend needs no change** — only send the correct `paymentSystem` + `uzcardRail` as below.

### Step 1 — Channel (first dropdown)

| User selects | Meaning | Next step |
|--------------|---------|-----------|
| **Oson** | External Oson API (not Telegram bots) | **No second dropdown.** Card is Uzcard via Oson only. |
| **Telegram** | Bot-side checks (port 2805 service: CardXabar vs Humo) | **Second dropdown** required (see below). |

**Wrong UX:** Choosing **Oson** and then showing **Oson API** *and* **CardXabar**. CardXabar belongs under **Telegram**, not under Oson.

### Step 2 — Only when user chose **Telegram**

Show exactly two options:

| User selects | `paymentSystem` | `uzcardRail` |
|--------------|-----------------|--------------|
| **CardXabar** | `UZCARD` | `CARDXABAR` |
| **Humo** | `HUMO` | omit or `null` |

### When user chose **Oson** (step 1 only)

| `paymentSystem` | `uzcardRail` |
|-----------------|--------------|
| `UZCARD` | `OSON` |

No sub-dropdown.

### Wireframe

```
Channel:  [ Oson ▼ ]  [ Telegram ▼ ]

If Oson     → (done) POST { paymentSystem: "UZCARD", uzcardRail: "OSON", ... }

If Telegram → Type: [ CardXabar ▼ ] [ Humo ▼ ]
              CardXabar → { paymentSystem: "UZCARD", uzcardRail: "CARDXABAR", ... }
              Humo      → { paymentSystem: "HUMO", ... }
```

---

## Admin card form — field reference

- **`paymentSystem`:** `"UZCARD"` | `"HUMO"`.
- For **`UZCARD`**, send **`uzcardRail`:** `"OSON"` or `"CARDXABAR"` (never `"OFF"` on a card row).

Per-card `uzcardRail` must **match** the global `uzcardRail` when that mode is active for the card to be offered in rotation.

- Duplicate card numbers: **409 Conflict** (see [ADMIN_CARD_AND_CONFIG_API.md](./ADMIN_CARD_AND_CONFIG_API.md)).

---

## Empty pool (user bot, for reference)

If nothing is eligible (e.g. `humoEnabled: false` and `uzcardRail: "OFF"`), users may see copy keyed as `topup.message.no_payment_method_available` (UZ/RU strings live in backend i18n).

---

## Saving config

- On **PUT/POST** config, send **`uzcardRail` explicitly** when the operator chooses **OFF** so the backend does not treat missing/null as default **OSON**.
- Include **`humoEnabled`** in the payload for a full round-trip save.

---

## Quick eligibility matrix

| `humoEnabled` | `uzcardRail` | Who can appear in top-up rotation |
|---------------|--------------|-----------------------------------|
| true | OSON | Humo cards + UZCARD (verified via Oson) |
| true | CARDXABAR | Humo cards + UZCARD (verified via CardXabar) |
| true | OFF | Humo cards only |
| false | OSON | UZCARD (Oson) only |
| false | CARDXABAR | UZCARD (CardXabar) only |
| false | OFF | **Nobody** (show backend / bot error) |
