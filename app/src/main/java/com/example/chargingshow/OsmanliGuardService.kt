package com.example.chargingshow

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Koruma hizmeti (Erişilebilirlik):
 *  1) Kilitli bir uygulama HER YERDEN (son uygulamalar, bildirim, başka launcher) açılsa bile kilit sorar.
 *  2) Ayarlar / paket yükleyicide "Şarj Show / Osmanlı Launcher" kaldırılmaya, durdurulmaya
 *     ya da bu hizmet kapatılmaya çalışılırsa ana kilidi sorar.
 */
class OsmanliGuardService : AccessibilityService() {

    private var lastPrompt = 0L
    private var lastScan = 0L
    private var imeCache: Set<String> = emptySet()
    private var imeAt = 0L
    private var receiverOn = false

    private val tr = Locale("tr")

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // ekran kapanınca bütün uygulama kilitleri yeniden kapanır
            LockGuard.clearAll()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverOn) {
            try {
                ContextCompat.registerReceiver(
                    this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverOn = true
            } catch (e: Exception) {
                // kayıt olmazsa sadece ekran kapanınca otomatik kilitleme çalışmaz
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (receiverOn) {
            try {
                unregisterReceiver(screenOff)
            } catch (e: Exception) {
                // zaten kapalı
            }
            receiverOn = false
        }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val ctx = applicationContext

        // 1) uygulama kilidi
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isNoise(pkg)) {
            LockGuard.onForeground(pkg)
            if (LockStore.isAppLocked(ctx, pkg) && !LockGuard.isUnlocked(pkg)) {
                prompt(AuthActivity.forApp(this, pkg))
            }
        }

        // 2) kaldırma / durdurma / hizmeti kapatma koruması
        if (LockStore.guardEnabled(ctx) &&
            LockStore.isSet(ctx, LockStore.MASTER) &&
            !LockGuard.uninstallAllowed() &&
            isGuardPackage(pkg)
        ) {
            scanGuardWindow()
        }
    }

    // ------------------------------------------------------------ yardımcılar

    private fun prompt(intent: Intent) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPrompt < 1000) return
        lastPrompt = now
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // arka plandan açma izni yoksa yapacak bir şey yok
        }
    }

    private fun isNoise(pkg: String): Boolean =
        pkg == "com.android.systemui" || pkg == "android" || isIme(pkg)

    private fun isIme(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - imeAt > 60_000L) {
            imeAt = now
            imeCache = try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.enabledInputMethodList.map { it.packageName }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        return imeCache.contains(pkg)
    }

    private fun isGuardPackage(pkg: String): Boolean =
        pkg == "android" ||
            pkg.contains("packageinstaller") ||
            pkg.contains("settings") ||
            pkg.contains("securitycenter") ||
            pkg.contains("appmanager") ||
            pkg.contains("permissioncontroller")

    private fun scanGuardWindow() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScan < 350) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, IntArray(1))
        val text = sb.toString().lowercase(tr)

        val appLabel = packageManager.getApplicationLabel(applicationInfo).toString().lowercase(tr)
        val launcherLabel = getString(R.string.launcher_label).lowercase(tr)
        val serviceLabel = getString(R.string.guard_service_label).lowercase(tr)

        // a) uygulama bilgisi sayfası / kaldırma onayı
        val mentionsApp = text.contains(appLabel) || text.contains(launcherLabel)
        val dangerous = DANGER_WORDS.any { text.contains(it) }
        // b) erişilebilirlikte bu hizmetin sayfası (kapatılamasın)
        val mentionsService = text.contains(serviceLabel)

        if ((mentionsApp && dangerous) || mentionsService) {
            prompt(AuthActivity.forGuard(this))
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, sb: StringBuilder, count: IntArray) {
        if (node == null || count[0] > 600) return
        count[0]++
        val t = node.text
        if (t != null) sb.append(t).append(' ')
        val d = node.contentDescription
        if (d != null) sb.append(d).append(' ')
        for (i in 0 until node.childCount) collect(node.getChild(i), sb, count)
    }

    companion object {
        private val DANGER_WORDS = listOf(
            "kaldır", "uninstall", "zorla durdur", "durmaya zorla", "force stop",
            "verileri temizle", "clear data"
        )
    }
}
