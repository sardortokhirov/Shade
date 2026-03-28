# Blocked Users Management API

## Overview
This API allows admins to view all users and manage blocks. A user is considered "Blocked" if their phone number entry in the system is explicitly set to the string `"BLOCKED"`.

Additionally, **blocked phone numbers** are stored in table `blocked_phone_number`. When a user is blocked by chat ID, their current real phone (if any) is added to that list so they cannot register again with the same number under a new Telegram account. **Unblocking by chat** removes the row linked to that chat. **Block/unblock by phone** manages entries without a chat link.

## Endpoints

### 1. Get All Users (Sorted by Blocked)
**URL**: `GET /api/admin/blocked-users`

**Description**:
Returns a paginated list of all users. Users who are currently blocked appear at the top of the list.

**Query Parameters**:
- `page` (int, default: 0): Page number.
- `size` (int, default: 10): Items per page.

**Example Response**:
```json
{
  "content": [
    {
      "chatId": 123456789,
      "language": "uz",
      "blocked": true,
      "phoneNumber": "BLOCKED"
    },
    {
      "chatId": 987654321,
      "language": "ru",
      "blocked": false,
      "phoneNumber": "+998901234567"
    }
  ],
  "pageable": { ... },
  "totalPages": 5,
  "totalElements": 48
}
```

---

### 2. Unblock User
**URL**: `POST /api/admin/blocked-users/unblock`

**Description**:
Removes the block from a user. This will only work if the user's phone number is currently `"BLOCKED"`.

**Query Parameters**:
- `chatId` (Long, required): The Telegram Chat ID of the user to unblock.

**Example Request**:
`POST /api/admin/blocked-users/unblock?chatId=123456789`

**Responses**:
- `200 OK`: "✅ Foydalanuvchi blokdan chiqarildi: 123456789"
- `404 Not Found`: "❌ Foydalanuvchi bloklanganlar ro‘yxatida emas" (Returned if user is not in the list or is already unblocked).

---

### 3. Block by phone number
**URL**: `POST /api/admin/blocked-users/block-phone`

**Query parameters**:
- `phone` (String, required): Raw or formatted number; normalized internally (digits + leading `+`, spaces/dashes stripped).

**Responses**:
- `200 OK`: Phone added to global blocklist (`linked_chat_id` is null).
- `400 Bad Request`: Normalization failed (empty/invalid).

**Example**: `POST /api/admin/blocked-users/block-phone?phone=%2B998901234567`

---

### 4. Unblock by phone number
**URL**: `POST /api/admin/blocked-users/unblock-phone`

**Query parameters**:
- `phone` (String, required): Same normalization as block-phone.

**Responses**:
- `200 OK`: Row removed from blocklist.
- `404 Not Found`: No matching normalized phone on the list.

---

## Authentication
All requests require **Basic Authentication** (same as other admin APIs in this deployment):
- **Header**: `Authorization: Basic <base64(username:password)>`
- **Credentials**: match `BlockedUserController` (e.g. `MaxUp1000` and the configured password).
