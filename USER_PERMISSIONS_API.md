# Granular User Platform Permissions API

This API allows admins to control specific actions (Top-up, Withdraw, Bonus Top-up) for specific platform User IDs.

## Base URL
`/api/admin/user-permissions`

## Authentication
**Basic Authentication**
- Username: `MaxUp1000`
- Password: `MaxUp1000`

---

## 1. Get All Permission Overrides
Returns a paginated list of all User IDs that have special permission settings.

- **URL**: `/api/admin/user-permissions`
- **Method**: `GET`
- **Query Params**:
  - `page` (int, default 0)
  - `size` (int, default 10)

---

## 2. Save/Update Permissions
Add a new override or update an existing one for a specific User ID.

- **URL**: `/api/admin/user-permissions`
- **Method**: `POST`
- **Query Params**:
  - `userId` (String, Required): The platform User ID.
  - `canTopUp` (boolean, Optional, default: true)
  - `canWithdraw` (boolean, Optional, default: true)
  - `canBonusTopUp` (boolean, Optional, default: true)

**Note**: If a `userId` is not in this list, all actions are **ALLOWED** by default. Setting a flag to `false` will block that action for that ID.

---

## 3. Delete Permission Override
Remove the override for a specific User ID, effectively resetting them to "Allowed" for all actions.

- **URL**: `/api/admin/user-permissions`
- **Method**: `DELETE`
- **Query Params**:
  - `userId` (String, Required)

---

## Message to Users
If a user is blocked, they will receive a message in their selected language:
- **UZ**: ❗️ Sizga ushbu ID orqali [Harakat] taqiqlangan. Ma'lumot uchun administrator bilan bog‘laning: @Boss9w
- **RU**: ❗️ Вам запрещено [Действие] через этот ID. Для информации свяжитесь с администратором: @Boss9w
