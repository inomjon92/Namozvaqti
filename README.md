# Namoz vaqtlari — Katta shrift

Keksalar uchun Android namoz vaqtlari ilovasi: katta shrift, yuqori kontrast, oddiy menyu. Bepul, reklamasiz, internetsiz ishlaydi.

## Imkoniyatlar

- O'zbekiston bo'yicha 14 hudud va ~50 shahar, Hanafiy usulida hisoblash (Bomdod/Xufton 15°), har bir vaqtni daqiqaga to'g'rilash.
- Bosh sahifa: keyingi namoz katta raqamlarda va qolgan vaqt, bugungi 6 vaqt, ovoz bilan o'qib berish.
- Oylik jadval, Ro'za taqvimi (har yili hijriy kalendardan o'zi hisoblanadi, ±1 kun tuzatish), saharlik va iftor eslatmalari, duolar.
- Qibla kompasi.
- Har bir namoz uchun: o'chiq / bildirishnoma / ovoz; ovoz tanlash (ilova melodiyalari, telefon ohanglari, telefondagi istalgan audio fayl, masalan azon mp3).
- Ovozli e'lon: "Peshin namozi vaqti kirdi" (ichki erkak ovozi yoki o'zingiz yozib olgan ovoz).
- Ovoz chalinayotganda telefonning ovoz tugmasi bosilsa, darhol o'chadi. Qulflangan ekranda katta "TO'XTATISH" tugmasi.
- Sozlamalarda shriftni 80–160% oralig'ida kattalashtirish/kichraytirish, yorug'/qorong'i mavzu.

## Yig'ish

- Android Studio yoki `gradle assembleDebug` (GitHub Actions avtomatik yig'adi, APK "Artifacts" bo'limida).
- Gradle'siz: `tools/build-local.sh` (aapt2, dx, apksigner va API 34 android.jar kerak).
- Melodiyalar, e'lonlar va ikonka `tools/gen_assets.py` bilan yaratilgan (ffmpeg, espeak-ng).
