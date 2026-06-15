# Admin cards and system config — frontend / ops reference

**Short frontend checklist:** [FRONTEND_TOPUP_CONFIG.md](./FRONTEND_TOPUP_CONFIG.md) (includes **UI anti-pattern** and **settings wireframe** — do not nest CardXabar under Oson.)

**Plain “what we changed” summary:** [WHAT_WE_CHANGED_TOPUP.md](./WHAT_WE_CHANGED_TOPUP.md)

**Changelog (latest):** Top-up uses **Humo** (`humoEnabled`) and **one global Uzcard verification mode** (`uzcardRail`: Oson API / CardXabar / off). **Oson and CardXabar are sibling verification paths** for Uzcard, not parent/child. Per-card `uzcardRail` marks **which of those two paths** that Uzcard row belongs to (pool + PAN uniqueness). Rotation and UZ verification follow **system config**. New user-facing error: `topup.message.no_payment_method_available`.

Basic auth for `/api/admin/*` and `/api/config` is unchanged.

---

## 1. Mental model: three payment check paths (siblings)

| Path | Controlled by | Meaning |
|------|----------------|---------|
| **Humo** (HUMO admin cards) | `humoEnabled` | If `false`, Humo cards are **not** shown in top-up rotation. |
| **Oson** (Uzcard via **Oson API**) | System `uzcardRail === "OSON"` | Only UZCARD rows with `uzcardRail: "OSON"` are eligible; verification uses **Oson API**. |
| **CardXabar** (Uzcard via **bot / 2805**) | System `uzcardRail === "CARDXABAR"` | Only UZCARD rows with `uzcardRail: "CARDXABAR"` are eligible; verification uses **CardXabar**. |
| **UZ disabled** | `uzcardRail === "OFF"` | **No** UZCARD admin cards in rotation. Humo can still work if `humoEnabled` is true. |

**CardXabar is not “under” Oson** in product logic: both are **parallel** Uzcard verification backends. The API still uses one enum `uzcardRail` to pick **at most one** active Uz path (or off).

**Frontend:** Do **not** label per-card `uzcardRail` as **“Lane”** nested under an **“Oson”** header — use **“Verified via”** / **Oson | CardXabar** as peers. See [FRONTEND_TOPUP_CONFIG.md](./FRONTEND_TOPUP_CONFIG.md).

**Rotation pool** (primary `OsonConfig` only, same as before):

- Include **HUMO** rows iff `humoEnabled === true`.
- Include **UZCARD** rows iff system `uzcardRail` is `OSON` or `CARDXABAR` **and** the row’s `uzcardRail` **equals** that global mode (same verification path).

If the pool is empty (e.g. Humo off **and** UZ off, or no cards for the active lane), the bot shows a generic “no payment method” message.

---

## 2. `GET /api/config` and `PUT /api/config/{id}` / `POST /api/config`

`SystemConfiguration` includes:

| Field | Type | Notes |
|--------|------|--------|
| `humoEnabled` | boolean | Humo cards in/out of rotation. |
| `uzcardRail` | string enum | **`"OSON"`** \| **`"CARDXABAR"`** \| **`"OFF"`**. Global UZ mode. |
| ~~`humoLegacyDualCheckEnd`~~ | _(removed from API JSON)_ | Deprecated; Humo always uses CardXabar-then-Humo. Column may still exist in DB. |

**Important:** Sending **`uzcardRail: "OFF"`** must be preserved on save. Omitting or sending `null` for `uzcardRail` may be coerced to **`"OSON"`** by the backend when updating (see service defaults).

---

## 3. Admin cards API

### Create — `POST /api/admin/cards/oson/{osonConfigId}`

| Field | UZCARD | HUMO |
|--------|--------|------|
| `cardNumber` | required, 16 digits | required, 16 digits |
| `paymentSystem` | `UZCARD` | `HUMO` |
| `uzcardRail` | **`OSON` or `CARDXABAR`** — **which verification path** this Uzcard row uses (Oson API vs CardXabar). Not a “sub-option of Oson.” If omitted, server defaults: if global UZ is `OFF`, defaults to **`OSON`** (idle until global mode matches). Else defaults to current global `OSON`/`CARDXABAR`. | omit / null (stored null) |

**Uniqueness (per `osonConfigId`):**

- **UZCARD:** at most one row per `(cardNumber, uzcardRail)` — same PAN allowed once on **OSON** and once on **CARDXABAR**.
- **HUMO:** at most one row per `cardNumber`.

Duplicate → **409 Conflict**.

### Update — `PUT /api/admin/cards/{id}`

Same rules; `uzcardRail` on the body updates the row’s **verification path** (Oson vs CardXabar) when provided (non-null). Global mode does **not** change from this endpoint.

### Listing

Responses include `uzcardRail` for **UZCARD** rows when set.

---

## 4. What is **not** true anymore

- Per-card `uzcardRail` does **not** override which API verifies payment. **Verification** for UZ uses **`GET /api/config` → `uzcardRail`** (`OSON` / `CARDXABAR` / `OFF`).
- Per-card `uzcardRail` only decides **membership** in the UZ pool when global mode is `OSON` or `CARDXABAR` (row must match the active path).
- **CardXabar is not a child of Oson** in the domain model; avoid UI that nests them that way.

---

## 5. Admin Telegram bot

Under **Funksiyalar**:

- **HUMO yoq/o'chir** — toggles `humoEnabled`.
- **UZ tekshiruv (Oson/CardXabar)** — opens global UZ mode: **Oson API**, **CardXabar**, or **UZ o'chiq** (`OFF`).

Card add flow still asks **Oson vs CardXabar** for each new **UZCARD** row (that sets the row’s **verification path** / uniqueness bucket), separate from the global switch.

---

## 6. i18n (user bot)

| Key | When |
|-----|------|
| `topup.message.no_payment_method_available` | No eligible admin card (e.g. Humo off and UZ off, or no cards for active lanes). |
| `topup.message.no_uzcard_available` | Still present for older paths; new logic prefers `no_payment_method_available` for the unified empty pool. |

---

## 7. Database

Run Flyway/manual migration **[`V19__uzcard_rail_allow_off.sql`](../src/main/resources/db/migration/V19__uzcard_rail_allow_off.sql)** if PostgreSQL has a CHECK on `system_configuration.uzcard_rail` that only allowed two values, so **`OFF`** can be stored.

---

## 8. Quick matrix

| `humoEnabled` | `uzcardRail` | Eligible cards |
|---------------|--------------|----------------|
| true | OSON | HUMO + UZCARD with `uzcardRail` OSON |
| true | CARDXABAR | HUMO + UZCARD with `uzcardRail` CARDXABAR |
| true | OFF | HUMO only |
| false | OSON | UZCARD OSON only |
| false | CARDXABAR | UZCARD CARDXABAR only |
| false | OFF | **none** → error message |
