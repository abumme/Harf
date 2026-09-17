# «Harf» ilovasining Maxfiylik siyosati

**Tahrir sanasi:** 2026-09-17


## 1. Umumiy qoidalar

1.1. Ushbu Siyosat «Harf» mobil ilovasida (keyingi o'rinlarda — «Ilova») qanday ma'lumotlar qayta ishlanishini, qanday maqsadda va qanday himoyalanishini tavsiflaydi.

1.2. **Ma'lumotlar operatori:** Islomov Mekhrojbek, jismoniy shaxs, O'zbekiston Respublikasi. Email: lazydevscat@gmail.com.

1.3. Ma'lumotlarni qayta ishlash O'zbekiston Respublikasining «Shaxsga doir ma'lumotlar to'g'risida»gi qonuniga muvofiq amalga oshiriladi.

1.4. Ilovani o'rnatish va undan foydalanish orqali Foydalanuvchi ushbu Siyosatga rozilik bildiradi.

## 2. Qanday ma'lumotlar qayta ishlanadi

2.1. **Hisob identifikatori.** Ilova anonim hisob identifikatorini (tasodifiy UUID) yaratadi. U ism, telefon yoki boshqa to'g'ridan-to'g'ri identifikatorlarni o'z ichiga olmaydi.

2.2. **Google yoki Apple orqali kirish (ixtiyoriy).** Agar Foydalanuvchi Google yoki Apple orqali kirsa, serverda provayder identifikatori (provider + subject id) — u qayta kirishda hisobni bog'lash imkonini beradi — hamda Foydalanuvchi kirishda tasdiqlagan ko'rinadigan ism saqlanadi. **Elektron pochta manzili va profil surati serverda saqlanmaydi.**

2.3. **O'yin statistikasi.** Qurilmalar orasida progressni sinxronlash uchun quyidagilar saqlanadi: o'yin tili, kunlik boshqotirma raqami, g'alaba fakti, urinishlar soni va yangilanish vaqti.

2.4. **Xaridlar haqidagi ma'lumotlar.** Ilova ichidagi xaridlar Google Play do'koni va RevenueCat xizmati tomonidan qayta ishlanadi. Ilova faqat kirish huquqlari holatini (nima sotib olinganini) oladi, biroq to'lov rekvizitlarini (karta raqamlari va h.k.) **olmaydi va saqlamaydi**.

2.5. **Mahalliy sozlamalar.** Sozlamalar va progress Foydalanuvchi qurilmasida mahalliy saqlanadi (mavzu, til, tugallanmagan o'yin, mahalliy statistika).

2.6. Ilova joylashuv, kontaktlar, mikrofon, kamerani **to'plamaydi** va reklama ko'rsatmaydi.

2.7. **So'z takliflari (ixtiyoriy).** Agar Foydalanuvchi lug'atga so'z qo'shishni taklif qilsa, serverda so'z, uning tili, yuborilgan vaqti va u bo'yicha qaror (qabul qilingan yoki rad etilgan) saqlanadi. Taklif hisob o'chirilgunga qadar hisobga bog'langan bo'ladi; hisob o'chirilgandan so'ng taklif hisobga bog'lanmagan holda saqlanib qoladi.

## 3. Qayta ishlash maqsadlari

- O'yin progressini saqlash va qurilmalar orasida sinxronlash.
- To'langan qo'shimchalarga kirishni ta'minlash.
- Hisobga kirish ishini va sessiyalar xavfsizligini ta'minlash.
- Ilovadan qanday foydalanilishini tushunish va uni yaxshilash uchun o'yin statistikasini alohida foydalanuvchilarni aniqlamasdan, umumlashtirilgan holda tahlil qilish.

## 4. Uchinchi shaxslarga uzatish

4.1. Ma'lumotlar Ilova ishlashi uchun zarur bo'lgan hajmda quyidagi xizmatlar tomonidan qayta ishlanishi mumkin:

- **Google Play** (Google LLC) — to'lovlarni qabul qilish va Ilovani tarqatish.
- **RevenueCat, Inc.** — ilova ichidagi xaridlar va kirish huquqlarini boshqarish.
- **Google Sign-In / Apple Sign-In** — kirishni tekshirish (Foydalanuvchi tanloviga ko'ra).

4.2. Ma'lumotlar uchinchi shaxslarga sotilmaydi va reklama maqsadlarida ishlatilmaydi.

## 5. Saqlash va xavfsizlik

5.1. Sessiya tokenlari serverda faqat xeш ko'rinishida saqlanadi. Ilova va server o'rtasidagi ma'lumotlar himoyalangan ulanish (HTTPS) orqali uzatiladi.

5.2. Server ma'lumotlari Foydalanuvchi hisobi mavjud bo'lguncha saqlanadi. Hech kimni aniqlab bo'lmaydigan umumlashtirilgan statistika hisob o'chirilganidan keyin ham saqlanishi mumkin.

5.3. Ilovaning vakolatli administratorlari hisob ma'lumotlariga (hisob identifikatori, kirish provayderi turi, ko'rinadigan ism, o'yin statistikasi va so'z takliflari) faqat qo'llab-quvvatlash, moderatsiya va suiiste'molning oldini olish uchun kirishi mumkin; bunday kirish faqat administrator hisoblariga beriladi va ularning harakatlari jurnalga yoziladi.

## 6. Foydalanuvchi huquqlari

6.1. **Hisobni o'chirish.** Foydalanuvchi o'z hisobini Ilovada o'chirishi mumkin; o'chirilganda bog'liq server ma'lumotlari (kirish identifikatorlari, statistika, tokenlar) kaskadli o'chiriladi. Hisobni Foydalanuvchining so'roviga ko'ra administrator ham o'chirishi mumkin.

6.2. Foydalanuvchi o'z ma'lumotlarini qayta ishlash haqida ma'lumot olish uchun lazydevscat@gmail.com manziliga murojaat qilishi mumkin.

## 7. Bolalar

7.1. Ilova bolalar ma'lumotlarini to'plash uchun mo'ljallanmagan va yoshni aniqlash imkonini beruvchi ma'lumotlarni so'ramaydi. Ilova 2-bo'limda tavsiflanganidan ortiq shaxsga doir ma'lumotlarni to'plamaydi.

## 8. Siyosatga o'zgartirishlar

8.1. Operator Siyosatni o'zgartirishga, yangi tahrirni https://lazydevs.uz/harf/privacy manzilida e'lon qilishga haqli. Amaldagi tahrir doimo Ilovada va ko'rsatilgan havolada mavjud.

## 9. Aloqa

- **Operator:** Islomov Mekhrojbek (jismoniy shaxs), O'zbekiston Respublikasi
- **Email:** lazydevscat@gmail.com
- **Ilova:** «Harf» (paket identifikatori: `uz.abumme.harfgame`)
