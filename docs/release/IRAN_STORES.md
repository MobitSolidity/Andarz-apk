# Publishing on Myket and Cafe Bazaar (D-95..D-98)

The owner's decision (2026-10-27): publish on **Myket** and **Cafe Bazaar** only; Google Play is not a target for
now (`STORE_LISTING.md` and `RELEASE.md` stay as reference for a later Play release). Every rule below was read
from the stores' own pages on 2026-10-27; the source is linked next to it. Nothing here was verified inside a
developer panel, because that needs the owner's login: those steps are marked **(owner)**.

## 0. Identity of the app

| Item | Value | Note |
|---|---|---|
| Package name (`applicationId`) | `ir.mobitmaster.andarz` | D-95. **Can never change** after the first upload on either store. Checked 2026-10-27: `myket.ir/app/ir.mobitmaster.andarz` and `cafebazaar.ir/app/ir.mobitmaster.andarz` both return 404 (free), while a known app returns 200 on both. A store can still refuse it at upload; reserve it first (§2). |
| App name | اندرز / Andarz | |
| Version | `andarz.versionName` in `gradle.properties`; `versionCode` = commit count | Both stores require a higher versionCode for every update |
| minSdk / targetSdk | 26 / 36 | Myket requires targetSdk ≥ 34 for new apps and updates from 1 Aban 1405 ([source](https://myket.ir/kb/pages/target-sdk-34/)); Bazaar ties its minimum to Google's ([source](https://developers.cafebazaar.ir/fa/guidelines/developers-panel/common-errors)). 36 satisfies both. |
| Signing key | the owner's own keystore (not in git) | **Use the same key on both stores, for ever.** Android refuses an update signed with a different key, and Bazaar rejects an update not signed "with the same key as the last published package" ([source](https://developers.cafebazaar.ir/fa/guidelines/developers-panel/common-errors)). Same key on both stores also lets a user move between them without uninstalling. |
| Support e-mail | mobitmaster@gmail.com | Required in Myket's contact field ([source](https://myket.ir/kb/pages/app-release/)) |
| Privacy policy URL | https://mobitsolidity.github.io/Andarz-apk/ | Generated from the in-app text (§4) |
| Price | Free, no in-app purchase | Myket: an app published free can never become paid later ([source](https://myket.ir/kb/pages/app-release/)) |
| Category | Finance (مالی / اقتصادی) | Pick the closest category in each panel |

## 1. Build the file to upload (owner, on your computer)

Both stores take a signed **APK** (Bazaar also accepts an AAB, but only after signing it with Bazaar's own
bundle-signer tool into a `.bin`; the APK path is simpler and identical for both stores).

```bash
git pull                       # main, clean tree
# keystore.properties at the repo root (git-ignored) — storeFile, storePassword, keyAlias, keyPassword
./gradlew clean build assembleRelease --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Checks before uploading (all must pass):

```bash
APK=app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump badging $APK | head -1
#   package: name='ir.mobitmaster.andarz' versionCode='…' versionName='0.12.0'   <- no "-unsigned"
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs $APK | grep -E 'Signer #1 certificate (DN|SHA-256)'
#   DN must be YOUR certificate, never "CN=Android Debug"
$ANDROID_HOME/build-tools/35.0.0/zipalign -c -P 16 -v 4 $APK | tail -1     # "Verification succesful"
sha256sum $APK > app-release.apk.sha256
```

(Use whatever build-tools version is installed under `$ANDROID_HOME/build-tools/`.) If the Gradle output shows the
**NOT PUBLISHABLE** box, the keystore was not found and the APK is debug-signed: do not upload it.

## 2. Myket (owner)

Steps from Myket's own guide ([قدم به قدم تا انتشار برنامه](https://myket.ir/kb/pages/app-release/)):

1. Developer panel → *برنامه جدید*. Optionally *رزرو شناسه* first with `ir.mobitmaster.andarz` to hold the
   package name before the final APK.
2. Upload the signed APK (§1).
3. Basic info: type *برنامه*; category; icon; Persian **and** English title; description (§5); contact
   e-mail **mobitmaster@gmail.com**; price **رایگان**.
4. Package info: *تغییرات نسخه* is mandatory, e.g. «نسخهٔ اولیه».
5. Screenshots (§6). Optional video: upload to Aparat and paste the link.
6. Final review → *ارسال برای بررسی*. In the reviewer note box paste the text of §7 (permissions) and §8
   (licence question).
7. Review takes up to 3 working days; the result arrives by e-mail and in *پیام‌ها*.

Myket rules that apply to Andarz, and where the app stands:

| Rule (source: [privacy-security-access-info](https://myket.ir/kb/pages/privacy-security-access-info-fa/)) | Andarz |
|---|---|
| Privacy text inside the app, easy to find | Legal screen from More, Settings and About (D-96) |
| Consent by a direct user action (tap or tick); back/home or timeouts never count | First-run gate: tick box **and** button; no auto-accept (D-96) |
| Sensitive permissions only if the feature needs them; explain them in the app; ask at runtime; respect a refusal and offer an alternative | Each special permission is asked only when its switch is on, with its own one-line reason, and the app falls back to its own ring when refused (§7) |
| A sensitive permission that looks unrelated is rejected unless explained to the reviewer via ticket | §7 text, paste it in the reviewer note or a ticket |
| Links in the app/description must not point to other stores | None in the app; do not add Play/Bazaar links to the Myket description |
| No selling of user data; data use disclosed | No data leaves the phone except coin symbols to price hosts (disclosed) |
| Apps whose activity needs a licence must present it ([مجوزها](https://myket.ir/kb/pages/app-authorizations/)) | Unclear for a price-display + journal app. Ask first (§8) |

## 3. Cafe Bazaar (owner)

Bazaar's privacy rule ([حریم خصوصی کاربران](https://developers.cafebazaar.ir/fa/app-publish-guidelines/rules/privacy))
matches Myket's: privacy text inside the app and easy to reach; disclose every third party that receives data
(the five price hosts are named); transfer over modern encryption (HTTPS only, enforced by
`EgressInterceptor` and `network_security_config`); ask permissions at runtime; explicit consent by a direct
action. Andarz meets each point the same way as for Myket.

1. Bazaar developer panel (pishkhan) → new app → upload the **same** signed APK.
2. Fill title, descriptions (§5), category, contact e-mail, screenshots (§6).
3. Release note, e.g. «نسخهٔ اولیه».
4. In the note to the reviewer paste §7 and §8.
5. An APK rejected by review must be deactivated before a fixed one can be published (one active package at a
   time).

## 4. Hosted privacy policy

`docs/index.html` is generated by `python3 tools/legal/build_legal_site.py` from the `legal` blocks of
`content_app_fa.json` / `content_app_en.json`; `LegalContentTest` fails if it is stale. To publish it
**(owner, once)**: GitHub → repository *Andarz-apk* → *Settings → Pages* → *Build and deployment*:
Source **Deploy from a branch**, Branch **main**, folder **/docs** → Save. After a minute
https://mobitsolidity.github.io/Andarz-apk/ shows the page (`docs/.nojekyll` stops Jekyll from processing the
other Markdown in `docs/`). Check it opens before submitting to either store.

When the meaning of the terms or the policy changes: edit both `legal` blocks, **raise `legal.version`** (the
app then asks every user to accept again), update `updated`, rerun the script, commit.

## 5. Listing text

### Title
- fa: `اندرز — ژورنال ترید و مدیریت ریسک`
- en: `Andarz: Trading Journal & Risk`

### Short description (one line)
- fa: `قبل از ورود به معامله «بله» یا «نه» می‌گوید؛ ژورنال و مدیریت ریسک روی گوشی خودت`
- en: `Says yes or no before you enter a trade. A trading journal and risk rules on your phone`

### Full description — fa

```
اندرز برای هر تریدری است که می‌خواهد قوانین معاملهٔ خودش را بنویسد و به آن پایبند بماند: پیش از ورود جلوی معاملهٔ بی‌قاعده را می‌گیرد و بعد از خروج اشتباه‌های تکراری را نشان می‌دهد. اول بقا، بعد سود.

پیش از ورود
• چک‌لیست هشت‌بندی و محاسبهٔ اندازهٔ پوزیشن از روی حد ضرر و درصد ریسک
• مشاور اهرم با بازهٔ مجاز و اهرم پیشنهادی
• اگر یکی از قیدها نخواند، ورود بسته می‌ماند و دلیلش با شمارهٔ ماده و عدد نوشته می‌شود

حین معامله
• حد ضرر فقط به سمت سود جابه‌جا می‌شود؛ خروج پله‌ای با کارمزد
• توقف زمانی، رویدادها و یادداشت قابل اشتراک

بعد از خروج
• R نسبت به حد ضرر اولیه، رعایت قوانین، اشتباه اصلی، احساس و درس
• آمار روزانه، هفتگی و ماهانه، نمودارها و عملکرد هر ستاپ

قفل‌های انضباطی
• خنک‌شدن بعد از ضرر، سقف ضرر روزانه و هفتگی، قفل افت سرمایه، سقف تعداد معامله
• قوانین فقط در جلسهٔ بازبینی عوض می‌شوند: سخت‌گیرانه‌تر فوراً، آسان‌گیرانه‌تر بعد از ۲۴ ساعت

آموزش
• اساس‌نامهٔ نمونهٔ ۱۳ ماده‌ای که همهٔ عددهایش قابل تنظیم است
• واژه‌نامهٔ ۱۶۵ اصطلاح ترید و صرافی، فارسی و انگلیسی

حریم خصوصی
• ژورنال، معامله‌ها و موجودی فقط روی گوشی خودت می‌ماند؛ بدون حساب کاربری و بدون تبلیغ
• پشتیبان رمزگذاری‌شده (AES-256-GCM) که خودت می‌سازی و نگه می‌داری؛ خروجی CSV و JSON
• قفل برنامه با اثر انگشت یا رمز؛ پنهان کردن مبلغ‌ها با یک لمس

بخش بازار (اختیاری)
• تا خودت روشنش نکنی خاموش است. وقتی روشن باشد فقط قیمت‌ها را از سرویس‌های عمومی می‌خواند و هیچ چیز از ژورنال نمی‌فرستد.
• قیمت به دلار یا تومان، هشدار حرکت تند قیمت که می‌تواند مثل زنگ ساعت به صدا درآید، پایش لحظه‌ای قیمت، تقویم رویدادهای اقتصادی

فارسی و انگلیسی، تم تیره و روشن.

اندرز ابزار مدیریت ریسک است، نه توصیهٔ مالی و نه سیگنال. با هیچ صرافی‌ای معامله نمی‌کند و به حساب صرافی‌ات وصل نمی‌شود. معامله با اهرم پرریسک است.
پشتیبانی: mobitmaster@gmail.com
```

### Full description — en

```
Andarz is for any trader who wants to write down their own trading rules and stick to them: before you enter, it blocks a trade that breaks your rules; after you exit, it shows the mistakes you keep repeating. Survive first, profit second.

Before the entry
• An eight-point checklist and position sizing from your stop and risk percentage
• A leverage advisor with the allowed range and a recommended value
• If one rule fails, the entry stays closed and the reason is shown with its article number

During the trade
• The stop only moves toward profit; partial exits with fees
• Time stop, event log and a shareable note

After the exit
• R against the initial stop, rule compliance, main mistake, emotion and lesson
• Daily, weekly and monthly statistics, charts and per-setup performance

Discipline locks
• Cool-down after a loss, daily and weekly loss caps, drawdown lock, trade cap
• Rules change only inside a review session: tighter at once, looser after 24 hours

Learn
• A sample 13-article trading constitution whose every number can be adjusted
• A glossary of 165 trading and exchange terms

Privacy
• Journal, trades and balances stay on your phone. No account, no ads.
• Encrypted backup (AES-256-GCM) that you create and keep; CSV and JSON export
• App lock with fingerprint or PIN; hide amounts with one tap

Market (optional)
• Off until you turn it on. When on, it only reads prices from public services and sends nothing from your journal.
• Prices in USD or Toman, sharp-move alerts that can ring like an alarm clock, live price monitoring, an economic calendar

Persian and English, dark and light themes.

Andarz is a risk-management tool, not financial advice and not a signal service. It places no trades and does not connect to your exchange account. Leveraged trading is high risk.
Support: mobitmaster@gmail.com
```

Every claim above was checked against the code: 165 glossary terms (`glossary.json`), 13 articles
(`content_fa.json`), AES-256-GCM (`BackupCrypto.kt`), Market off by default (`MarketPrefs.enabled = false`,
`mayUseNetwork` needs the consent stamp). Do not write "no internet" or "offline only" (false, and guard 9 forbids
it in the app). Do not promise profit.

## 6. Graphics checklist (owner)

- [ ] Icon 512 × 512 PNG. The launcher icon is a vector adaptive icon; export it from Android Studio
      (*Image Asset*) or render the foreground on its background colour.
- [ ] 4–8 phone screenshots from a real device, portrait: Today, the entry wizard with a "no" reason, a closed
      trade, insights chart, the Learn glossary, Market prices (switch on), the legal screen.
- [ ] Optional: a 30–60 s video on Aparat (Myket and Bazaar both take Aparat links).

## 7. Permission justification for reviewers (paste into the reviewer note)

```
توضیح دسترسی‌های اندرز (ir.mobitmaster.andarz)

اندرز ژورنال معاملاتی و ابزار مدیریت ریسک است و بخش اختیاری «بازار» قیمت رمزارزها را نشان می‌دهد و هشدار قیمت می‌دهد. همهٔ دسترسی‌های حساس فقط وقتی درخواست می‌شوند که کاربر قابلیت مربوط را خودش روشن کند، هرکدام با توضیح جداگانه در همان صفحه، و اگر کاربر رد کند برنامه با جایگزین کار می‌کند.

- INTERNET و ACCESS_NETWORK_STATE: فقط برای بخش بازار که پیش‌فرض خاموش است و پس از تأیید صفحهٔ رضایت روشن می‌شود. فقط درخواست خواندن قیمت (GET، HTTPS) به CoinGecko، Binance، نوبیتکس، والکس و alternative.me فرستاده می‌شود.
- POST_NOTIFICATIONS: یادآورهای بازبینی و هشدار قیمت؛ اولین بار که کاربر هشدار یا یادآور را روشن کند پرسیده می‌شود.
- FOREGROUND_SERVICE و FOREGROUND_SERVICE_SPECIAL_USE و WAKE_LOCK: «پایش لحظه‌ای قیمت» که کاربر خودش روشن می‌کند؛ در تمام مدت یک اعلان ثابت با دکمهٔ توقف دیده می‌شود.
- RECEIVE_BOOT_COMPLETED: برگرداندن یادآورها و پایش (اگر روشن بوده) پس از راه‌اندازی دوبارهٔ گوشی یا به‌روزرسانی برنامه.
- SCHEDULE_EXACT_ALARM و USE_FULL_SCREEN_INTENT: هشدار قیمت به شکل زنگ هشدار روی صفحهٔ قفل؛ با دکمهٔ جداگانه در صفحهٔ هشدارها، و بدون آن اعلان معمولی نمایش داده می‌شود.
- SYSTEM_ALERT_WINDOW و com.android.alarm.permission.SET_ALARM: گزینهٔ «زنگ از برنامهٔ ساعت گوشی»؛ هشدار قیمت مثل زنگ بیدارباش از ساعت خود گوشی پخش می‌شود و رویدادهای تقویم اقتصادی به ساعت گوشی سپرده می‌شوند. اندروید بدون «نمایش روی برنامه‌های دیگر» اجازه نمی‌دهد ساعت از پس‌زمینه باز شود؛ این اجازه فقط وقتی همین گزینه روشن باشد از تنظیمات اندروید خواسته می‌شود و بدون آن زنگ داخلی اندرز پخش می‌شود.
- USE_BIOMETRIC (و USE_FINGERPRINT برای اندروید ۸ و ۹، از کتابخانهٔ androidx.biometric) و VIBRATE: قفل برنامه و بازخورد لمسی.

اندرز به مخاطبین، پیامک، تماس، موقعیت مکانی، دوربین، میکروفون، فایل‌ها یا فهرست برنامه‌های نصب‌شده دسترسی ندارد، حساب کاربری و تبلیغ ندارد و هیچ داده‌ای از ژورنال کاربر به جایی فرستاده نمی‌شود. متن کامل حریم خصوصی داخل برنامه (منوی «بیشتر» ← قوانین و حریم خصوصی) و در https://mobitsolidity.github.io/Andarz-apk/ است.
```

Each line was matched against the merged release manifest (guard 1 allow-list in `app/build.gradle.kts`) and
the code that requests it (`AlertsScreen.kt`, `AlarmRinger.kt`, `ClockRing.kt`, `Controls.kt`,
`LiveService.kt`, `BootReceiver`).

## 8. Licence question — ticket text (send before the first submission)

Myket says apps in sensitive fields may need a licence from the Digital Media Organisation (سامانهٔ سرآمد /
ساماندهی) and that finding out is the developer's responsibility
([مجوزهای برنامه](https://myket.ir/kb/pages/app-authorizations/)). Whether a price-display + journal app needs
one is not stated anywhere public. Send this as a ticket in each panel (Myket: *تیکت‌ها*; Bazaar: *ثبت تیکت*):

```
سلام. قصد انتشار برنامهٔ «اندرز» با شناسهٔ ir.mobitmaster.andarz را دارم و پیش از ارسال برای بررسی می‌خواهم مطمئن شوم مجوز خاصی لازم است یا نه.

کارکرد برنامه:
- ژورنال معاملاتی و ماشین‌حساب مدیریت ریسک که کاملاً روی گوشی کاربر کار می‌کند (بدون حساب کاربری، بدون سرور، بدون تبلیغ).
- بخش اختیاری «بازار» که فقط قیمت عمومی رمزارزها و نرخ تتر را از سرویس‌های عمومی (CoinGecko، Binance، نوبیتکس، والکس) نمایش می‌دهد و هشدار قیمت می‌دهد.

برنامه هیچ خرید و فروش، واریز، برداشت، نگهداری دارایی، اتصال به حساب صرافی یا سیگنال و توصیهٔ سرمایه‌گذاری ندارد و پرداخت درون‌برنامه‌ای هم ندارد. شرایط استفاده، حریم خصوصی و هشدار ریسک داخل برنامه است و کاربر پیش از استفاده آن را می‌پذیرد.

آیا برای انتشار این برنامه مجوز سرآمد/ساماندهی یا مجوز دیگری لازم است؟ اگر لازم است، کدام مجوز؟
با تشکر — mobitmaster@gmail.com
```

## 9. After publishing

- Every update: bump `andarz.versionName`, build signed with the **same** key, upload to **both** stores.
- Keep the APK, its `.sha256` and `mapping.txt` per version (retrace crash stacks reported by users).
- Watch reviews and the support inbox; there is no crash SDK (D-86), so user reports are the only signal.
