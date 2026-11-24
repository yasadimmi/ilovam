# NetScope — Wi-Fi / Bluetooth tarmoq tahlili (root talab qilinmaydi)

NetScope — **faqat passiv kuzatuv** ilovasi: Android'ning ochiq API'lari beradigan Wi-Fi/Bluetooth ma'lumotlarini o'qiydi.
Hujum, deauth, paket yuborish, boshqa qurilmalarni skanerlash **yo'q**. Tekshiruv faqat o'z telefoningiz va ulangan routeringiz (shlyuz) bilan cheklangan.
Hamma narsa oflayn, faqat telefonda saqlanadi (Room/SQLite). Backend, akkaunt, AI yo'q. Internet faqat tezlik testi uchun.

## Imkoniyatlar
Wi-Fi skaner · Kanal bandligi grafigi · Signal issiqlik xaritasi · BLE/Bluetooth skaner · Tezlik testi ·
Tarix va signal tendensiyasi · Tarmoq sifati monitori (foreground xizmat) · Ulanish paneli ·
Wi-Fi nuqtalari ogohlantirishi · QR ulashish/skanerlash · CSV/JSON eksport va JSON import.

## Telefondan GitHub'ga yuklash (kompyutersiz)

### 1-usul (eng oson): ZIP orqali
1. Telefon brauzerida https://github.com ga kiring → **Sign up / Sign in**.
2. Yuqoridagi **+ → New repository** → nom: `NetScope` → **Create repository**.
3. Repo sahifasida **uploading an existing file** havolasini bosing → `NetScope.zip` faylini tanlang → **Commit changes**.
   (GitHub ZIP'ni ochmaydi — bu normal. Workflow uni o'zi ochadi.)
4. **Add file → Create new file**. Fayl nomi maydoniga aynan shuni yozing: `.github/workflows/android.yml`
   (`/` yozganda papkalar avtomatik yaratiladi). Ttarkibiga ilova bilan birga berilgan `android.yml` faylining matnini joylang → **Commit changes**.
5. Shu commit'dan so'ng **Actions** yorlig'ida "Android CI" ishga tushadi.

### 2-usul: Git mijoz ilovasi
Android uchun Termux yoki MGit ilovasida repo'ni clone qiling, ZIP ichidagi fayllarni repo ichiga ochib, `git add . && git commit && git push` qiling.
Bu holda ZIP'ning o'zini repo'ga qo'shmang.

## APK qanday quriladi va qayerdan olinadi
* Har bir `push` da GitHub Actions JDK 17 + Android SDK bilan `./gradlew assembleDebug` ni ishga tushiradi (Gradle keshi yoqilgan).
* **Actions** → oxirgi ish (yashil ✓) → pastda **Artifacts** → `NetScope-debug-apk` → yuklab oling (ZIP ichida `app-debug.apk`).
* Birinchi qurish 5–10 daqiqa olishi mumkin.

### Imzolangan release (versiya tegi)
1. Keystore yaratish kerak (bir marta). Telefonda qulay yo'l: GitHub'da **Actions → Run workflow** orqali emas, balki Termux'da:
   `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias netscope`
   so'ng `base64 -w0 release.jks` natijasini nusxalang.
2. Repo → **Settings → Secrets and variables → Actions → New repository secret**:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. **Releases → Create a new release** → yangi teg yozing (masalan `v1.0.0`) → Publish.
   Workflow imzolangan APK ni quradi va **Releases** sahifasiga biriktiradi.
   Keystore'ni yo'qotmang: yangilanishlar bir xil kalit bilan imzolanishi kerak.

## Android'ga o'rnatish
1. APK'ni yuklab oling (Releases sahifasidan to'g'ridan-to'g'ri `.apk`; Actions artifakti bo'lsa avval ZIP'ni oching).
2. Faylni bosing. Android so'rasa: **Sozlamalar → "Noma'lum ilovalarni o'rnatish"** (Install unknown apps) → brauzer/Fayllar ilovasi uchun yoqing → orqaga qaytib **O'rnatish**.
3. Debug APK Play Protect ogohlantirishini ko'rsatishi mumkin — "Baribir o'rnatish".

## Ruxsatlar (nega kerak)
| Ruxsat | Sabab |
|---|---|
| ACCESS_FINE_LOCATION | Android Wi-Fi/BLE skan natijalarini faqat shu ruxsat bilan beradi |
| ACCESS_WIFI_STATE / CHANGE_WIFI_STATE | Wi-Fi holati, skan boshlash, QR orqali tarmoq qo'shish |
| BLUETOOTH_SCAN / BLUETOOTH_CONNECT | Bluetooth qurilmalarini topish va nomini o'qish (Android 12+) |
| ACCESS_NETWORK_STATE | Ulanish paneli, shlyuz IP |
| READ_PHONE_STATE | Faqat 4G/5G turi va mobil signal. Rad etilsa — faqat operator nomi |
| POST_NOTIFICATIONS, FOREGROUND_SERVICE | Monitoring xizmati va ogohlantirishlar |
| CAMERA | QR skanerlash (tasvir qurilmadan chiqmaydi) |
| INTERNET | Faqat tezlik testi (oddiy ruxsat, so'ralmaydi) |

Har bir ruxsatdan oldin ilova o'zbekcha tushuntirish oynasini ko'rsatadi.

## Muammolarni hal qilish
* **Wi-Fi ro'yxati bo'sh** — telefonda Joylashuv (GPS) xizmatini yoqing va joylashuv ruxsatini bering; Android skanerni ~2 daqiqada 4 martagacha cheklaydi.
* **Bluetooth qurilmalar ko'rinmayapti** — Bluetooth yoqilganini va "Yaqin atrofdagi qurilmalar" ruxsatini tekshiring.
* **Mobil tarmoq turi "ruxsat kerak"** — Ulanish yorlig'idan Telefon holati ruxsatini bering.
* **Monitoring to'xtab qolyapti** — Sozlamalar → Ilovalar → NetScope → Batareya → "Cheklovsiz". Ba'zi ishlab chiqaruvchilar (Xiaomi, Huawei, Samsung) fon xizmatlarini o'chiradi.
* **Tezlik testi xato beradi** — Sozlamalar'da test manzillarini tekshiring; `https://` bilan boshlanishi va upload manzili POST qabul qilishi shart.
* **Build xatosi (Actions qizil ✗)** — ishni oching, qizil qadamni kengaytiring va xato matnini qidiring. Odatda: noto'g'ri nomlangan workflow fayli (`.github/workflows/android.yml`), yoki release uchun secrets kiritilmagan.
* **"KEYSTORE_BASE64 secret topilmadi"** — faqat `v...` tegi uchun; oddiy push'lar debug APK quradi va secrets talab qilmaydi.
* **Xaritada tarmoq signali topilmadi** — tanlangan tarmoq diapazonida bo'ling, "Tarmoqlar" bo'limida skanerlang.

## OUI jadvali haqida
`app/src/main/assets/oui.csv` — qisqa, qo'lda tuzilgan namunaviy jadval (`XXXXXX,Ishlab chiqaruvchi`). Noma'lum prefikslar "Noma'lum" deb ko'rsatiladi.
To'liq IEEE ro'yxatini shu formatda qo'yib, jadvalni kengaytirishingiz mumkin. Lokal/tasodifiy MAC'lar alohida belgilanadi.

## Loyiha tuzilmasi
```
NetScope/
├─ .github/workflows/android.yml
├─ build.gradle.kts  settings.gradle.kts  gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties
└─ app/
   ├─ build.gradle.kts  proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ assets/oui.csv
      ├─ res/ (values/strings.xml, themes.xml, colors.xml, drawable, mipmap-anydpi-v26)
      └─ java/com/netscope/
         ├─ NetScopeApp.kt  MainActivity.kt
         ├─ data/Db.kt                      (Room: entity, DAO, baza)
         ├─ service/MonitorService.kt
         ├─ util/  Prefs, PermUtil, Net(+WifiUtil, Oui), Notifier, DeviceAlert, SpeedTester, Exporter, QrUtil
         └─ ui/    Theme, PermissionUi, Charts, WifiScreens, HeatmapScreen, BluetoothScreen,
                   SpeedScreen, HistoryScreen, MonitorScreen, SettingsScreen, QrScreens
```
