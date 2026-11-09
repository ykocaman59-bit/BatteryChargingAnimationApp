package com.example.chargingshow

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity

/**
 * Tam ekran kilit doğrulama:
 *  - MODE_APP   : kilitli bir uygulama açılırken
 *  - MODE_GUARD : Osmanlı Launcher kaldırılmaya / durdurulmaya çalışılırken
 */
class AuthActivity : FragmentActivity() {

    private val modeState = mutableStateOf(MODE_APP)
    private val pkgState = mutableStateOf("")
    private val serial = mutableIntStateOf(0)

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PKG = "pkg"
        const val MODE_APP = "app"
        const val MODE_GUARD = "guard"

        fun forApp(ctx: Context, pkg: String): Intent =
            Intent(ctx, AuthActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_APP)
                .putExtra(EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun forGuard(ctx: Context): Intent =
            Intent(ctx, AuthActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_GUARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PIN/desen ekran görüntüsünde ve son uygulamalarda görünmesin
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        readIntent(intent)

        // Geri tuşu = vazgeç: ana ekrana dön (kilitli uygulama görünmesin)
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            OsmanliLauncherTheme {
                key(serial.intValue) {
                    val mode = modeState.value
                    val pkg = pkgState.value
                    val ns = if (mode == MODE_APP && pkg.isNotEmpty()) {
                        LockStore.appNs(this@AuthActivity, pkg)
                    } else {
                        LockStore.MASTER
                    }
                    if (!LockStore.isSet(this@AuthActivity, ns)) {
                        // sorulacak kilit kalmamış (silinmiş): geç
                        LaunchedEffect(Unit) { complete(mode, pkg) }
                    } else {
                        AuthScreen(
                            ns = ns,
                            title = if (mode == MODE_APP) appLabel(pkg) else "Osmanlı Koruma",
                            subtitle = if (mode == MODE_APP) "Bu uygulama kilitli"
                            else "Kaldırmak veya ayarları değiştirmek için kimliğini doğrula",
                            onSuccess = { complete(mode, pkg) },
                            onCancel = { goHome() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        serial.intValue++
    }

    private fun readIntent(i: Intent?) {
        modeState.value = i?.getStringExtra(EXTRA_MODE) ?: MODE_APP
        pkgState.value = i?.getStringExtra(EXTRA_PKG) ?: ""
    }

    private fun appLabel(pkg: String): String =
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

    private fun complete(mode: String, pkg: String) {
        if (mode == MODE_APP && pkg.isNotEmpty()) {
            LockGuard.unlock(pkg)
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(launch)
                } catch (e: Exception) {
                    // açılamadıysa sessizce kapan
                }
            }
        } else {
            // kaldırma / ayar koruması: kısa süre izin ver
            LockGuard.allowUninstall(90_000L)
        }
        finishAndRemoveTask()
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ana ekran bulunamadıysa sadece kapan
        }
        finishAndRemoveTask()
    }
}
