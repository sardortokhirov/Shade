# Promo Logic Implementation Guide

## Overview
The Promo Bonus system has been updated to restrict bonus transfers based on specific **User IDs** when "Promo Mode" is active. The system now ignores the platform and only checks if the strict `userId` is whitelisted.

## User Flow
1.  **Access:** All users can access the **Bonus** menu in the Telegram bot.
2.  **Input:** User selects a **Platform** and enters their **User ID**.
3.  **Validation:**
    *   The system first validates the ID with the Platform API.
    *   **New Check:** If **Promo Mode** is `ENABLED`, the system checks if the `userId` is in the whitelist.
4.  **Outcome:**
    *   **Allowed:** If whitelisted (or Promo Mode is `DISABLED`), the user proceeds.
    *   **Restricted:** If not whitelisted, the user receives a message directing them to contact admin `@Boss9w`.

## Admin Whitelist API
Admins can manage the whitelist using the following API endpoints. Supports **Add**, **Delete**, and **Get All**.

### Base URL
`/api/admin/promo/users`

### Authentication
**Basic Auth**: Requires admin credentials (e.g., `MaxUp1000:MaxUp1000`).

### 1. Add User to Whitelist
**POST** `/api/admin/promo/users`

**Parameters:**
| Parameter | Type   | Description           |
| :-------- | :----- | :-------------------- |
| `userId`  | String | The User ID to allow. |

**Example:**
```bash
curl -X POST "http://localhost:8080/api/admin/promo/users?userId=123456" \
     -H "Authorization: Basic TWF4VXAxMDAwOk1heFVwMTAwMA=="
```

### 2. Remove User from Whitelist
**DELETE** `/api/admin/promo/users`

**Parameters:**
| Parameter | Type   | Description             |
| :-------- | :----- | :---------------------- |
| `userId`  | String | The User ID to remove.  |

**Example:**
```bash
curl -X DELETE "http://localhost:8080/api/admin/promo/users?userId=123456" \
     -H "Authorization: Basic TWF4VXAxMDAwOk1heFVwMTAwMA=="
```

### 3. Get All Allowed Users (Paginated)
**GET** `/api/admin/promo/users`

**Parameters:**
| Parameter | Type | Default | Description             |
| :--- | :--- | :--- | :--- |
| `page`    | int  | 0       | Page number (0-indexed) |
| `size`    | int  | 10      | Number of items per page|

**Example:**
```bash
curl -X GET "http://localhost:8080/api/admin/promo/users?page=0&size=20" \
     -H "Authorization: Basic TWF4VXAxMDAwOk1heFVwMTAwMA=="
```
**Response:**
Returns a JSON Page object containing the list of users and pagination metadata.
