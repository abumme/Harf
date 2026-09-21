# «Harf» Uygulaması Gizlilik Politikası

**Revizyon tarihi:** 2026-09-17

## 1. Genel Hükümler

1.1. Bu Politika, «Harf» mobil uygulamasında (bundan sonra — «Uygulama») hangi verilerin işlendiğini, hangi amaçla ve nasıl korunduğunu açıklar.

1.2. **Veri sorumlusu:** Islomov Mekhrojbek, gerçek kişi, Özbekistan Cumhuriyeti. Email: lazydevscat@gmail.com.

1.3. Veriler, Özbekistan Cumhuriyeti'nin "Kişisel Veriler Hakkında" Kanununa uygun olarak işlenir.

1.4. Uygulamayı yükleyip kullanarak Kullanıcı bu Politikayı kabul eder.

## 2. İşlenen Veriler

2.1. **Hesap tanımlayıcısı.** Uygulama anonim bir hesap tanımlayıcısı (rastgele UUID) oluşturur. İçinde ad, telefon veya başka doğrudan tanımlayıcılar bulunmaz.

2.2. **Google veya Apple ile oturum açma (isteğe bağlı).** Kullanıcı Google veya Apple ile oturum açarsa, sunucuda sonraki girişlerde hesabı ilişkilendiren sağlayıcı tanımlayıcısı (provider + subject id) ve Kullanıcının oturum açarken onayladığı görünen ad saklanır. **E-posta adresi ve profil fotoğrafı sunucuda saklanmaz.**

2.3. **Oyun istatistikleri.** İlerlemeyi cihazlar arasında eşitlemek için şunlar saklanır: oyun dili, günlük bulmaca numarası, turun kazanılıp kazanılmadığı, deneme sayısı ve güncelleme zamanı.

2.4. **Satın alma verileri.** Uygulama içi satın almalar Google Play mağazası ve RevenueCat hizmeti tarafından işlenir. Uygulama yalnızca yetki durumunu (neyin satın alındığını) alır ve ödeme bilgilerini (kart numaraları vb.) **almaz ve saklamaz**.

2.5. **Yerel ayarlar.** Ayarlar ve ilerleme Kullanıcının cihazında yerel olarak saklanır (tema, dil, tamamlanmamış tur, yerel istatistikler).

2.6. Uygulama konum, kişiler, mikrofon veya kamera verilerini **toplamaz** ve reklam göstermez.

2.7. **Kelime önerileri (isteğe bağlı).** Kullanıcı sözlüğe bir kelime eklenmesini önerirse, sunucuda kelime, dili, gönderilme zamanı ve öneri hakkındaki karar (kabul edildi veya reddedildi) saklanır. Öneri, hesap silinene kadar hesapla ilişkilidir; hesap silindikten sonra öneri hesapla herhangi bir bağlantısı olmadan saklanır.

## 3. İşleme Amaçları

- Oyun ilerlemesini kaydetmek ve cihazlar arasında eşitlemek.
- Ücretli ek içeriklere erişim sağlamak.
- Hesap oturumu ve oturum güvenliğini sağlamak.
- Uygulamanın nasıl kullanıldığını anlamak ve geliştirmek amacıyla oyun istatistiklerini, tek tek kullanıcıları tanımlamadan, toplu hâlde analiz etmek.

## 4. Üçüncü Taraflar

4.1. Veriler, Uygulamanın çalışması için gereken ölçüde aşağıdaki hizmetler tarafından işlenebilir:

- **Google Play** (Google LLC) — ödeme kabulü ve Uygulama dağıtımı.
- **RevenueCat, Inc.** — uygulama içi satın almaların ve yetkilerin yönetimi.
- **Google Sign-In / Apple Sign-In** — oturum doğrulaması (Kullanıcının tercihine göre).

4.2. Veriler üçüncü taraflara satılmaz ve reklam amacıyla kullanılmaz.

## 5. Saklama ve Güvenlik

5.1. Oturum belirteçleri sunucuda yalnızca karma (hash) olarak saklanır. Uygulama ile sunucu arasındaki veriler güvenli bir bağlantı (HTTPS) üzerinden iletilir.

5.2. Sunucu verileri, Kullanıcının hesabı var olduğu sürece saklanır. Kimseyi tanımlamayan toplu istatistikler, hesap silindikten sonra da saklanabilir.

5.3. Uygulamanın yetkili yöneticileri hesap verilerine (hesap tanımlayıcısı, oturum açma sağlayıcısı türü, görünen ad, oyun istatistikleri ve kelime önerileri) yalnızca destek, moderasyon ve kötüye kullanımın önlenmesi amacıyla erişebilir; yönetici erişimi yönetici hesaplarıyla sınırlıdır ve yöneticilerin işlemleri kayıt altına alınır.

## 6. Kullanıcı Hakları

6.1. **Hesap silme.** Kullanıcı hesabını Uygulama içinde silebilir; silindiğinde ilişkili sunucu verileri (oturum tanımlayıcıları, istatistikler, belirteçler) art arda (cascade) silinir. Hesap silme işlemi, Kullanıcının talebi üzerine bir yönetici tarafından da yapılabilir.

6.2. Kullanıcı, verilerinin işlenmesi hakkında bilgi almak için lazydevscat@gmail.com adresine başvurabilir.

## 7. Çocuklar

7.1. Uygulama çocukların verilerini toplamaya yönelik değildir ve yaşı belirlemeye yarayacak veriler istemez. Uygulama, 2. Bölümde açıklananın ötesinde kişisel veri toplamaz.

## 8. Politika Değişiklikleri

8.1. Sorumlu, Politikayı değiştirebilir ve yeni sürümü https://lazydevs.uz/harf/privacy adresinde yayımlayabilir. Güncel sürüm her zaman Uygulamada ve belirtilen bağlantıda mevcuttur.

## 9. İletişim

- **Sorumlu:** Islomov Mekhrojbek (gerçek kişi), Özbekistan Cumhuriyeti
- **Email:** lazydevscat@gmail.com
- **Uygulama:** «Harf» (paket kimliği: `uz.abumme.harfgame`)
