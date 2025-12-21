# Blocked Users Management API

## Overview
This API allows admins to view all users and manage blocks. A user is considered "Blocked" if their phone number entry in the system is explicitly set to the string `"BLOCKED"`.

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

## Authentication
All requests require **Basic Authentication**:
- **Header**: `Authorization: Basic TWF4VXAxMDAwOk1heFVwMTAwMA==`
- **Credentials**: `MaxUp1000 : MaxUp1000`
