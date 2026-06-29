# Promo Logic Implementation Guide

## Overview
When **Promo Mode** is active, bonus transfers require:
1. The user's **Telegram chat ID** to exist in `promo_allowed_chat`
2. A **linked pair** `(chatId, platformUserId)` in `promo_platform_link` with an optional kontora name label (max 80 chars)

Admins manage links manually: add a chat shell, open detail, add platform rows.

## Admin API

Base URL: `/api/admin/promo`  
Auth: Basic `MaxUp1000:MaxUp1000998905982808`

### Chats
| Method | Path | Description |
|--------|------|-------------|
| GET | `/chats?page=&size=` | Paginated chat list with `linkCount` and `filled` |
| POST | `/chats?chatId=` | Add chat shell |
| DELETE | `/chats?chatId=` | Delete chat and all links |

### Links (inside chat detail)
| Method | Path | Body |
|--------|------|------|
| GET | `/chats/{chatId}/links` | — |
| POST | `/chats/{chatId}/links` | `{ "platformUserId": "123", "platformName": "1xbet" }` |
| DELETE | `/chats/{chatId}/links/{linkId}` | — |

### Search
| Method | Path | Params |
|--------|------|--------|
| GET | `/search` | `chatId` **or** `platformUserId` |

Returns linked platforms (by chat) or linked chat IDs (by platform user ID).

## Deprecated
Old `/api/admin/promo/users` endpoints removed. Use chat-centric API above.
