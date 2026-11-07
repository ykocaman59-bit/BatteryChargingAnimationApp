# ⚡ Şarj Show + 🏛️ Osmanlı Launcher

Tek uygulamada iki özellik:

**Osmanlı Launcher** – Osmanlı temalı (lacivert + altın) ana ekran: Osmanlı/Hicri takvim kartı, uygulama arama, 4'lü uygulama ızgarası, alt dock.

**Şarj Show** – Şarja takılınca animasyon + sesli bildirim (Türkçe TTS), logo açılış animasyonu.

## Kullanım
1. Uygulamayı aç: logo animasyonu → Osmanlı ana ekranı.
2. Dock'taki ⚙ (Şarj Show) simgesinden ayarlara gir; servisi aç, "üstte gösterme" iznini ver.
3. Ayarlardaki **"Osmanlı Launcher'ı ana ekran yap"** butonuyla varsayılan ana ekran seç.
   (Geri dönmek için: Telefon Ayarları ▸ Uygulamalar ▸ Varsayılan uygulamalar ▸ Ana ekran uygulaması)

## APK alma
- **GitHub:** push → Actions ▸ "Build Android APK" ▸ Artifacts ▸ `sarj-show-debug-apk`
- **Android Studio:** Build ▸ Build APK(s)
- **Komut satırı:** `gradle assembleDebug` (Gradle 8.5, JDK 17)
