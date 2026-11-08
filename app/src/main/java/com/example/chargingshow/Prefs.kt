package com.example.chargingshow

object Prefs {
    const val NAME = "settings"

    // Şarj Show
    const val ENABLED = "enabled"
    const val TEXT_PLUG = "text_plug"
    const val TEXT_UNPLUG = "text_unplug"
    const val DEFAULT_PLUG = "Şarj başladı"
    const val DEFAULT_UNPLUG = "Şarj kablosu çıkarıldı"

    // Düşük şarj uyarısı
    const val LOW_ENABLED = "low_enabled"
    const val LOW_LEVEL = "low_level"
    const val LOW_TEXT = "low_text"
    const val DEFAULT_LOW_LEVEL = 19
    const val DEFAULT_LOW_TEXT = "Şarj bitiyor, lütfen şarja tak"

    // Launcher
    const val COVER = "cover"
    const val APPLY_LOCK = "apply_lock"
    const val HOME_LAYOUT = "home_layout"
}
