# فندق (Fandogh)

کلاینت VPN اندروید مبتنی بر هسته [Xray](https://github.com/XTLS/Xray-core)، ساخته‌شده روی پایه [v2rayNG](https://github.com/2dust/v2rayNG).

طراحی‌شده برای اتصال به پنل **3x-ui (سنایی)** از طریق لینک اشتراک (subscription).

---

## دریافت APK

نیازی به نصب Android Studio نیست. APK روی GitHub Actions ساخته می‌شود:

1. به تب **Actions** این مخزن بروید
2. workflow با نام **Build Fandogh APK** را انتخاب کنید
3. روی **Run workflow** بزنید (یا منتظر بمانید تا با هر push خودکار اجرا شود)
4. پس از پایان اجرا، از بخش **Artifacts** فقط موردی که لازم دارید را دانلود کنید

| Artifact | مناسب برای | حجم |
|---|---|---|
| **`Fandogh-arm64-v8a`** | تقریباً همه گوشی‌های امروزی ✅ | ~۳۹ مگ |
| `Fandogh-armeabi-v7a` | گوشی‌های قدیمی ۳۲ بیتی | ~۴۰ مگ |
| `Fandogh-universal` | هر دستگاهی، ولی حجیم‌تر | ~۸۱ مگ |
| `Fandogh-x86` | شبیه‌ساز (امولاتور) | ~۴۰ مگ |

اگر مطمئن نیستید، **`Fandogh-arm64-v8a`** را بگیرید.

هر artifact یک فایل zip است؛ از داخلش APK را بیرون بکشید و روی گوشی نصب کنید.
اندروید هنگام نصب هشدار «منبع ناشناس» می‌دهد که طبیعی است.

---

## اتصال به پنل 3x-ui

1. در پنل، برای کاربر یک **Subscription URL** بسازید
2. در اپ: **+** ← **افزودن از لینک اشتراک** ← لینک را وارد کنید
3. **به‌روزرسانی اشتراک** را بزنید تا سرورها بیایند
4. یک سرور انتخاب و دکمه اتصال را بزنید

اولین بار اندروید اجازه ساخت VPN می‌خواهد؛ تأیید کنید.

---

## ساخت روی سیستم خودتان

اگر خواستید محلی build بگیرید:

```bash
git clone --recursive https://github.com/MAHDI-byte64/Fandogh.git
cd Fandogh

export NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
bash compile-hevtun.sh
cp -r libs V2rayNG/app/

# libv2ray.aar را از releases پروژه AndroidLibXrayLite بگیرید
# و در V2rayNG/app/libs/ قرار دهید

cd V2rayNG
./gradlew assemblePlaystoreDebug
```

خروجی در `V2rayNG/app/build/outputs/apk/playstore/debug/` ساخته می‌شود.

---

## ساختار پروژه

```
Fandogh/
├── V2rayNG/                 اپ اندروید (Kotlin + Compose)
│   └── app/
│       ├── src/main/java/   کد اصلی
│       ├── src/main/res/    رابط کاربری، رشته‌ها، تم
│       └── libs/            libv2ray.aar + کتابخانه‌های نیتیو
├── AndroidLibXrayLite/      زیرماژول: هسته Xray برای اندروید
├── hev-socks5-tunnel/       زیرماژول: تونل tun2socks نیتیو
├── compile-hevtun.sh        اسکریپت build کتابخانه نیتیو
└── .github/workflows/       ساخت خودکار APK
```

---

## شخصی‌سازی

| چه چیزی | کجا |
|---|---|
| نام نمایشی اپ | `V2rayNG/app/src/main/res/values/strings.xml` ← `app_name` |
| شناسه بسته | `V2rayNG/app/build.gradle.kts` ← `applicationId` |
| آیکون | `V2rayNG/app/src/main/res/mipmap-*/` |
| رنگ‌ها و تم | `V2rayNG/app/src/main/res/values/colors.xml` |
| متن‌های فارسی | `V2rayNG/app/src/main/res/values-fa/strings.xml` |

⚠️ `namespace` را در `build.gradle.kts` تغییر ندهید. کتابخانه نیتیو با
`-DPKGNAME=com/v2ray/ang/service` کامپایل می‌شود و تغییر نام بسته باعث
می‌شود ثبت JNI در زمان اجرا بشکند. برای جدا شدن از v2rayNG تغییر
`applicationId` کافی است.

---

## امضای APK برای انتشار

APK فعلی با کلید debug امضا می‌شود — روی گوشی نصب می‌شود ولی برای انتشار
عمومی مناسب نیست. برای امضای واقعی:

```bash
keytool -genkey -v -keystore fandogh.jks -keyalg RSA \
        -keysize 2048 -validity 10000 -alias fandogh
base64 -w0 fandogh.jks    # خروجی را در Secrets مخزن ذخیره کنید
```

سپس در workflow از `assemblePlaystoreRelease` به همراه پارامترهای
`android.injected.signing.*` استفاده کنید.

---

## مجوز

این پروژه فورکی از v2rayNG است و تحت **GPLv3** منتشر می‌شود. طبق این مجوز
فورک کردن، تغییر و تغییر نام کاملاً مجاز است، به شرط آنکه کد منبع باز
بماند و همین مجوز حفظ شود. فایل [LICENSE](LICENSE) را دست نزنید.
