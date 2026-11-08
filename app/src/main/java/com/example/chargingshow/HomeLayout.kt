package com.example.chargingshow

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ana ekrandaki bir öğe: uygulama ya da klasör. */
sealed class HomeEntry {
    abstract val key: String

    data class App(val pkg: String) : HomeEntry() {
        override val key: String get() = "a:$pkg"
    }

    data class Folder(val id: String, val name: String, val pkgs: List<String>) : HomeEntry() {
        override val key: String get() = "f:$id"
    }
}

/** Sıralama ve klasörleri telefonda saklar. */
object LayoutStore {

    fun load(prefs: SharedPreferences): List<HomeEntry> {
        val raw = prefs.getString(Prefs.HOME_LAYOUT, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<HomeEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                when (o.getString("t")) {
                    "app" -> list.add(HomeEntry.App(o.getString("p")))
                    "folder" -> {
                        val a = o.getJSONArray("a")
                        val pk = ArrayList<String>()
                        for (k in 0 until a.length()) pk.add(a.getString(k))
                        list.add(HomeEntry.Folder(o.getString("id"), o.getString("n"), pk))
                    }
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(prefs: SharedPreferences, entries: List<HomeEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            when (e) {
                is HomeEntry.App -> {
                    o.put("t", "app")
                    o.put("p", e.pkg)
                }
                is HomeEntry.Folder -> {
                    o.put("t", "folder")
                    o.put("id", e.id)
                    o.put("n", e.name)
                    val a = JSONArray()
                    for (p in e.pkgs) a.put(p)
                    o.put("a", a)
                }
            }
            arr.put(o)
        }
        prefs.edit().putString(Prefs.HOME_LAYOUT, arr.toString()).apply()
    }

    /** Silinen uygulamaları temizler, yeni yüklenenleri sona ekler. */
    fun reconcile(saved: List<HomeEntry>, installed: List<AppItem>): List<HomeEntry> {
        val installedPkgs = installed.map { it.packageName }.toSet()
        val seen = HashSet<String>()
        val result = ArrayList<HomeEntry>()
        for (e in saved) {
            when (e) {
                is HomeEntry.App -> {
                    if (e.pkg in installedPkgs && seen.add(e.pkg)) result.add(e)
                }
                is HomeEntry.Folder -> {
                    val keep = ArrayList<String>()
                    for (p in e.pkgs) {
                        if (p in installedPkgs && seen.add(p)) keep.add(p)
                    }
                    if (keep.isNotEmpty()) result.add(e.copy(pkgs = keep))
                }
            }
        }
        for (a in installed) {
            if (seen.add(a.packageName)) result.add(HomeEntry.App(a.packageName))
        }
        return result
    }
}
