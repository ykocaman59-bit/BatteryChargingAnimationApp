package com.example.chargingshow

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN / desen / parmak izi ayarlarının saklandığı yer.
 * PIN ve desen düz yazı olarak SAKLANMAZ; tuzlanmış SHA-256 özeti saklanır.
 *
 * "ns" (isim alanı): "master" = ana kilit, "app.<paket>" = o uygulamaya özel kilit.
 */
object LockStore {
    const val MASTER = "master"
    private const val FILE = "osmanli_lock"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ özet / doğrulama

    private fun newSalt(): String {
        val b = ByteArray(12)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun hash(salt: String, value: String): String {
        val d = MessageDigest.getInstance("SHA-256")
            .digest((salt + ":" + value).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun pack(value: String): String {
        val salt = newSalt()
        return salt + ":" + hash(salt, value)
    }

    private fun matches(stored: String?, value: String): Boolean {
        if (stored == null) return false
        val i = stored.indexOf(':')
        if (i < 0) return false
        val salt = stored.substring(0, i)
        val h = stored.substring(i + 1)
        return MessageDigest.isEqual(h.toByteArray(Charsets.UTF_8), hash(salt, value).toByteArray(Charsets.UTF_8))
    }

    // ------------------------------------------------------------ kilit yöntemleri

    fun hasPin(ctx: Context, ns: String): Boolean = sp(ctx).contains("$ns.pin")
    fun hasPattern(ctx: Context, ns: String): Boolean = sp(ctx).contains("$ns.pat")
    fun hasFingerprint(ctx: Context, ns: String): Boolean = sp(ctx).getBoolean("$ns.fp", false)
    fun hasFace(ctx: Context, ns: String): Boolean = sp(ctx).getBoolean("$ns.face", false)
    fun isSet(ctx: Context, ns: String): Boolean =
        hasPin(ctx, ns) || hasPattern(ctx, ns) || hasFingerprint(ctx, ns) || hasFace(ctx, ns)

    fun pinLength(ctx: Context, ns: String): Int = sp(ctx).getInt("$ns.pinlen", 0)

    fun describe(ctx: Context, ns: String): String {
        val l = ArrayList<String>()
        if (hasPin(ctx, ns)) l.add("PIN")
        if (hasPattern(ctx, ns)) l.add("Desen")
        if (hasFingerprint(ctx, ns)) l.add("Parmak izi")
        if (hasFace(ctx, ns)) l.add("Yüz tarama")
        return if (l.isEmpty()) "Ayarlanmadı" else l.joinToString(" • ")
    }

    /** Bu isim alanındaki TÜM yöntemleri verilenlerle değiştirir (null = o yöntem yok). */
    fun save(ctx: Context, ns: String, pin: String?, pattern: String?, fingerprint: Boolean, face: Boolean) {
        val e = sp(ctx).edit()
        if (pin != null) {
            e.putString("$ns.pin", pack(pin)).putInt("$ns.pinlen", pin.length)
        } else {
            e.remove("$ns.pin").remove("$ns.pinlen")
        }
        if (pattern != null) e.putString("$ns.pat", pack(pattern)) else e.remove("$ns.pat")
        e.putBoolean("$ns.fp", fingerprint)
        e.putBoolean("$ns.face", face)
        e.apply()
    }

    fun clear(ctx: Context, ns: String) {
        sp(ctx).edit()
            .remove("$ns.pin").remove("$ns.pinlen").remove("$ns.pat").remove("$ns.fp").remove("$ns.face")
            .apply()
    }

    fun checkPin(ctx: Context, ns: String, pin: String): Boolean =
        matches(sp(ctx).getString("$ns.pin", null), pin)

    fun checkPattern(ctx: Context, ns: String, pattern: String): Boolean =
        matches(sp(ctx).getString("$ns.pat", null), pattern)

    // ------------------------------------------------------------ kilitli uygulamalar

    fun lockedApps(ctx: Context): Set<String> =
        HashSet(sp(ctx).getStringSet("apps", emptySet()) ?: emptySet())

    fun isAppLocked(ctx: Context, pkg: String): Boolean = lockedApps(ctx).contains(pkg)

    fun appUsesOwn(ctx: Context, pkg: String): Boolean = sp(ctx).getBoolean("app.$pkg.own", false)

    /** Bu uygulamayı açarken hangi kilit sorulacak? */
    fun appNs(ctx: Context, pkg: String): String = if (appUsesOwn(ctx, pkg)) "app.$pkg" else MASTER

    fun lockApp(ctx: Context, pkg: String, own: Boolean) {
        val s = lockedApps(ctx).toMutableSet()
        s.add(pkg)
        sp(ctx).edit().putStringSet("apps", s).putBoolean("app.$pkg.own", own).apply()
    }

    fun unlockApp(ctx: Context, pkg: String) {
        val s = lockedApps(ctx).toMutableSet()
        s.remove(pkg)
        clear(ctx, "app.$pkg")
        sp(ctx).edit().putStringSet("apps", s).remove("app.$pkg.own").apply()
    }

    /** Ana kilidi siler; ana kiliti kullanan uygulama kilitleri de kalkar. */
    fun removeMaster(ctx: Context) {
        for (pkg in lockedApps(ctx)) {
            if (!appUsesOwn(ctx, pkg)) unlockApp(ctx, pkg)
        }
        clear(ctx, MASTER)
    }

    // ------------------------------------------------------------ kaldırma koruması

    fun guardEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("guard", true)
    fun setGuard(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("guard", on).apply()
    }

    // ------------------------------------------------------------ yanlış deneme sınırı

    fun lockRemainingMs(ctx: Context): Long {
        val until = sp(ctx).getLong("lock_until", 0L)
        return maxOf(0L, until - System.currentTimeMillis())
    }

    fun registerFail(ctx: Context) {
        val p = sp(ctx)
        val fails = p.getInt("fails", 0) + 1
        val e = p.edit().putInt("fails", fails)
        if (fails >= 5) {
            val wait = minOf(300_000L, 30_000L * (fails - 4))
            e.putLong("lock_until", System.currentTimeMillis() + wait)
        }
        e.apply()
    }

    fun resetFails(ctx: Context) {
        sp(ctx).edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
    }
}

/** Bellekte tutulan "şu an açık" durumu (servis ve ekranlar aynı süreçte). */
object LockGuard {
    private const val GRACE_MS = 15_000L
    private val unlocked = HashMap<String, Long>() // paket -> ayrıldığı an (0 = hâlâ içinde)

    @Volatile
    private var uninstallUntil = 0L

    @Synchronized
    fun unlock(pkg: String) {
        unlocked[pkg] = 0L
    }

    @Synchronized
    fun isUnlocked(pkg: String): Boolean {
        val left = unlocked[pkg] ?: return false
        return left == 0L || System.currentTimeMillis() - left <= GRACE_MS
    }

    /** Servis, ön plana gelen paketi bildirir: ayrılan uygulamaların kilidi kısa süre sonra kapanır. */
    @Synchronized
    fun onForeground(pkg: String) {
        val now = System.currentTimeMillis()
        val iter = unlocked.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            val left = e.value
            if (e.key == pkg) {
                if (left != 0L && now - left > GRACE_MS) iter.remove() else e.setValue(0L)
            } else {
                if (left == 0L) e.setValue(now)
                else if (now - left > GRACE_MS) iter.remove()
            }
        }
    }

    @Synchronized
    fun clearAll() {
        unlocked.clear()
        uninstallUntil = 0L
    }

    fun allowUninstall(ms: Long) {
        uninstallUntil = System.currentTimeMillis() + ms
    }

    fun uninstallAllowed(): Boolean = System.currentTimeMillis() < uninstallUntil
}
