package com.example.chargingshow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Gold = Color(0xFFD4AF37)
private val Navy = Color(0xFF0F2A3F)

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

@Composable
fun OsmanliLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Gold,
            secondary = Navy,
            background = Color(0xFF07131D)
        ),
        content = content
    )
}

// ------------------------------------------------------------------ yardımcılar

private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            AppItem(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = runCatching { ri.loadIcon(pm).toBitmap(108, 108).asImageBitmap() }.getOrNull()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale("tr")) }
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // cihazda bu işi yapan uygulama yoksa çökmesin
    }
}

private fun launchApp(context: Context, pkg: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
    if (intent != null) safeStart(context, intent)
}

private fun openAppInfo(context: Context, pkg: String) {
    safeStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
}

private fun uninstallApp(context: Context, pkg: String) {
    safeStart(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
}

private fun hit(info: LazyGridItemInfo, p: Offset): Boolean =
    p.x >= info.offset.x && p.x <= info.offset.x + info.size.width &&
        p.y >= info.offset.y && p.y <= info.offset.y + info.size.height

private fun isCenter(info: LazyGridItemInfo, p: Offset): Boolean {
    val cx = info.offset.x + info.size.width / 2f
    val cy = info.offset.y + info.size.height / 2f
    return abs(p.x - cx) < info.size.width * 0.30f && abs(p.y - cy) < info.size.height * 0.30f
}

/** Sürükle-bırak durumu. */
@Stable
class DragState {
    var key by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var moved by mutableStateOf(0f)
    var lastEnd by mutableLongStateOf(0L)

    /** Sürükleme sonrası parmak kalkınca yanlışlıkla "tıklama" sayılmasın. */
    fun tapAllowed(): Boolean =
        key == null && (System.currentTimeMillis() - lastEnd) > 250
}

// ------------------------------------------------------------------ ana kök

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(
    refreshKey: Int,
    resetKey: Int,
    homeKey: Int,
    coverRef: String,
    applyLock: Boolean,
    onSetCover: (String) -> Unit,
    onPickGallery: () -> Unit,
    onApplyLockChanged: (Boolean) -> Unit,
    onApplyLockNow: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var entries by remember { mutableStateOf(LayoutStore.load(prefs)) }

    var searchQuery by remember { mutableStateOf("") }
    var appMenu by remember { mutableStateOf<Pair<String, String?>?>(null) }   // (paket, klasör id)
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var showCovers by remember { mutableStateOf(false) }

    val drag = remember { DragState() }
    val pagerState = rememberPagerState(pageCount = { 2 })

    val appMap = remember(apps) { apps.associateBy { it.packageName } }

    // uygulama listesi
    LaunchedEffect(refreshKey) {
        val loaded = withContext(Dispatchers.Default) { loadApps(context) }
        apps = loaded
        val reconciled = LayoutStore.reconcile(entries, loaded)
        entries = reconciled
        LayoutStore.save(prefs, reconciled)
    }

    // geri / ana ekran / kare tuşu: arama yazısı, klavye, menüler sıfırlansın
    LaunchedEffect(resetKey) {
        searchQuery = ""
        appMenu = null
        folderMenu = null
        openFolderId = null
        renameFolderId = null
        showCovers = false
        drag.key = null
        focusManager.clearFocus()
    }

    // ana ekran tuşu / geri: ilk sayfaya dön
    LaunchedEffect(homeKey) {
        if (pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }

    // kapak resmi
    val dm = context.resources.displayMetrics
    val cover by produceState<ImageBitmap?>(null, coverRef) {
        value = withContext(Dispatchers.IO) {
            Covers.decode(context, coverRef, dm.widthPixels, dm.heightPixels)?.asImageBitmap()
        }
    }

    fun commit(list: List<HomeEntry>) {
        entries = list
        LayoutStore.save(prefs, list)
    }

    fun moveEntry(draggedKey: String, targetKey: String?) {
        val list = entries.toMutableList()
        val from = list.indexOfFirst { it.key == draggedKey }
        if (from < 0) return
        val origTarget = if (targetKey == null) -1 else list.indexOfFirst { it.key == targetKey }
        val item = list.removeAt(from)
        if (targetKey == null || origTarget < 0) {
            list.add(item)
        } else {
            val to = list.indexOfFirst { it.key == targetKey }
            list.add(if (from < origTarget) to + 1 else to, item)
        }
        commit(list)
    }

    fun mergeEntry(draggedKey: String, targetKey: String) {
        val dragged = entries.firstOrNull { it.key == draggedKey }
        val target = entries.firstOrNull { it.key == targetKey }
        if (dragged !is HomeEntry.App || target == null) {
            moveEntry(draggedKey, targetKey)
            return
        }
        when (target) {
            is HomeEntry.App -> {
                val id = UUID.randomUUID().toString().take(8)
                val folder = HomeEntry.Folder(id, "Klasör", listOf(target.pkg, dragged.pkg))
                val list = ArrayList<HomeEntry>()
                for (e in entries) {
                    if (e.key == dragged.key) continue
                    if (e.key == target.key) list.add(folder) else list.add(e)
                }
                commit(list)
                renameFolderId = id // hemen ad soralım
            }
            is HomeEntry.Folder -> {
                val list = ArrayList<HomeEntry>()
                for (e in entries) {
                    if (e.key == dragged.key) continue
                    if (e.key == target.key) {
                        list.add(target.copy(pkgs = (target.pkgs + dragged.pkg).distinct()))
                    } else list.add(e)
                }
                commit(list)
            }
        }
    }

    fun renameFolder(id: String, name: String) {
        val clean = name.trim().ifEmpty { "Klasör" }
        commit(entries.map { if (it is HomeEntry.Folder && it.id == id) it.copy(name = clean) else it })
    }

    fun dissolveFolder(id: String) {
        val list = ArrayList<HomeEntry>()
        for (e in entries) {
            if (e is HomeEntry.Folder && e.id == id) {
                e.pkgs.forEach { list.add(HomeEntry.App(it)) }
            } else list.add(e)
        }
        if (openFolderId == id) openFolderId = null
        commit(list)
    }

    fun removeFromFolder(id: String, pkg: String) {
        val list = ArrayList<HomeEntry>()
        for (e in entries) {
            if (e is HomeEntry.Folder && e.id == id) {
                val rest = e.pkgs.filter { it != pkg }
                if (rest.isNotEmpty()) list.add(e.copy(pkgs = rest))
                list.add(HomeEntry.App(pkg))
                if (rest.isEmpty() && openFolderId == id) openFolderId = null
            } else list.add(e)
        }
        commit(list)
    }

    val results = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- arka plan: kapak resmi
        val coverBmp = cover
        if (coverBmp != null) {
            Image(
                bitmap = coverBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B1E2D), Color(0xFF040A10))))
            )
        }
        // okunabilirlik için hafif karartma
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = drag.key == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    HomePage(onOpenCovers = { showCovers = true })
                } else {
                    AppsPage(
                        entries = entries,
                        apps = appMap,
                        results = results,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        drag = drag,
                        onOpenApp = { launchApp(context, it) },
                        onOpenFolder = { openFolderId = it },
                        onAppMenu = { pkg -> appMenu = Pair(pkg, null) },
                        onFolderMenu = { folderMenu = it },
                        onMerge = { a, b -> mergeEntry(a, b) },
                        onMove = { a, b -> moveEntry(a, b) }
                    )
                }
            }

            PageDots(current = pagerState.currentPage, count = 2)
            Spacer(Modifier.height(8.dp))
            Dock(context)
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- uygulamaya basılı tutunca menü
    val am = appMenu
    if (am != null) {
        val (pkg, folderId) = am
        val label = appMap[pkg]?.label ?: pkg
        val actions = ArrayList<Pair<String, () -> Unit>>()
        actions.add("Uygulama bilgisi" to { openAppInfo(context, pkg) })
        actions.add("Kaldır" to { uninstallApp(context, pkg) })
        if (folderId != null) {
            actions.add("Klasörden çıkar" to { removeFromFolder(folderId, pkg) })
        }
        ActionMenu(title = label, actions = actions, onDismiss = { appMenu = null })
    }

    // ---- klasöre basılı tutunca menü
    val fm = folderMenu
    if (fm != null) {
        val folder = entries.filterIsInstance<HomeEntry.Folder>().firstOrNull { it.id == fm }
        if (folder != null) {
            ActionMenu(
                title = folder.name,
                actions = listOf<Pair<String, () -> Unit>>(
                    "Yeniden adlandır" to { renameFolderId = folder.id },
                    "Klasörü dağıt" to { dissolveFolder(folder.id) }
                ),
                onDismiss = { folderMenu = null }
            )
        }
    }

    // ---- klasör adı
    val rf = renameFolderId
    if (rf != null) {
        val folder = entries.filterIsInstance<HomeEntry.Folder>().firstOrNull { it.id == rf }
        if (folder != null) {
            NameDialog(
                title = "Klasör adı",
                initial = folder.name,
                onConfirm = { renameFolder(rf, it); renameFolderId = null },
                onDismiss = { renameFolderId = null }
            )
        }
    }

    // ---- açık klasör
    val of = openFolderId
    if (of != null) {
        val folder = entries.filterIsInstance<HomeEntry.Folder>().firstOrNull { it.id == of }
        if (folder != null) {
            FolderDialog(
                folder = folder,
                apps = appMap,
                onRename = { renameFolder(folder.id, it) },
                onLaunch = { openFolderId = null; launchApp(context, it) },
                onAppMenu = { pkg -> appMenu = Pair(pkg, folder.id) },
                onDismiss = { openFolderId = null }
            )
        }
    }

    // ---- kapak resmi seçici
    if (showCovers) {
        CoverPickerDialog(
            current = coverRef,
            applyLock = applyLock,
            onSelect = { onSetCover(it) },
            onPickGallery = { onPickGallery() },
            onApplyLockChanged = onApplyLockChanged,
            onApplyLockNow = onApplyLockNow,
            onDismiss = { showCovers = false }
        )
    }
}

// ------------------------------------------------------------------ sayfalar

@Composable
fun HomePage(onOpenCovers: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenCovers) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                    contentDescription = "Kapak resmi",
                    tint = Gold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        ClockBlock()
        Spacer(Modifier.height(28.dp))
        OsmanliWidgetCard()
    }
}

@Composable
fun ClockBlock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 50L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale("tr")).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr")).format(Date(now)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = time, color = Gold, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text(text = date, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun AppsPage(
    entries: List<HomeEntry>,
    apps: Map<String, AppItem>,
    results: List<AppItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    drag: DragState,
    onOpenApp: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onFolderMenu: (String) -> Unit,
    onMerge: (String, String) -> Unit,
    onMove: (String, String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Uygulamalarda Ara...", color = Gold.copy(alpha = 0.7f)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Gold,
                focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.35f)
            )
        )
        Spacer(Modifier.height(12.dp))

        if (searchQuery.isNotBlank()) {
            // arama sonuçları
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(results, key = { it.packageName }) { app ->
                    AppIconItem(
                        app = app,
                        onClick = { onOpenApp(app.packageName) },
                        onLongPress = { onAppMenu(app.packageName) }
                    )
                }
            }
        } else {
            AppsGrid(
                entries = entries,
                apps = apps,
                drag = drag,
                onOpenApp = onOpenApp,
                onOpenFolder = onOpenFolder,
                onAppMenu = onAppMenu,
                onFolderMenu = onFolderMenu,
                onMerge = onMerge,
                onMove = onMove
            )
        }
    }
}

// ------------------------------------------------------------------ sürükle-bırak ızgarası

@Composable
fun AppsGrid(
    entries: List<HomeEntry>,
    apps: Map<String, AppItem>,
    drag: DragState,
    onOpenApp: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onFolderMenu: (String) -> Unit,
    onMerge: (String, String) -> Unit,
    onMove: (String, String?) -> Unit
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val slop = with(density) { 12.dp.toPx() }
    val half = with(density) { 27.dp.toPx() }

    val entriesNow by rememberUpdatedState(entries)
    val onAppMenuNow by rememberUpdatedState(onAppMenu)
    val onFolderMenuNow by rememberUpdatedState(onFolderMenu)
    val onMergeNow by rememberUpdatedState(onMerge)
    val onMoveNow by rememberUpdatedState(onMove)

    val dragKey = drag.key

    // üstüne gelinen öğe (klasör oluşturma adayı)
    var mergeKey: String? = null
    if (dragKey != null) {
        val over = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key != dragKey && hit(it, drag.pointer) }
        if (over != null && isCenter(over, drag.pointer)) mergeKey = over.key as? String
    }

    // kenara yaklaşınca otomatik kaydır
    LaunchedEffect(dragKey) {
        while (drag.key != null) {
            val h = gridState.layoutInfo.viewportSize.height
            val y = drag.pointer.y
            if (y < 90f) gridState.scrollBy(-22f)
            else if (h > 0 && y > h - 90f) gridState.scrollBy(22f)
            delay(16)
        }
    }

    val finishDrag: () -> Unit = {
        val k = drag.key
        if (k != null) {
            val p = drag.pointer
            val infos = gridState.layoutInfo.visibleItemsInfo
            if (drag.moved < slop) {
                // sadece basılı tutuldu -> menü
                val e = entriesNow.firstOrNull { it.key == k }
                if (e is HomeEntry.App) onAppMenuNow(e.pkg)
                else if (e is HomeEntry.Folder) onFolderMenuNow(e.id)
            } else {
                val target = infos.firstOrNull { it.key != k && hit(it, p) }
                if (target != null) {
                    val tk = target.key as? String
                    if (tk != null) {
                        if (isCenter(target, p)) onMergeNow(k, tk) else onMoveNow(k, tk)
                    }
                } else {
                    val lastBottom = infos.maxOfOrNull { it.offset.y + it.size.height } ?: 0
                    if (p.y > lastBottom) {
                        onMoveNow(k, null)
                    } else {
                        val nearest = infos.filter { it.key != k }.minByOrNull {
                            val cx = it.offset.x + it.size.width / 2f
                            val cy = it.offset.y + it.size.height / 2f
                            hypot(p.x - cx, p.y - cy)
                        }
                        val nk = nearest?.key as? String
                        if (nk != null) onMoveNow(k, nk)
                    }
                }
            }
        }
        drag.key = null
        drag.lastEnd = System.currentTimeMillis()
    }
    val finishDragNow by rememberUpdatedState(finishDrag)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            userScrollEnabled = dragKey == null,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { hit(it, offset) }
                            val k = info?.key as? String
                            if (k != null) {
                                drag.key = k
                                drag.pointer = offset
                                drag.moved = 0f
                            }
                        },
                        onDrag = { change, amount ->
                            if (drag.key != null) {
                                change.consume()
                                drag.pointer = drag.pointer + amount
                                drag.moved += amount.getDistance()
                            }
                        },
                        onDragEnd = { finishDragNow() },
                        onDragCancel = { drag.key = null }
                    )
                }
        ) {
            items(entries, key = { it.key }) { entry ->
                val dimmed = entry.key == dragKey
                Box(modifier = Modifier.alpha(if (dimmed) 0.25f else 1f)) {
                    when (entry) {
                        is HomeEntry.App -> {
                            val app = apps[entry.pkg]
                            if (app != null) {
                                AppIconItem(
                                    app = app,
                                    highlight = mergeKey == entry.key,
                                    onClick = { if (drag.tapAllowed()) onOpenApp(entry.pkg) }
                                )
                            }
                        }
                        is HomeEntry.Folder -> {
                            FolderIconItem(
                                folder = entry,
                                apps = apps,
                                highlight = mergeKey == entry.key,
                                onClick = { if (drag.tapAllowed()) onOpenFolder(entry.id) }
                            )
                        }
                    }
                }
            }
        }

        // parmağın altında süzülen kopya
        if (dragKey != null) {
            val dragged = entries.firstOrNull { it.key == dragKey }
            if (dragged != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (drag.pointer.x - half).roundToInt(),
                                (drag.pointer.y - half).roundToInt()
                            )
                        }
                        .size(54.dp)
                        .alpha(0.92f)
                ) {
                    when (dragged) {
                        is HomeEntry.App -> apps[dragged.pkg]?.let { AppTile(it, false) }
                        is HomeEntry.Folder -> FolderTile(dragged, apps, false)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ simgeler

@Composable
fun AppTile(app: AppItem, highlight: Boolean) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0xCC132F45), RoundedCornerShape(16.dp))
            .border(
                if (highlight) 2.dp else 1.dp,
                if (highlight) Gold else Gold.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
fun FolderTile(folder: HomeEntry.Folder, apps: Map<String, AppItem>, highlight: Boolean) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0x66FFFFFF), RoundedCornerShape(16.dp))
            .border(
                if (highlight) 2.dp else 1.dp,
                if (highlight) Gold else Gold.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(5.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (row in folder.pkgs.take(4).chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (pkg in row) {
                        val icon = apps[pkg]?.icon
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(19.dp))
                        } else {
                            Spacer(Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconItem(
    app: AppItem,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val clickNow by rememberUpdatedState(onClick)
    val longNow by rememberUpdatedState(onLongPress)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { clickNow() },
                    onLongPress = if (onLongPress != null) {
                        { longNow?.invoke() }
                    } else null
                )
            }
            .padding(6.dp)
    ) {
        AppTile(app, highlight)
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FolderIconItem(
    folder: HomeEntry.Folder,
    apps: Map<String, AppItem>,
    highlight: Boolean,
    onClick: () -> Unit
) {
    val clickNow by rememberUpdatedState(onClick)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { clickNow() }) }
            .padding(6.dp)
    ) {
        FolderTile(folder, apps, highlight)
        Spacer(Modifier.height(6.dp))
        Text(
            text = folder.name,
            color = Gold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ------------------------------------------------------------------ pencereler

@Composable
fun ActionMenu(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            border = BorderStroke(1.dp, Gold)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                for (action in actions) {
                    Text(
                        text = action.first,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action.second()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            border = BorderStroke(1.dp, Gold)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Gold
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Vazgeç",
                        color = Color.White,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(12.dp)
                    )
                    Text(
                        "Tamam",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onConfirm(text) }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderDialog(
    folder: HomeEntry.Folder,
    apps: Map<String, AppItem>,
    onRename: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onAppMenu: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            border = BorderStroke(1.dp, Gold)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onRename(it)
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Gold.copy(alpha = 0.3f),
                        cursorColor = Gold
                    )
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folder.pkgs, key = { it }) { pkg ->
                        val app = apps[pkg]
                        if (app != null) {
                            AppIconItem(
                                app = app,
                                onClick = { onLaunch(pkg) },
                                onLongPress = { onAppMenu(pkg) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverPickerDialog(
    current: String,
    applyLock: Boolean,
    onSelect: (String) -> Unit,
    onPickGallery: () -> Unit,
    onApplyLockChanged: (Boolean) -> Unit,
    onApplyLockNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val covers = remember { Covers.listAssets(context) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1E2D)),
            border = BorderStroke(1.dp, Gold)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Kapak Resmi (${covers.size} Osmanlı teması)",
                    color = Gold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPickGallery,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("Galeriden seç") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onApplyLockNow,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Gold),
                        border = BorderStroke(1.dp, Gold)
                    ) { Text("Kilit ekranına uygula") }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = applyLock,
                        onCheckedChange = onApplyLockChanged,
                        colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Seçtiğim kapak kilit ekranına da uygulansın", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(covers, key = { it }) { ref ->
                        CoverThumb(ref = ref, selected = ref == current, onClick = { onSelect(ref) })
                    }
                }
            }
        }
    }
}

@Composable
fun CoverThumb(ref: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, ref) {
        value = withContext(Dispatchers.IO) {
            Covers.decode(context, ref, 200, 420)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .aspectRatio(0.46f)
            .clip(shape)
            .background(Color(0xFF132F45))
            .border(if (selected) 3.dp else 1.dp, if (selected) Gold else Gold.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ------------------------------------------------------------------ kartlar, dock

@Composable
fun OsmanliWidgetCard() {
    val hijriYear = remember {
        runCatching { HijrahDate.from(LocalDate.now()).get(ChronoField.YEAR) }.getOrDefault(1448)
    }
    val currentDate = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy.copy(alpha = 0.88f)),
        border = BorderStroke(1.5.dp, Gold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.osmanli_amblem),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(1.dp, Gold, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("OSMANLI TAKVİMİ", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$hijriYear | Hicri",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Gold.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("Miladi Tarih", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(currentDate, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PageDots(current: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until count) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .size(if (i == current) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Gold else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun Dock(context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Navy.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockIcon(iconRes = android.R.drawable.ic_menu_call, label = "Telefon") {
            safeStart(context, Intent(Intent.ACTION_DIAL))
        }
        DockIcon(iconRes = android.R.drawable.ic_menu_send, label = "Mesajlar") {
            safeStart(
                context,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            )
        }
        DockIcon(iconRes = android.R.drawable.ic_menu_camera, label = "Kamera") {
            safeStart(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
        DockIcon(iconRes = android.R.drawable.ic_menu_preferences, label = "Şarj Show") {
            safeStart(context, Intent(context, SettingsActivity::class.java))
        }
    }
}

@Composable
fun DockIcon(iconRes: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = Gold,
            modifier = Modifier.size(28.dp)
        )
    }
}
