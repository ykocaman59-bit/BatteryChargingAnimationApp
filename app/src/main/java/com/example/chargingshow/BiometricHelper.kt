package com.example.chargingshow

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

object BiometricHelper {

    /** Telefonda kayıtlı herhangi bir biyometrik (parmak izi / yüz) var mı? */
    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** "Güçlü" sınıf biyometrik var mı? (çoğu telefonda parmak izi güçlü, yüz tanıma zayıf sınıftır) */
    fun availableStrong(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Telefonda parmak izi sensörü var ve kayıtlı bir biyometrik mevcut mu? */
    fun fingerprintAvailable(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) && available(ctx)

    /**
     * Parmak izi penceresini açar.
     * onError(null) = kullanıcı vazgeçti (mesaj gösterme), onError("...") = gösterilecek hata.
     * strongOnly: true = yalnız güçlü sınıf (genelde parmak izi), false = telefonun sunduğu her biyometrik
     * (parmak izi veya yüz). Telefonun kendi ekran kilidi (PIN/desen) ASLA yedek olarak sorulmaz.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        fallbackText: String,
        strongOnly: Boolean,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (silent) null else errString.toString())
            }
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        builder.setAllowedAuthenticators(
            if (strongOnly) Authenticators.BIOMETRIC_STRONG else Authenticators.BIOMETRIC_WEAK
        ).setNegativeButtonText(fallbackText)
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(builder.build())
        } catch (e: Exception) {
            onError("Parmak izi başlatılamadı")
        }
    }
}
