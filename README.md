# Namoz vaqtlari — Katta shrift

Keksalar uchun Android namoz vaqtlari ilovasi: katta shrift, yuqori kontrast, oddiy menyu. Bepul, reklamasiz, internetsiz ishlaydi.

## Imkoniyatlar

- O'zbekiston bo'yicha 14 hudud va ~50 shahar, Hanafiy usulida hisoblash (Bomdod/Xufton 15°), har bir vaqtni daqiqaga to'g'rilash.
- Bosh sahifa: keyingi namoz katta raqamlarda va qolgan vaqt, bugungi 6 vaqt, ovoz bilan o'qib berish.
- Oylik jadval, Ro'za taqvimi (har yili hijriy kalendardan o'zi hisoblanadi, ±1 kun tuzatish), duolar.
- Ramazonda saharlik (Bomdoddan 30 daqiqa oldin) va iftor eslatmalari namoz vaqtlari kabi: qo'ng'iroqcha va ovoz tanlash.
- Qibla kompasi (giroskopsiz arzon telefonlarda ham: akselerometr + magnit datchigi).
- Har bir vaqt yonida qo'ng'iroqcha (ovozni o'chirish/yoqish) va ovoz tanlash: o'z ovozingizni yozib olish, ilova melodiyalari, telefon ohanglari, telefondagi istalgan audio fayl (azon, qo'shiq mp3).
- Ovoz chalinayotganda telefonning ovoz tugmasi bosilsa, darhol o'chadi. Qulflangan ekranda katta "TO'XTATISH" tugmasi.
- Sozlamalarda shriftni 80–160% oralig'ida kattalashtirish/kichraytirish, yorug'/qorong'i mavzu.

## Yig'ish

- Android Studio yoki `gradle assembleDebug` (GitHub Actions avtomatik yig'adi, APK "Artifacts" bo'limida).
- Gradle'siz: `tools/build-local.sh` (aapt2, dx, apksigner va API 34 android.jar kerak).
- Melodiyalar va ikonka `tools/gen_assets.py` bilan yaratilgan (ffmpeg, Pillow).
