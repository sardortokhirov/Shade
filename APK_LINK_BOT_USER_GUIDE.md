# APK Link Bot – Step-by-Step User Guide (Process)

This guide walks through the bot screen by screen: what you see, what you tap, and what appears next.

---

## Part A: Private Chat (talking to the bot alone)

---

### A1. First time you open the bot

**Step 1**  
You open the bot and send any message (e.g. type "Hi" or tap **Start**).

**Step 2**  
You see one message from the bot:
- Text: **"Tilni tanlang:"** and **"Выберите язык:"** (choose language in Uzbek and Russian).
- **Two buttons in one row:** [ **O'zbek** ] [ **Русский** ]

**Step 3**  
You tap one of the buttons:
- Tap **O'zbek** → the bot remembers Uzbek and shows the main menu (see A2).
- Tap **Русский** → the bot remembers Russian and shows the main menu (see A2).

---

### A2. Main menu (every time after language is set)

**Step 1**  
You send any message in the bot (or you just chose language).

**Step 2**  
You see one message from the bot:
- Text: main menu message (e.g. "Choose an option" in your language).
- **Three buttons, one per row:**
  - [ **Link / APK** ]
  - [ **Group / Channel** ]
  - [ **Contacts** ]

**Step 3**  
You choose one of the three. What happens next depends on your choice:
- **Link / APK** → go to **A3**.
- **Group / Channel** → go to **A6**.
- **Contacts** → go to **A7**.

---

### A3. After you tap "Link / APK"

**Step 1**  
You tapped **Link / APK** on the main menu.

**Step 2**  
You see one message:
- Text: **"Select platform"** (in your language).
- **Platform buttons in a grid:** two buttons per row (e.g. [ **1xbet** ] [ **mostbet** ], then [ **Platform 3** ] …).
- **Last row:** [ **Back** ]

If there are no platforms, you only see text like "Select platform. No platforms configured." (no platform buttons).

**Step 3**  
You tap:
- A **platform name** (e.g. **1xbet**) → go to **A4**.
- **Back** → you return to the **main menu** (A2).

---

### A4. After you choose a platform (Link or APK?)

**Step 1**  
You tapped a platform (e.g. **1xbet**) on the platform list.

**Step 2**  
You see one message:
- Text: **"Choose link or APK"** (in your language).
- **First row:** [ **Link** ] [ **APK** ]
- **Second row:** [ **Back** ]

**Step 3**  
You tap one of the three:
- **Link** → go to **A5a**.
- **APK** → go to **A5b**.
- **Back** → you return to the **platform list** (A3).

---

### A5a. After you tap "Link"

**Step 1**  
You tapped **Link** for a platform.

**Step 2 – If you are NOT on cooldown**  
You see:
- One message with the **platform’s link** (URL as text).  
You can copy or open it.  
No extra buttons.

**Step 2 – If you ARE on cooldown and the channel link is set**  
You see:
- Text like: "Time limit applies. Open the channel to get the link or APK:"
- **One button:** [ **Open channel** ]  
Tapping it opens the channel post with the APK/link.

**Step 2 – If you ARE on cooldown and the channel link is not set**  
You see:
- Text like: "Try again in X minutes left."
- **One button:** [ **Back** ]  
Tapping **Back** returns you to the main menu (A2).

---

### A5b. After you tap "APK"

**Step 1**  
You tapped **APK** for a platform.

**Step 2 – If the bot has a main channel link set**  
You see:
- Text like: "Open the channel to get the APK:"
- **One button:** [ **Open channel** ]  
Tapping it opens the channel message where all APKs are posted.  
There is **no cooldown** for APK.

**Step 2 – If the bot does NOT have a main channel link**  
You see one of these:
- The **APK file** (document) from the bot in the chat, **or**
- A **link (URL)** as text if the file could not be sent, **or**
- Text: "APK is not configured for this platform."

No extra buttons in these cases.

---

### A6. After you tap "Group / Channel"

**Step 1**  
You tapped **Group / Channel** on the main menu.

**Step 2**  
You see one message:
- Text: **"Tap the button to join a group or channel:"** (in your language).
- **A table of buttons:**  
  - Each row has up to two buttons: **left** = a channel (📢 name), **right** = a group (👥 name).  
  - Rows are filled in order (channels and groups from the admin list).  
  - If there are more channels than groups (or the other way around), some rows have only one button.
- **Last row:** [ **Back** ]

**Step 3**  
You tap:
- A **channel** or **group** button → Telegram opens the **invite link** in the app (you join that channel or group). The bot does not send a new message.
- **Back** → you return to the **main menu** (A2).

---

### A7. After you tap "Contacts"

**Step 1**  
You tapped **Contacts** on the main menu.

**Step 2**  
You see one message:
- Text: contact prompt (e.g. "Contact us").
- **First row:** [ **Admin** ]  
- **Second row:** [ **Chat** ]  
- **Third row:** [ **Back** ]

**Step 3**  
You tap:
- **Admin** → Telegram opens the admin profile (e.g. t.me/Boss9w).
- **Chat** → Telegram opens the chat (e.g. t.me/Abadiy_Kassa).
- **Back** → you return to the **main menu** (A2).

---

## Part B: In a Group (bot is member of the group)

You **write a text message** in the group. The bot may reply with a message and buttons. There are two types of commands.

---

### B1. "All APK" command (e.g. type `apk` or `/apk`)

**Step 1**  
You send in the group exactly the **all-APK keyword** (e.g. **apk** or **/apk** — admin sets this).

**Step 2 – If the bot has the main channel link and you are allowed (no cooldown or you are admin)**  
You see one message from the bot:
- Text like: "Open the channel to get the APK:"
- **One button:** [ **Open channel** ]  
Tapping it opens the channel post with all APKs.

**Step 2 – If you are on group cooldown (and you are not an admin)**  
You see:
- Text like: "Try again in X minutes left."  
No button.

**Step 2 – If the main channel link is not set**  
You see:
- Text: "APK link is not configured."

---

### B2. Platform keyword (e.g. type `1xbet` or `/1xbet`)

**Step 1**  
You send in the group a word that matches a **platform keyword** (e.g. **1xbet** or **/1xbet** — admin sets these per platform).

**Step 2 – If you are allowed (no cooldown or you are admin)**  
You see from the bot:
- **First message:** the **platform link** (URL as text).
- **Second message:** either the **APK file** (document) or, if that is not possible, a **link (URL)** as text.

**Step 2 – If you are on group cooldown (and you are not an admin)**  
You see:
- Text like: "Try again in X minutes left."

**Step 2 – If your message does not match any keyword**  
The bot does **not** reply (no message, no buttons).

---

## Part C: In the Main Channel (admin only)

This part is for the **admin** in the **main APK channel** (the one set in the admin panel).

**Step 1**  
You post in that channel exactly the **channel keyword** (e.g. **apk** or **/apk**).

**Step 2**  
The bot posts in the same channel:
- **All APK files** (one or more documents), in batches if there are many.
- The bot **saves this message** as the "main channel link." From now on, when users tap **APK** in private or use the "all APK" keyword in groups, they get a button that opens **this** message.

**If you post the same keyword in a different channel (not the main one)**  
You see:
- Text: "This is not the main APK channel."  
No APK files are sent there.

---

## Quick reference: Buttons you see and what comes next

| Where you are        | Buttons you see                    | What happens when you tap                    |
|----------------------|------------------------------------|----------------------------------------------|
| First screen         | O'zbek, Русский                    | Language saved → Main menu                    |
| Main menu            | Link/APK, Group/Channel, Contacts  | Platform list / Group–channel list / Contacts |
| Platform list        | Platform names (2 per row), Back   | Link or APK choice / Back → Main menu        |
| Link or APK choice   | Link, APK, Back                    | Link result or APK result / Back → Platform list |
| On Link cooldown     | Open channel, or Back              | Opens channel / Back → Main menu             |
| After APK (channel set) | Open channel                    | Opens channel message with APKs              |
| Group/Channel screen | Channel and group names, Back      | Opens invite link / Back → Main menu         |
| Contacts             | Admin, Chat, Back                 | Opens profile or chat / Back → Main menu     |
| Group (all APK)      | Open channel                       | Opens channel message with APKs              |

This is the full process: what appears, what you tap, and what you see next at each step.
