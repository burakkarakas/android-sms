# Akıllı SMS

Samsung S23 (Android 10+) için kendi SMS uygulaman. Gelen mesajları kurallarla ve isteğe bağlı DeepSeek yapay zekasıyla filtreler.

## Özellikler

- Gelen mesajları **Önemli / Normal / Spam** olarak sınıflandırır
- Kurallar: engellenen gönderenler, engellenen kelimeler, izin verilen gönderenler
- Kurallarla karar veremediği mesajları DeepSeek'e sorar (isteğe bağlı, Ayarlar'dan açılır)
- Spam mesajları ayrı bir listede görür, yanlış işaretlenenleri geri alabilirsin
- Sohbet, mesaj gönderme ve yeni mesaj

## Kurulum

1. GitHub'da **Actions** sekmesine git, en son çalışan **Build APK** işini aç.
2. Sayfanın altındaki **sms-filter-debug** dosyasını indir ve zip'i aç. İçinden `app-debug.apk` çıkar.
3. APK'yı telefona aktar ve kur. "Bilinmeyen kaynaktan yükleme" izni istenirse ver.
4. Uygulamayı aç ve izinleri onayla.
5. **Ayarlar** ekranında "Varsayılan yap" butonuna bas ve Samsung'un uygulama seçim ekranında bu uygulamayı seç.
6. İstersen DeepSeek API anahtarını gir, "Test et" ile kontrol et ve "Yapay zeka filtresi"ni aç.

## Bilinen sınırlamalar

- MMS desteklenmiyor, sadece düz metin SMS.
- Sohbet silme ve arama henüz yok.
- DeepSeek anahtarı telefonda uygulamanın kendi alanında düz metin olarak saklanır.
- Yapay zeka açıkken mesaj metni DeepSeek'e gönderilir.
