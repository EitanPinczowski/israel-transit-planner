# Installing Israel Transit Planner · התקנת מתכנן הנסיעות

## עברית

1. בטלפון, פתחו את דף הגרסאות:
   https://github.com/EitanPinczowski/israel-transit-planner/releases/latest
2. הורידו את הקובץ שמסתיים ב־`.apk`.
3. פתחו אותו. אנדרואיד ישאל אם לאפשר התקנה ממקור זה ("התקנת אפליקציות לא מוכרות") — אשרו לדפדפן
   או למנהל הקבצים, וחזרו להתקנה.
4. בפתיחה הראשונה האפליקציה תבקש **מיקום** (כדי לתכנן מהמקום שלכם). התראות יתבקשו רק כשתבחרו
   "תזכורת מתי לצאת" או "התחלת נסיעה".

**עדכונים:** כשיש גרסה חדשה יופיע באפליקציה פס "גרסה חדשה זמינה" — לחצו "הורדה" והתקינו מעל הגרסה
הקיימת. ההגדרות, המקומות השמורים וההיסטוריה נשמרים.

**פרטיות:** אין חשבון ואין שרת משלנו. חיפושי מסלול נשלחים ל־Transitous (מתכנן נסיעות חינמי וקוד
פתוח) והמפה מגיעה מ־OpenFreeMap. מקומות שמורים, היסטוריה ותזכורות נשמרים רק בטלפון.

## English

1. On the phone, open https://github.com/EitanPinczowski/israel-transit-planner/releases/latest
2. Download the file ending in `.apk` and open it.
3. Android asks to allow installs from this source ("install unknown apps") — allow it for your
   browser or file manager, then go back and install.
4. On first launch the app asks for **location** (to plan from where you are). Notifications
   are asked for only when you use "Remind me when to leave" or "Start trip".

**Updates:** when a new version is out, an "Update available" banner appears in the app — tap
Download and install over the old version. Settings, saved places and history are kept.

**Permissions, and why**

| Permission | Used for |
|---|---|
| Location | "From: my location"; the get-off alert while a trip is running |
| Notifications | the leave reminder and the get-off alert |
| Exact alarms | ringing the leave reminder on time |
| Run at startup | re-arming a reminder after the phone restarts |
| Foreground service (location) | GPS only while "Start trip" is active; it stops itself |

**Privacy:** no account, no server of our own. Trip searches go to Transitous (a free,
open-source planner) and the map comes from OpenFreeMap. Saved places, history and reminders
stay on the phone.
