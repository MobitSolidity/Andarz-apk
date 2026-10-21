# Store listing — Google Play (draft for the owner)

Everything here is a **draft**. Text that ships inside the app lives in `content_fa.json` /
`content_market_fa.json` (K13); this file is store copy only. Wording claims were checked against the code:
the journal, engine and backup make no network request; only `:market` connects, only after the user turns
the internet switch on and accepts consent (D-76, guards 2–6). Never write "no internet" or "offline only":
guard 9 forbids that claim in shipped strings, and it would also be false for the store.

Category: **Finance**. Tags (Play Console picks from a fixed list): *Trading journal*, *Risk management*,
*Personal finance*. Default language: **Persian (fa-IR)**; second listing: **English (en-US)**.

## 1. Title, short and full description

Limits: title ≤ 30, short description ≤ 80, full description ≤ 4000 characters. Counted with Python `len()`
(code points): fa full 1444, en full 1622.

### fa-IR

**Title** (19): `اندرز — ژورنال ترید`

**Short description** (69): `قبل از ورود «بله» یا «نه» می‌گوید؛ ژورنال و مدیریت ریسک روی گوشی خودت`

**Full description:**

```
اندرز اساس‌نامهٔ ترید خودت را اجرا می‌کند: پیش از ورود جلوی معاملهٔ بی‌قاعده را می‌گیرد و بعد از خروج اشتباه‌های تکراری‌ات را نشان می‌دهد. اول بقا، بعد سود.

پیش از ورود
• چک‌لیست هشت‌بندی و محاسبهٔ اندازهٔ پوزیشن از روی حد ضرر و درصد ریسک
• مشاور اهرم با بازهٔ مجاز و اهرم پیشنهادی
• اگر یکی از قیدها نخواند، ورود بسته می‌ماند و دلیلش با شمارهٔ ماده و عدد نوشته می‌شود

حین معامله
• حد ضرر فقط به سمت سود جابه‌جا می‌شود؛ خروج پله‌ای با کارمزد
• توقف زمانی، رویدادها و یادداشت قابل اشتراک

بعد از خروج
• R نسبت به حد ضرر اولیه، رعایت قوانین، اشتباه اصلی، احساس و درس
• آمار روزانه، هفتگی و ماهانه، نُه نمودار با نمای جدولی، عملکرد هر ستاپ

قفل‌های انضباطی
• خنک‌شدن بعد از ضرر، سقف ضرر روزانه و هفتگی، قفل افت سرمایه، سقف تعداد معامله
• قوانین فقط در جلسهٔ بازبینی عوض می‌شوند: سخت‌گیرانه‌تر فوراً، آسان‌گیرانه‌تر بعد از ۲۴ ساعت

حریم خصوصی
• ژورنال، معامله‌ها و موجودی فقط روی گوشی خودت می‌ماند؛ بدون حساب کاربری و بدون تبلیغ
• پشتیبان رمزگذاری‌شده (AES-256-GCM) که خودت می‌سازی و نگه می‌داری؛ خروجی CSV و JSON
• قفل برنامه با اثر انگشت یا رمز

بخش بازار (اختیاری)
• تا خودت روشنش نکنی خاموش است. وقتی روشن باشد فقط قیمت‌ها را از سرویس‌های عمومی می‌خواند (CoinGecko، Binance، Nobitex، Wallex، alternative.me) و هیچ چیز از ژورنال نمی‌فرستد.
• قیمت به دلار یا تومان، هشدار حرکت تند قیمت با زنگ، تقویم رویدادهای اقتصادی

اندرز ابزار مدیریت ریسک شخصی است، نه توصیهٔ مالی و نه سیگنال. با هیچ صرافی‌ای معامله نمی‌کند و به حساب صرافی‌ات وصل نمی‌شود.
```

### en-US

**Title** (28): `Andarz: Trading Risk Journal`

**Short description** (79): `Says yes or no before you enter. A trading journal and risk rules on your phone`

**Full description:**

```
Andarz enforces your own written trading constitution. Before you enter, it blocks a trade that breaks your rules; after you exit, it shows the mistakes you keep repeating. Survive first, profit second.

Before the entry
• An eight-point checklist and position sizing from your stop and risk percentage
• A leverage advisor with the allowed range and a recommended value
• If one rule fails, the entry stays closed and the reason is shown with its article number

During the trade
• The stop only moves toward profit; partial exits with fees
• Time stop, event log and a shareable note

After the exit
• R against the initial stop, rule compliance, main mistake, emotion and lesson
• Daily, weekly and monthly statistics, nine charts with a table view, per-setup performance

Discipline locks
• Cool-down after a loss, daily and weekly loss caps, drawdown lock, trade cap
• Rules change only inside a review session: tighter at once, looser after 24 hours

Privacy
• Journal, trades and balances stay on your phone. No account, no ads.
• Encrypted backup (AES-256-GCM) that you create and keep; CSV and JSON export
• App lock with fingerprint or PIN

Market (optional)
• Off until you turn it on. When on, it only reads prices from public services (CoinGecko, Binance, Nobitex, Wallex, alternative.me) and sends nothing from your journal.
• Prices in USD or Toman, sharp-move alerts that can ring, an economic calendar

The app is in Persian (right-to-left).

Andarz is a personal risk-management tool, not financial advice and not a signal service. It does not place trades and does not connect to your exchange account.
```

**Keywords for ASO** (Play has no keyword field; they are woven into the copy above):
fa — ژورنال ترید، مدیریت ریسک، حد ضرر، اندازه پوزیشن، فیوچرز، کریپتو، انضباط معاملاتی، قیمت تتر تومان.
en — trading journal, risk management, position size calculator, stop loss, crypto futures, trade log.

> The `en-US` listing must say the app UI is Persian (it does); otherwise users installing from the English
> listing would report "misleading". Play policy: the description must not promise returns (it does not).

## 2. Graphic assets checklist

- [ ] App icon 512 × 512 PNG, 32-bit, ≤ 1 MB. The launcher icon is a vector adaptive icon only
      (`mipmap-anydpi-v26`, `drawable/ic_launcher_*`); render it to 512 px (Android Studio → Image Asset, or
      export the foreground on the background colour). No PNG exists in the repo yet.
- [ ] Feature graphic 1024 × 500 JPG/PNG, no alpha
- [ ] Phone screenshots, 2–8, 16:9 or 9:16, min side ≥ 320 px, max ≤ 3840 px — Today, wizard gate, trade
      close, insights chart, rules session, Market prices (with the switch on), Settings internet card
- [ ] 7" and 10" tablet screenshots (optional; the layout is phone-first)
- [ ] Screenshots taken on a **device** in both themes (pending, MANUAL_TEST.md)

## 3. Privacy policy (host at a public HTTPS URL; required because the app has sensitive permissions)

```
Privacy Policy — Andarz
Last updated: <date of publication>

Andarz ("the app") is a personal trading journal and risk-management tool. This policy explains what the app
does with data.

1. Data you enter stays on your device.
Trades, journal notes, balances, rules, reviews and settings are stored only in the app's private storage on
your device. The app has no user accounts and no server. The developer cannot see, collect or recover this
data.

2. No analytics, no advertising, no tracking.
The app contains no analytics, advertising, crash-reporting or tracking SDK. Android's system backup is
disabled for the app, so its data is not uploaded to Google Drive or moved by device-to-device transfer.

3. The optional Market feature.
The Market section is off by default. If you turn on the internet switch and accept the consent screen, the
app requests public price data from: api.coingecko.com, api.alternative.me, data-stream.binance.vision,
api.nobitex.ir and api.wallex.ir. These requests contain only the coin symbols being displayed. Nothing from
your journal, trades or balances is sent. Like any internet request, these services receive your device's IP
address; their own privacy policies apply. Turning the switch off stops all requests.

4. Files you export.
Encrypted backups, CSV and JSON exports are created only when you ask, and are saved or shared to a place you
choose. The backup is encrypted with a password only you know; if you lose it, nobody can recover the backup.

5. Permissions.
Notifications (reminders and price alerts), biometric (app lock), exact alarms, full-screen alerts, display
over other apps and the foreground service are used only for features you turn on: the app lock, reminders,
price alarms and the live price monitor. Network access is used only by the Market feature.

6. Children.
The app is intended for adults (18+) and is not directed at children.

7. Deleting your data.
Uninstalling the app, or clearing its storage in Android settings, deletes all data. There is no account and
no copy elsewhere to delete.

8. Changes and contact.
Changes to this policy are published at this URL with a new date. Contact: <owner e-mail>.
```

Persian version: translate the same eight sections; the in-app wording of `about.privacy` (override) is the
reference tone. **Owner must fill** `<date>` and `<owner e-mail>` and host the page (P0 in AUDIT.md, R-04).

## 4. Data safety form (Play Console → App content → Data safety)

| Question | Answer | Evidence |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Journal data never leaves the device (K2, guards 2–4); Market requests carry only coin symbols (guard 5: no body, no `Authorization`/`Cookie`). Play's definition of "collect" is data transmitted off the device *to you or a third party*; public price queries carry no user data type of the list. |
| Is all user data encrypted in transit? | **Yes** (answer only appears if collecting; keep for reference) | HTTPS/WSS only, `cleartextTrafficPermitted=false` |
| Do you provide a way for users to request deletion? | **Yes — data is on device; uninstall or clear storage** | No account exists |
| Approximate location / IP | Not collected by the app. Third-party price hosts see the IP as part of any connection; disclose in the privacy policy (done, §3) | |

If Play review disagrees on the IP point, the fallback declaration is: *Device or other IDs — No*;
*App activity — No*; none shared. Do **not** declare "Financial info collected": it is stored locally only.

## 5. Other App content declarations

- [ ] **Ads:** No ads.
- [ ] **App access:** All functionality is available without login (no account). If the lock is on, it is
      user-set; reviewers start from a fresh install.
- [ ] **Target audience:** 18 and over only. Not designed for children; no "Teacher Approved".
- [ ] **Content rating (IARC questionnaire):** category *Utility/Productivity/Reference*; no violence, sexual
      content, profanity, drugs, gambling (it is not gambling: no money is wagered in the app), no user
      interaction or sharing between users, no location sharing, no digital purchases. Expected: PEGI 3 /
      Everyone.
- [ ] **Financial features declaration:** the app offers none of the listed features (no trading, loans,
      banking, crypto exchange, wallet or token). Select "My app doesn't provide any financial features" and,
      if a free-text field is offered, state: "price display and a personal, on-device trading journal; no
      exchange, no wallet, no order placement".
- [ ] **Government app:** No. **News app:** No (the calendar is a static list).
- [ ] **Health:** No.
- [ ] **Foreground service (specialUse):** describe "Real-time cryptocurrency price monitoring for
      user-configured instant price alerts; started only by the user; persistent notification with a stop
      action." Attach a short video of turning the monitor on and the notification appearing (device needed).
- [ ] **Full-screen intent (Android 14+):** the app is not an alarm/calling app by category, so Play will not
      pre-grant it. Declare it as alarm-style price alerts that the user arms; the code already falls back to
      a high-priority notification when `canUseFullScreenIntent()` is false (`AlarmRinger.kt:124`).
- [ ] **Exact alarm:** `SCHEDULE_EXACT_ALARM` (user-grantable), not `USE_EXACT_ALARM`; no declaration form,
      but justify in review notes.
- [ ] **SYSTEM_ALERT_WINDOW:** not a Play-restricted permission, but reviewers ask. Purpose: to hand a price
      alarm to the phone's Clock app from the background (`ClockRing.kt:39-52`); requested only when the user
      selects "ring through the Clock app". If Play objects, remove it — the app falls back to its own alarm
      (`AlarmRinger`) and loses nothing essential (AUDIT.md R-06).

## 6. Common rejection reasons checked

| Risk | Status |
|---|---|
| Missing or unreachable privacy policy URL | **Open — owner must host it** |
| Data safety mismatch with real behaviour | Matches code (§4) |
| Foreground service type not justified / no video | Open — video needs a device |
| Full-screen intent misuse | Fallback present; declaration text above |
| Misleading claims ("guaranteed profit", "no internet") | None; guard 9 blocks "no internet" |
| Target API below requirement | targetSdk 35 (meets the Aug 2025 requirement) |
| Crashes on launch on review devices | Not yet run on a device — **P0** |
| App signed with debug key | Current builds are `-unsigned` / NOT PUBLISHABLE — **P0 until O-2** |
| Package name `ir.andarz.app` already taken / not final | **O-1 open** |
