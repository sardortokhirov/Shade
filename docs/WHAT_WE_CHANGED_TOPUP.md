# What we changed — top-up payments, cards, and docs

This file is a **plain-language summary** of changes in the Shade project around **Humo / Oson / CardXabar**, admin cards, and **documentation**. For API details use [ADMIN_CARD_AND_CONFIG_API.md](./ADMIN_CARD_AND_CONFIG_API.md); for UI use [FRONTEND_TOPUP_CONFIG.md](./FRONTEND_TOPUP_CONFIG.md).

---

## 1. Backend behavior (how it works now)

### Three concepts (your product language)

| Concept | Role |
|--------|------|
| **Humo** | Humo admin cards; on/off with **`humoEnabled`** in system config. |
| **Oson** | Uzcard checked via **Oson API** — not Telegram. |
| **CardXabar** | Same **Uzcard** plastic, but checked via **CardXabar / bot / 2805** path. |

Humo is **separate** from Uzcard. **Oson and CardXabar are not nested**: they are **two different ways** to verify **Uzcard** payments. The server allows **only one** of those two to be active for Uzcard at a time, or **Uzcard off entirely**.

### System config (`GET/PUT /api/config`)

- **`humoEnabled`** — if `false`, no Humo cards in the top-up rotation.
- **`uzcardRail`** — **`"OSON"`** | **`"CARDXABAR"`** | **`"OFF"`**  
  - **`OFF`** means **no Uzcard** in rotation (both Oson and CardXabar off for payouts). Humo can still work if it is on.

### Admin cards

- **`paymentSystem`:** **`HUMO`** or **`UZCARD`**.
- For **`UZCARD`**, each row has **`uzcardRail`:** **`OSON`** or **`CARDXABAR`** (which **verification path** that card belongs to — pool + duplicate rules).
- **Same 16-digit number** can exist **twice** as Uzcard if one row is **Oson** and one is **CardXabar** (per config).

### Who gets picked for top-up

- Only cards on the **primary** Oson config pool, filtered by:
  - Humo rows if Humo is enabled.
  - Uzcard rows whose **`uzcardRail` matches** the **global** `uzcardRail` (when global is not `OFF`).
- If **nobody** matches (e.g. Humo off **and** `uzcardRail` **OFF**), the user sees a **“no payment method”** style message (`topup.message.no_payment_method_available`).

### Payment verification

- **Uzcard:** uses **global** `uzcardRail` (Oson API vs CardXabar vs skip if `OFF`).
- **Humo:** always **CardXabar then Humo** (`verifyPaymentAmount`); no scheduled cutover to Humo-only.

### Database / ops

- Migration **[`V19__uzcard_rail_allow_off.sql`](../src/main/resources/db/migration/V19__uzcard_rail_allow_off.sql)** — allows storing **`OFF`** on `system_configuration.uzcard_rail` if an old CHECK blocked it.

### Admin Telegram bot

- Under features: **UZ tekshiruv** menu to set global **Oson / CardXabar / UZ off** (same as `uzcardRail`).

---

## 2. Documentation changes (what we wrote for the team)

### New / updated files

| File | Purpose |
|------|---------|
| [FRONTEND_TOPUP_CONFIG.md](./FRONTEND_TOPUP_CONFIG.md) | Frontend: fields, TypeScript-ish types, **wireframe**, **do not nest “Lane” under Oson**. |
| [ADMIN_CARD_AND_CONFIG_API.md](./ADMIN_CARD_AND_CONFIG_API.md) | Full API reference, rotation matrix, sibling model for Oson vs CardXabar. |
| **This file** | Short “what changed” narrative for PM / frontend / ops. |

### UI clarification (fixing a wrong mental model)

- **Problem:** A screen that puts **“Lane”** under **“Oson”** with **CardXabar** inside looks like CardXabar **belongs to** Oson.
- **Reality:** CardXabar and Oson are **siblings** (two Uzcard check backends). Humo is the **third** path, controlled separately.
- **Fix:** Docs tell frontend to use **Humo toggle** + **one tri-state** for Uzcard (**Oson / CardXabar / Off**), and on the card form label **`uzcardRail`** as **“Verified via”** (or similar), **not** “lane” under Oson.

**No backend change** was required for that clarification — only **correct UI layout and labels**.

---

## 3. Quick “before vs after” (conceptual)

| Before (confusing) | After (intended) |
|-------------------|------------------|
| “Lane” under Oson including CardXabar | Humo on/off + flat choice: Uz **Oson** \| **CardXabar** \| **Uz off** |
| Unclear if Oson “contains” CardXabar | Docs: **parallel** Uz paths; Humo separate |

---

## 4. Where to look in code (for developers)

- Rotation filter: [`TopUpService`](../src/main/java/com/example/shade/service/TopUpService.java) — `pickLeastRecentlyUsedTopUpAdminCard`
- Uz verify branch: same class — uses `configurationService.getUzcardRail()`
- Config: [`SystemConfiguration`](../src/main/java/com/example/shade/model/SystemConfiguration.java), [`SystemConfigurationService.setUzcardRail`](../src/main/java/com/example/shade/service/SystemConfigurationService.java)
- Card defaults / uniqueness: [`AdminCardService`](../src/main/java/com/example/shade/service/AdminCardService.java)
- Enum: [`UzcardRail`](../src/main/java/com/example/shade/model/UzcardRail.java) — includes **`OFF`**
