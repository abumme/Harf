# Hisobni va ma'lumotlarni o'chirish — «Harf»

**Tahrir sanasi:** 2026-09-08

Bu sahifa «Harf» mobil ilovasida hisobingizni va unga bog'liq ma'lumotlarni qanday o'chirishni tushuntiradi.

**Ma'lumotlar operatori:** Islomov Mehrojbek, jismoniy shaxs, O'zbekiston Respublikasi. Email: lazydevscat@gmail.com.

## O'chirishni qanday so'rash mumkin

**Ilovada (darhol):**
1. **Sozlamalar** bo'limini oching.
2. **Hisobni o'chirish** tugmasini bosing.
3. Tasdiqlang. Serverdagi hisob va ma'lumotlar darhol o'chiriladi, qurilmadagi mahalliy natijalar tozalanadi.

**Email orqali (ilovaga kira olmasangiz):**
Istalgan manzildan **lazydevscat@gmail.com** ga "Harf hisobimni o'chiring" mavzusi bilan so'rov yuboring. Hisoblar anonim bo'lgani uchun uni topishga yordam beradigan tafsilotlarni qo'shing (masalan, birinchi foydalanish sanasi yoki kirgan Google hisobingiz). Bunday so'rovlar 30 kun ichida ko'rib chiqiladi.

## Nima o'chiriladi

O'chirishda serverdan quyidagilar olib tashlanadi:

- anonim hisob identifikatori (tasodifiy UUID);
- kirish identifikatori (Google/Apple provayder + subject id), agar kirgan bo'lsangiz;
- sinxronlangan o'yin statistikasi (til, topishmoq raqami, g'alaba/urinishlar, yangilanish vaqti);
- barcha sessiya va refresh tokenlari.

Qurilmadagi mahalliy ma'lumotlar (mavzu, til, tugallanmagan raund, mahalliy statistika) shu amal bilan qurilmada tozalanadi.

## Nima saqlanadi va qancha muddat

- O'chirishdan keyin hech qanday shaxsiy ma'lumot saqlanmaydi — ilova serverda ismingiz, emailingiz yoki profil rasmingizni hech qachon saqlamaydi.
- Shifrlangan ma'lumotlar bazasi zaxira nusxalarida yozuvlaringiz **30 kungacha** qolishi mumkin, keyin ular qayta yoziladi va ma'lumot butunlay yo'qoladi.
- Xarid ma'lumotlari Google Play / RevenueCat da ularning siyosatiga ko'ra saqlanadi; ilova to'lov ma'lumotlarini saqlamaydi.

## Aloqa

O'chirish bo'yicha savollar: **lazydevscat@gmail.com**.
