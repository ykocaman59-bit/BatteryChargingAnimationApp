# ⚡ Şarj Show + 🏛️ Osmanlı Launcher

## Osmanlı Launcher
- **Ana sayfa:** kapak resmi, saat/tarih, Osmanlı (Hicri) takvim kartı. Sola kaydırınca **Uygulamalar** sayfası.
- **Kapak resmi:** sağ üstteki 🖼 simgesi. 120 hazır Osmanlı temalı tasarım + kendi galerinden seçme.
  `app/src/main/assets/wallpapers/` klasörüne eklediğin her resim otomatik listeye girer.
- **Kilit ekranı:** seçilen kapak istenirse kilit ekranı duvar kağıdı olarak da ayarlanır.
- **Basılı tut:** menü (Uygulama bilgisi / Kaldır). Basılı tutup **sürükle** → yer değiştirir.
  Bir uygulamayı başka uygulamanın üstüne bırak → **klasör** oluşur, adı sorulur.
- Arama kutusu; geri / ana ekran / kare tuşuna basınca yazı otomatik silinir.

## Şarj Show
- Şarja takılınca animasyon + sesli bildirim, açılışta logo animasyonu.
- **Düşük şarj uyarısı:** varsayılan %19, ayarlardan yüzde ve cümle değiştirilebilir.

## APK alma
- GitHub: push → Actions ▸ "Build Android APK" ▸ Artifacts
- Android Studio: Build ▸ Build APK(s)
- Komut satırı: `gradle assembleDebug` (Gradle 8.5, JDK 17)
