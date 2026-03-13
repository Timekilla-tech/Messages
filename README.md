# Zurvas - Enhanced Android SMS Messaging App

**Zurvas** is a next-generation Android SMS messenger app based on Fossify Messages, reimagined for modern communication needs. The project is developed as a final-year thesis by [Г.Төртүшиг (B220000135)], focused on UX optimization, powerful conversation management, and feature innovations for foldable devices.

---

## 📝 Project Goals

- **Inbox UX Redesign:** Fast organization, smart triage, and customized navigation.
- **Advanced Conversation Features:** Pinning, color-coding, custom folders/tags.
- **Scheduled Send:** Compose now, deliver later—robust scheduling even in background.
- **Smooth Foldable Support:** Pixel Fold QA, seamless screen continuity, state restoration.

---

## 🚀 Core Features

- **Swipe Actions:** One-hand gestures to Archive, Delete, or Block conversations (configurable).
- **Pinned Section:** Pin important conversations to keep them always visible.
- **Age Grouping:** Inbox sections: Today • Yesterday • This week • This month • Older—makes finding messages easy.
- **Long-press Customization:** Pin, choose color-coding, assign custom folder/tag—all via a modern context menu.
- **Folder/Category System:** Organize threads with user-created folders; filter inbox via tabs or dropdown.
- **Scheduled Message Send:** Schedule SMS to be sent at a future time; status displayed in Compose screen.
- **Room Database Upgrades:** Reliable local storage of pin, color, folder metadata; tested migrations.
- **Foldable Device QA:** Pixel 10 Fold screen continuity—no crash, UI distortion, or state loss across outer ↔ inner screen.

![Inbox Wireframe](screenshots/inbox_wireframe.png)
![Swipe Actions](screenshots/swipe_actions.png)
*(You can add or replace screenshots above.)*

---

## 📦 Installation & Build

1. **Clone the repo:**
   ```bash
   git clone https://gitlab.sict.edu.mn/B220000135/Messages
   cd Messages
   ```

2. **Open in Android Studio:**  
   - Minimum SDK: 26, Target SDK: 36
   - Ensure JDK 11+ is installed.

3. **Build the APK:**
   - Run `Build > Make Project`
   - Install on device/emulator (Pixel Fold recommended for QA)

4. **Permissions:**
   - Grant SMS, Contacts, Notifications for full feature set.

---

## 🛠️ How It Works

- **Inbox** loads conversations, sorts by pinned and age groups.
- **Swipe** a conversation left/right—action icons/animations show Archive, Delete, Block.
- **Long-press** opens bottom sheet context menu: Pin/Unpin, set color, assign folder.
- **Folders** are managed via CRUD UI; assign via picker; filter inbox instantly.
- **Schedule Send:** In Compose screen, select date/time → message is stored locally → background worker sends SMS at the scheduled moment (using WorkManager, fallback: AlarmManager).
- **Room Database** stores all custom metadata; tested fallbackToDestructiveMigration; migration scripts provided.
- **Foldable Support:** UI state is preserved through screen transitions; menu/dialog overlays adapt responsively.

---

## 📖 Documentation

- [Sprint Plan](docs/SprintPlan_v0.1_Template_Filled_Zurvas_MN.txt)
- [System Analysis](docs/Chapter2_Analysis_MN.txt)
- [Design & Implementation](docs/Chapter3_Design_Implementation_MN.txt)
- [UML Diagrams](docs/uml/)
- [Testing Plan & QA](docs/testing/)

---

## 💡 FAQ

**Q:** Why not just use Fossify Messages?  
**A:** Zurvas adds enhanced features for daily SMS power-users: categorization, pinning, time grouping, scheduled messaging, plus robust support for foldables.

**Q:** Will my custom folders be lost if I update?  
**A:** No—Room migrations are fully tested. However, if you switch to another app, export as needed.

**Q:** How do scheduled sends work if my phone is off?  
**A:** Scheduled messages are triggered when the device is back online and WorkManager constraints are met.

**Q:** Is cloud sync supported?  
**A:** Not in MVP. All data is stored locally for privacy.

---

## 🙌 Credits

- Fossify Messages (GPLv3)
- Android Jetpack, Room, WorkManager
- Thesis Mentor: А.Отгонбаяр, Д.Даариймаа
- [All contributors and inspiration sources](docs/Bibliography_APA7.txt)

---

## 📃 License

[GPLv3 (Fossify base)](LICENSE)
Additional thesis code: © Г.Төртүшиг 2026.

---

## 📞 Contact

- Email: [your@email.com]
- Demo repo: [https://gitlab.sict.edu.mn/B220000135/Messages](https://gitlab.sict.edu.mn/B220000135/Messages)
