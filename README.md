# ⚡ Şarj Show

Telefon şarja takılınca **animasyon** gösteren ve **sesli (TTS) bildirim** yapan Android uygulaması.

## Özellikler
- 🎬 **Açılış animasyonu:** Logo (şarj halkası + yıldırım) ~2 sn animasyonla gelir, sonra uygulama açılır.
- ⚡ **Şarj animasyonu:** Şarj takılınca ekranda canlı pil yüzdesiyle animasyon çıkar (kilit ekranında da).
- 🗣️ **Sesli okuma:** Şarj takılınca / çıkarılınca istediğin cümleyi Türkçe söyler.
- 🔄 **Arka plan servisi:** Açık bırakılırsa telefon yeniden başlayınca da çalışır.

## Kullanım
1. Uygulamayı aç, **"Şarj animasyonu ve sesli bildirim"** anahtarını aç.
2. **"Üstte gösterme iznini aç"** butonuna basıp izni ver (animasyonun arka plandan açılması için gerekli).
3. İstersen söylenecek cümleleri değiştirip **Kaydet**'e bas.

## APK alma
- **GitHub:** Projeyi bir repoya push et → *Actions* sekmesi → "Build Android APK" → en alttaki **Artifacts** → `sarj-show-debug-apk`.
- **Android Studio:** Projeyi aç → *Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)*.
- **Komut satırı:** `gradle assembleDebug` (Gradle 8.5, JDK 17) → `app/build/outputs/apk/debug/app-debug.apk`
