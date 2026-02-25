# APK Link Bot – Step-by-Step Overview

This document describes exactly what happens after each user action in the new APK Link distribution bot.

---

## 1. Where the bot runs

- **Private chat:** User talks to the bot one-on-one.
- **Group / supergroup:** User sends a text message; bot may reply in the group.
- **Channel:** Someone (e.g. admin) posts a message; bot may post in the same channel.

All text and buttons use the user’s chosen language (UZ or RU) in **private**; in groups and channels the default language (UZ) is used.

---

## 2. Private chat – first time (no language set)

| Step | User action | What the bot does |
|------|-------------|-------------------|
| 1 | User sends any text (e.g. `/start` or "Hi") | Bot checks: is a language saved for this chat? **No.** |
| 2 | — | Bot sends: **"Choose language"** (UZ + RU text) and **two buttons**: **O'zbek** and **Русский**. |
| 3 | User taps **O'zbek** | Callback `LANG_UZ`: bot saves language "uz" for this chat, then sends the **main menu**. |
| 4 | User taps **Русский** | Callback `LANG_RU`: bot saves language "ru" for this chat, then sends the **main menu**. |

---

## 3. Private chat – main menu (language already set)

| Step | User action | What the bot does |
|------|-------------|-------------------|
| 1 | User sends any text | Bot sees language is set → sends **main menu**: one message with three buttons. |
| 2 | — | **Buttons:** (1) **Link / APK**, (2) **Group / Channel**, (3) **Contacts**. |

From here, every **button press** is a **callback**; the bot reacts by callback data.

---

## 4. Private – "Link / APK" flow

| Step | User action | What the bot does |
|------|-------------|-------------------|
| 1 | User taps **Link / APK** | Callback `MAIN_LINK_APK` → bot loads all platforms from DB, builds **platform list**. |
| 2 | — | If no platforms: sends text "Select platform" + "No platforms configured". If there are platforms: sends **"Select platform"** and a **grid of platform buttons** (2 per row), plus **Back**. |
| 3 | User taps a **platform** (e.g. "1xbet") | Callback `PLATFORM:{id}` → bot loads that platform; if not found sends "Platform not found". Otherwise sends **"Choose link or APK"** and two buttons: **Link** and **APK**, plus **Back**. |
| 4a | User taps **Link** | Callback `SEND:link:{id}` → **handleSendLink**. |
| 4b | User taps **APK** | Callback `SEND:apk:{id}` → **handleSendApk**. |
| 5 | User taps **Back** | Callback `BACK_MAIN` → main menu. |

---

## 5. Private – after "Link" (handleSendLink)

| Step | Logic | Result |
|------|--------|--------|
| 1 | Bot gets **private cooldown** (minutes from config). Gets **remaining minutes** for this user. | If remaining **> 0**: user is on cooldown. |
| 2 | If on cooldown | If **main channel link is set**: bot sends a message with one **URL button** "Open channel" (user can tap to open the APK message in the channel). If **no channel link**: bot sends cooldown text + **Back** button. Then **stop**. |
| 3 | If not on cooldown | Bot loads platform by id. If missing → "Platform not found". Otherwise: **saves new cooldown** for this user, then sends **platform’s link URL** as plain text. |

So: **Link** = one link per platform, with **cooldown** in private. When on cooldown, user can still open the channel via the button if the main channel link exists.

---

## 6. Private – after "APK" (handleSendApk)

| Step | Logic | Result |
|------|--------|--------|
| 1 | Bot checks: is **main channel message link** stored (from admin posting keyword in main channel)? | If **yes** → bot sends **one message** with **one URL button** "Open channel". User taps → opens the channel message with all APKs. **No cooldown** for APK. **Stop.** |
| 2 | If no channel link | Bot loads platform by id. If missing → "Platform not found". |
| 3 | If platform has **apk_file_id** | Bot sends the APK as a **document** (by Telegram file_id). |
| 4 | If platform has only **apk_url** | Bot **downloads** the file from the URL (follows redirects), then sends it as a **document**. If download fails, sends the URL as text. Optionally saves the new Telegram **file_id** for next time. |
| 5 | If platform has neither | Bot sends "APK not configured for this platform". |

So: **APK** in private either **redirects to the main channel** (button) or sends the platform’s APK file/URL. There is **no cooldown** for APK.

---

## 7. Private – "Group / Channel" flow

| Step | User action | What the bot does |
|------|-------------|-------------------|
| 1 | User taps **Group / Channel** | Callback `MAIN_GROUP_CHANNEL` → bot loads **channels** and **groups** from DB (invite links). |
| 2 | — | Sends one message: text "Join group or channel" and a **table of URL buttons**: **left column** = channels (📢), **right column** = groups (👥), one row per pair, last row **Back**. |
| 3 | User taps a channel or group button | That’s a **URL button** → Telegram opens the invite link; **no callback**. User joins the channel/group. |
| 4 | User taps **Back** | Callback `BACK_MAIN` → main menu. |

---

## 8. Private – "Contacts" flow

| Step | User action | What the bot does |
|------|-------------|-------------------|
| 1 | User taps **Contacts** | Callback `MAIN_CONTACTS` → bot sends contact prompt text and **URL buttons**: **Admin** (t.me/Boss9w), **Chat** (t.me/Abadiy_Kassa), and **Back**. |
| 2 | User taps Admin or Chat | Telegram opens that link. |
| 3 | User taps **Back** | Callback `BACK_MAIN` → main menu. |

---

## 9. Group – when user sends a message

Bot only reacts to **text messages**. Message is **normalized**: trim, and if it starts with `/`, the slash is removed (so `apk` and `/apk` are the same).

**First check – "All APK" keyword**

| Step | Condition | What the bot does |
|------|-----------|-------------------|
| 1 | Config has **group keyword** (e.g. "apk") and user text **equals** it (case-insensitive, slash stripped) | This is the **"all APK"** request. |
| 2 | Bot checks if sender is **group admin** (creator/administrator) | If **yes** → no cooldown. If **no** → check **group cooldown**. |
| 3 | If not admin and **remaining cooldown > 0** minutes | Bot sends cooldown message ("Try again in X minutes") and **stops**. |
| 4 | If allowed | Bot applies group cooldown (if not admin). Then: if **main channel link is set** → sends **one message with one URL button** "Open channel". If **not set** → sends "APK link not configured". |

So in a group, the **group keyword** (e.g. `/apk` or `apk`) gives everyone a **button** to the main channel APK message, with **group cooldown** (admins skip cooldown).

**Second check – per-platform keyword**

| Step | Condition | What the bot does |
|------|-----------|-------------------|
| 1 | User text (normalized, slash stripped) **matches a platform keyword** in DB (e.g. "1xbet", "1x") | Bot finds that platform. |
| 2 | If no keyword match | Bot does nothing (no reply). |
| 3 | If match | Bot checks **group cooldown** (and admin exemption). If on cooldown (remaining > 0) → sends cooldown message and stops. |
| 4 | If allowed | Applies group cooldown (if not admin). Sends **platform link URL** as text, then: if platform has **apk_file_id** → sends APK document; if only **apk_url** → downloads and sends document (or URL as text if download fails). Optionally saves **file_id** for next time. |

So in a group, **platform keywords** (e.g. `/1xbet` or `1xbet`) give that platform’s **link + APK**, with **group cooldown**.

---

## 10. Channel – when someone posts a message

Only the **main APK channel** (and only if the message matches the **channel keyword**) triggers the bot.

| Step | Condition | What the bot does |
|------|-----------|-------------------|
| 1 | Message text (trim, lower, slash stripped) **equals** config **channel keyword** (e.g. "apk" or "/apk") | Proceed. Otherwise **no reply**. |
| 2 | Config has **main channel Chat ID** set and this chat is **not** that channel | Bot sends "This is not the main APK channel" and **stops**. |
| 3 | No platforms have APK (no apk_file_id and no apk_url) | Bot sends "APKs not configured" and **stops**. |
| 4 | Otherwise | Bot sends **all APK files** to this channel: platforms with **file_id** are sent by file_id; platforms with only **apk_url** are **downloaded** then sent as documents (in batches of 10, media group when possible). Bot saves the **first sent message’s** chat id + message id as the **main channel APK link**. That link is what private and group "APK" / "all APK" use. If **main channel Chat ID** was not set, it is set to this channel. |

So: **only in the main channel**, when the **channel keyword** is posted, the bot **posts all APKs** and **updates the single stored link** that private and group users open via the button.

---

## 11. Callbacks not matched

If the user taps an inline button whose callback data is **not** one of: language, BACK_MAIN, MAIN_LINK_APK, MAIN_GROUP_CHANNEL, MAIN_CONTACTS, PLATFORM:*, SEND:link:*, SEND:apk:* (e.g. old or invalid button), the bot sends: **"Unknown action. Please choose again from the menu."**

---

## 12. Data used (admin-configurable)

- **Config (API):** Bot token, cooldown (private minutes, group minutes), **channel keyword** (channel), **group keyword** (all APK in groups), **main channel Chat ID** (which channel may trigger "send all APKs" and store the link). The **stored channel message link** (chat id + message id) is set **by the bot** when it posts in the main channel; admin can only set which channel is "main".
- **Platforms:** Name, link URL, apk_file_id, apk_url, apk_file_name, sort order. **Keywords** per platform for group matching.
- **Channels / groups lists:** Name + invite link for the "Group / Channel" screen (URL buttons only).
- **Language:** Stored per private chat id (UZ or RU).

---

## 13. Short flow summary

- **Private:** Start → choose language (once) → main menu → Link/APK or Group/Channel or Contacts. Link/APK → choose platform → Link (cooldown + link text or channel button) or APK (channel button or file/URL, no cooldown). Group/Channel → list of invite buttons. Contacts → Admin/Chat buttons.
- **Group:** Text = group keyword → one "Open channel" button (with group cooldown, admins exempt). Text = platform keyword → platform link + APK file (with group cooldown).
- **Channel:** Only in main channel, message = channel keyword → bot posts all APKs and saves that message as the **main channel link** used for the APK button everywhere.

This is how the new APK Link bot works step by step after each click or message.
