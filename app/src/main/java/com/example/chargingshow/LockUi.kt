package com.example.chargingshow

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OGold = Color(0xFFD4AF37)
private val ONavy = Color(0xFF0F2A3F)
private val ORed = Color(0xFFE5534B)

/** Bir kilit kurulumu isteği (ana kilit ya da uygulamaya özel). */
data class SetupRequest(
    val ns: String,
    val title: String,
    val pkg: String?,        // doluysa kurulum bitince bu uygulama kilitlenir
    val openSettings: Boolean // bitince "Kilit Ekranı" ayarları tekrar açılsın mı
)

// ------------------------------------------------------------------ süs ve küçük parçalar

@Composable
fun OttomanBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1E2D), Color(0xFF040A10))))
    ) {
        // çift çizgili altın çerçeve
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.5.dp, OGold, RoundedCornerShape(26.dp))
                .padding(5.dp)
                .border(0.8.dp, OGold.copy(alpha = 0.45f), RoundedCornerShape(21.dp))
        ) {}
        content()
    }
}

private fun polygon(c: Offset, r: Float, n: Int, start: Double): Path {
    val p = Path()
    for (i in 0 until n) {
        val a = start + i * 2.0 * Math.PI / n
        val x = c.x + (r * cos(a)).toFloat()
        val y = c.y + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** Sekiz köşeli Selçuklu/Osmanlı yıldızı. */
@Composable
fun StarEmblem(emblemSize: Dp = 64.dp) {
    Canvas(Modifier.size(emblemSize)) {
        val c = center
        val r = this.size.minDimension / 2f
        drawCircle(OGold.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(width = 2f))
        drawCircle(OGold.copy(alpha = 0.25f), radius = r * 0.62f, center = c, style = Stroke(width = 1.5f))
        drawPath(polygon(c, r * 0.92f, 4, Math.PI / 4), OGold, style = Stroke(width = 3f))
        drawPath(polygon(c, r * 0.92f, 4, 0.0), OGold, style = Stroke(width = 3f))
        drawCircle(OGold, radius = r * 0.12f, center = c)
    }
}

@Composable
fun OttomanFullDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun OButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = if (filled) {
            ButtonDefaults.buttonColors(
                containerColor = OGold,
                contentColor = Color.Black,
                disabledContainerColor = OGold.copy(alpha = 0.3f),
                disabledContentColor = Color.Black.copy(alpha = 0.5f)
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = ONavy,
                contentColor = OGold,
                disabledContainerColor = ONavy.copy(alpha = 0.5f),
                disabledContentColor = OGold.copy(alpha = 0.4f)
            )
        },
        border = if (filled) null else BorderStroke(1.dp, OGold)
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
fun OLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = OGold,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
fun OCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ONavy.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
            .border(1.dp, OGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun OSwitchRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    desc: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = OGold, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun OConfirm(
    title: String,
    text: String,
    okText: String,
    onOk: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ONavy),
            border = BorderStroke(1.dp, OGold)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = OGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(text, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OButton("Vazgeç", onCancel, filled = false)
                    Spacer(Modifier.width(8.dp))
                    OButton(okText, onOk)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ PIN tuş takımı

@Composable
fun PinDots(filled: Int, slots: Int, error: Boolean) {
    val col = if (error) ORed else OGold
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until maxOf(slots, filled)) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) col else Color.Transparent)
                    .border(1.5.dp, col.copy(alpha = 0.8f), CircleShape)
            )
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    val a = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(ONavy.copy(alpha = 0.7f), CircleShape)
            .border(1.dp, OGold.copy(alpha = 0.8f * a), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = OGold.copy(alpha = a), fontSize = 26.sp)
    }
}

@Composable
fun PinPad(
    enabled: Boolean,
    canSubmit: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("←", "0", "✓")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (k in row) {
                    val keyEnabled = enabled && (k != "✓" || canSubmit)
                    PinKey(label = k, enabled = keyEnabled) {
                        when (k) {
                            "←" -> onBackspace()
                            "✓" -> onSubmit()
                            else -> onDigit(k[0])
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ desen çizme

/** 3x3 nokta. Bitince "01258" gibi nokta numaralarını (0-8) verir. */
@Composable
fun PatternPad(
    modifier: Modifier = Modifier,
    error: Boolean = false,
    enabled: Boolean = true,
    onStart: () -> Unit = {},
    onComplete: (String) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val completeNow by rememberUpdatedState(onComplete)
    val startNow by rememberUpdatedState(onStart)

    fun centerOf(i: Int): Offset {
        val cell = boxSize.width / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }

    fun hitDot(p: Offset): Int {
        val cell = boxSize.width / 3f
        for (i in 0..8) {
            val c = centerOf(i)
            if (hypot(p.x - c.x, p.y - c.y) < cell * 0.3f) return i
        }
        return -1
    }

    fun addDot(i: Int) {
        if (selected.contains(i)) return
        if (selected.isNotEmpty()) {
            val a = selected[selected.size - 1]
            val ra = a / 3
            val ca = a % 3
            val rb = i / 3
            val cb = i % 3
            // iki nokta arasında atlanan ortadaki nokta varsa onu da ekle
            if ((ra + rb) % 2 == 0 && (ca + cb) % 2 == 0) {
                val mid = ((ra + rb) / 2) * 3 + (ca + cb) / 2
                if (mid != a && mid != i && !selected.contains(mid)) selected.add(mid)
            }
        }
        selected.add(i)
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(enabled, boxSize) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (done) return@awaitEachGesture
                    startNow()
                    selected.clear()
                    finger = down.position
                    val first = hitDot(down.position)
                    if (first >= 0) addDot(first)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: break
                        if (ch.pressed) {
                            finger = ch.position
                            val h = hitDot(ch.position)
                            if (h >= 0) addDot(h)
                            ch.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    finger = null
                    val pat = selected.joinToString("")
                    if (pat.isNotEmpty()) {
                        done = true
                        completeNow(pat)
                        scope.launch {
                            delay(450)
                            selected.clear()
                            done = false
                        }
                    }
                }
            }
    ) {
        val col = if (error) ORed else OGold
        val cell = size.width / 3f
        for (k in 0 until selected.size - 1) {
            drawLine(
                color = col,
                start = centerOf(selected[k]),
                end = centerOf(selected[k + 1]),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
        val f = finger
        if (f != null && selected.isNotEmpty()) {
            drawLine(
                color = col.copy(alpha = 0.6f),
                start = centerOf(selected[selected.size - 1]),
                end = f,
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0..8) {
            val c = centerOf(i)
            val on = selected.contains(i)
            drawCircle(col.copy(alpha = if (on) 0.25f else 0.10f), radius = cell * 0.28f, center = c)
            drawCircle(col, radius = if (on) cell * 0.09f else cell * 0.06f, center = c)
            if (on) drawCircle(col, radius = cell * 0.28f, center = c, style = Stroke(width = 2.5f))
        }
    }
}

// ------------------------------------------------------------------ kimlik doğrulama

/**
 * PIN / desen / parmak izi doğrulama ekranı. Hangi yöntemler kuruluysa onlar sunulur.
 * Parmak izi okunmazsa ya da vazgeçilirse PIN veya desenle devam edilir.
 */
@Composable
fun AuthScreen(
    ns: String,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findFragmentActivity() }
    val hasPin = remember(ns) { LockStore.hasPin(ctx, ns) }
    val hasPat = remember(ns) { LockStore.hasPattern(ctx, ns) }
    val hasFp = remember(ns) { LockStore.hasFingerprint(ctx, ns) && activity != null }
    val hasFace = remember(ns) { LockStore.hasFace(ctx, ns) && activity != null }
    val hasBio = hasFp || hasFace
    val bioName = if (hasFace && !hasFp) "Yüz tarama" else if (hasFp && !hasFace) "Parmak izi" else "Parmak izi / Yüz"
    val pinLen = remember(ns) { LockStore.pinLength(ctx, ns) }

    var mode by remember(ns) { mutableIntStateOf(if (hasPat) 1 else if (hasPin) 0 else 2) } // 0 PIN, 1 desen, 2 yalnız parmak izi
    var pin by remember(ns) { mutableStateOf("") }
    var error by remember(ns) { mutableStateOf<String?>(null) }
    var lockMs by remember(ns) { mutableLongStateOf(LockStore.lockRemainingMs(ctx)) }
    var fpBusy by remember(ns) { mutableStateOf(false) }
    val successNow by rememberUpdatedState(onSuccess)

    fun ok() {
        LockStore.resetFails(ctx)
        successNow()
    }

    fun fail(msg: String) {
        LockStore.registerFail(ctx)
        lockMs = LockStore.lockRemainingMs(ctx)
        error = if (lockMs > 0) null else msg
        pin = ""
    }

    fun askFp() {
        val a = activity ?: return
        if (fpBusy) return
        fpBusy = true
        BiometricHelper.prompt(
            activity = a,
            title = bioName,
            subtitle = subtitle,
            fallbackText = if (hasPin || hasPat) "PIN / Desen kullan" else "Vazgeç",
            strongOnly = hasFp && !hasFace && BiometricHelper.availableStrong(ctx),
            onSuccess = {
                fpBusy = false
                ok()
            },
            onError = { m ->
                fpBusy = false
                if (m != null) error = m
            }
        )
    }

    fun submitPin() {
        if (pin.isEmpty()) return
        if (LockStore.checkPin(ctx, ns, pin)) ok() else fail("Yanlış PIN")
    }

    // kilitlenme sayacı
    LaunchedEffect(lockMs > 0) {
        while (lockMs > 0) {
            delay(500)
            lockMs = LockStore.lockRemainingMs(ctx)
        }
    }
    // parmak izi varsa ekran açılır açılmaz sor
    LaunchedEffect(ns) {
        if (hasBio) {
            delay(300)
            askFp()
        }
    }

    val locked = lockMs > 0

    OttomanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            StarEmblem(56.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = OGold, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))

            when (mode) {
                0 -> {
                    PinDots(filled = pin.length, slots = if (pinLen > 0) pinLen else 4, error = error != null)
                    Spacer(Modifier.height(18.dp))
                    PinPad(
                        enabled = !locked,
                        canSubmit = pin.isNotEmpty(),
                        onDigit = { d ->
                            if (pin.length < 8) {
                                error = null
                                pin += d
                                if (pin.length == pinLen) submitPin()
                            }
                        },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        onSubmit = { submitPin() }
                    )
                }
                1 -> {
                    PatternPad(
                        modifier = Modifier.size(270.dp),
                        error = error != null,
                        enabled = !locked,
                        onStart = { error = null },
                        onComplete = { p ->
                            if (p.length < 4) {
                                error = "En az 4 nokta birleştirin"
                            } else if (LockStore.checkPattern(ctx, ns, p)) {
                                ok()
                            } else {
                                fail("Yanlış desen")
                            }
                        }
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(ONavy.copy(alpha = 0.7f), CircleShape)
                            .border(2.dp, OGold, CircleShape)
                            .clickable { askFp() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (hasFace && !hasFp) "Yüzünü\ntara" else if (hasFp && !hasFace) "Parmak izini\nokutun" else "Parmak izi veya\nyüz tarama",
                            color = OGold, fontSize = 18.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (locked) {
                Text(
                    "Çok fazla yanlış deneme. ${(lockMs + 999) / 1000} sn bekleyin.",
                    color = ORed, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            } else if (error != null) {
                Text(error ?: "", color = ORed, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPin && hasPat) {
                    OLink(if (mode == 1) "PIN ile gir" else "Desenle gir") {
                        mode = if (mode == 1) 0 else 1
                        pin = ""
                        error = null
                    }
                }
                if (hasBio) OLink(bioName) { askFp() }
                OLink("Vazgeç") { onCancel() }
            }
        }
    }
}

// ------------------------------------------------------------------ kilit kurulumu

private enum class SetupStep { CHOOSE, PIN1, PIN2, PAT1, PAT2, FP, FACE }

/**
 * Kilit kurma sihirbazı: PIN, desen ve parmak izinden istediğin kombinasyonu seç.
 * Bitince [onDone] çağrılır; vazgeçilirse hiçbir şey değişmez.
 */
@Composable
fun CredentialSetupScreen(
    ns: String,
    title: String,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findFragmentActivity() }
    val fpAvailable = remember { activity != null && BiometricHelper.fingerprintAvailable(ctx) }
    val faceAvailable = remember { activity != null && BiometricHelper.available(ctx) }

    var wantPin by remember { mutableStateOf(LockStore.hasPin(ctx, ns) || !LockStore.isSet(ctx, ns)) }
    var wantPat by remember { mutableStateOf(LockStore.hasPattern(ctx, ns)) }
    var wantFp by remember { mutableStateOf(LockStore.hasFingerprint(ctx, ns) && fpAvailable) }
    var wantFace by remember { mutableStateOf(LockStore.hasFace(ctx, ns) && faceAvailable) }
    var step by remember { mutableStateOf(SetupStep.CHOOSE) }
    var pinInput by remember { mutableStateOf("") }
    var tempPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var tempPat by remember { mutableStateOf("") }
    var newPat by remember { mutableStateOf("") }
    var fpOk by remember { mutableStateOf(false) }
    var faceOk by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    // after: 0 = seçim, 1 = PIN, 2 = desen, 3 = parmak izi, 4 = yüz tarama
    fun goNext(after: Int) {
        msg = null
        pinInput = ""
        var s = after + 1
        while (s <= 4) {
            if (s == 1 && wantPin) { step = SetupStep.PIN1; return }
            if (s == 2 && wantPat) { step = SetupStep.PAT1; return }
            if (s == 3 && wantFp) { step = SetupStep.FP; return }
            if (s == 4 && wantFace) { step = SetupStep.FACE; return }
            s++
        }
        LockStore.save(
            ctx, ns,
            if (wantPin) newPin else null,
            if (wantPat) newPat else null,
            wantFp && fpOk,
            wantFace && faceOk
        )
        onDone()
    }

    OttomanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            StarEmblem(52.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = OGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))

            when (step) {
                SetupStep.CHOOSE -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "PIN veya desen seç; istersen yanına parmak izi ya da yüz tarama ekle (örn. desen + parmak izi, PIN + yüz). Parmak izi / yüz okumazsa PIN veya desenle girersin.",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        OCard {
                            OSwitchRow(wantPin, true, "PIN", "4-8 haneli rakam şifresi") { wantPin = it }
                            OSwitchRow(wantPat, true, "Desen", "9 noktayı birleştirerek çiz") { wantPat = it }
                            OSwitchRow(
                                wantFp, fpAvailable, "Parmak izi",
                                if (fpAvailable) "Telefonda kayıtlı parmak izi ile aç"
                                else "Telefonda kayıtlı parmak izi yok (Ayarlar'dan ekle)"
                            ) { wantFp = it }
                            OSwitchRow(
                                wantFace, faceAvailable, "Yüz tarama",
                                if (faceAvailable) "Telefonun yüz tanıma özelliği ile aç (telefonda kayıtlı olmalı)"
                                else "Telefonda kayıtlı yüz tanıma yok (Ayarlar'dan ekle)"
                            ) { wantFace = it }
                        }
                        if ((wantFp || wantFace) && !wantPin && !wantPat) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Parmak izi ve yüz tarama tek başına olamaz. Yanına PIN veya desen seç.",
                                color = ORed, fontSize = 13.sp
                            )
                        }
                    }
                    OButton(
                        "Devam",
                        onClick = { goNext(0) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = wantPin || wantPat
                    )
                }
                SetupStep.PIN1, SetupStep.PIN2 -> {
                    val second = step == SetupStep.PIN2
                    Text(
                        if (second) "PIN'i tekrar gir" else "PIN belirle (4-8 hane)",
                        color = Color.White, fontSize = 16.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    PinDots(filled = pinInput.length, slots = 4, error = msg != null)
                    Spacer(Modifier.height(16.dp))
                    PinPad(
                        enabled = true,
                        canSubmit = pinInput.length >= 4,
                        onDigit = { d -> if (pinInput.length < 8) { msg = null; pinInput += d } },
                        onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) },
                        onSubmit = {
                            if (!second) {
                                tempPin = pinInput
                                pinInput = ""
                                step = SetupStep.PIN2
                            } else if (pinInput == tempPin) {
                                newPin = pinInput
                                goNext(1)
                            } else {
                                msg = "PIN'ler eşleşmedi, baştan dene"
                                pinInput = ""
                                step = SetupStep.PIN1
                            }
                        }
                    )
                }
                SetupStep.PAT1, SetupStep.PAT2 -> {
                    val second = step == SetupStep.PAT2
                    Text(
                        if (second) "Deseni tekrar çiz" else "Desen çiz (en az 4 nokta)",
                        color = Color.White, fontSize = 16.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    PatternPad(
                        modifier = Modifier.size(270.dp),
                        error = msg != null,
                        onStart = { msg = null },
                        onComplete = { p ->
                            if (p.length < 4) {
                                msg = "En az 4 nokta birleştirin"
                            } else if (!second) {
                                tempPat = p
                                step = SetupStep.PAT2
                            } else if (p == tempPat) {
                                newPat = p
                                goNext(2)
                            } else {
                                msg = "Desenler eşleşmedi, baştan dene"
                                step = SetupStep.PAT1
                            }
                        }
                    )
                }
                SetupStep.FP -> {
                    Text(
                        "Parmak izini bir kez okutarak doğrula",
                        color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OButton("Parmak izini okut", onClick = {
                        val a = activity
                        if (a != null) {
                            BiometricHelper.prompt(
                                activity = a,
                                title = "Parmak izi",
                                subtitle = "Kilidi etkinleştirmek için doğrula",
                                fallbackText = "Vazgeç",
                                strongOnly = BiometricHelper.availableStrong(ctx),
                                onSuccess = {
                                    fpOk = true
                                    goNext(3)
                                },
                                onError = { m -> if (m != null) msg = m }
                            )
                        }
                    })
                }
                SetupStep.FACE -> {
                    Text(
                        "Yüzünü bir kez tarayarak doğrula",
                        color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OButton("Yüz taramayı başlat", onClick = {
                        val a = activity
                        if (a != null) {
                            BiometricHelper.prompt(
                                activity = a,
                                title = "Yüz tarama",
                                subtitle = "Kilidi etkinleştirmek için doğrula",
                                fallbackText = "Vazgeç",
                                strongOnly = false,
                                onSuccess = {
                                    faceOk = true
                                    goNext(4)
                                },
                                onError = { m -> if (m != null) msg = m }
                            )
                        }
                    })
                }
            }

            val m = msg
            if (m != null) {
                Spacer(Modifier.height(10.dp))
                Text(m, color = ORed, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (step != SetupStep.CHOOSE) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(6.dp))
            OLink("Vazgeç") { onCancel() }
        }
    }
}

// ------------------------------------------------------------------ Kilit Ekranı ayarları

object GuardUtil {
    fun isGuardServiceEnabled(ctx: android.content.Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = android.content.ComponentName(ctx, OsmanliGuardService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }
}

private fun safeOpen(ctx: android.content.Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // açılamadıysa çökmesin
    }
}

@Composable
fun LockSettingsScreen(
    appLabels: Map<String, String>,
    onChangeMaster: () -> Unit,
    onChanged: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var showManage by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    val accOn = remember(tick) { GuardUtil.isGuardServiceEnabled(ctx) }
    val overlayOn = remember(tick) { Settings.canDrawOverlays(ctx) }
    val guardOn = remember(ver) { LockStore.guardEnabled(ctx) }
    val masterSet = remember(ver, tick) { LockStore.isSet(ctx, LockStore.MASTER) }
    val summary = remember(ver, tick) { LockStore.describe(ctx, LockStore.MASTER) }
    val lockedCount = remember(ver, tick) { LockStore.lockedApps(ctx).size }

    OttomanFullDialog(onDismiss = onClose) {
        OttomanBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))
                StarEmblem(52.dp)
                Spacer(Modifier.height(6.dp))
                Text("Kilit Ekranı", color = OGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                // ---- ana kilit
                OCard {
                    Text("Ana kilit", color = OGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Durum: $summary", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OButton(
                            if (masterSet) "Değiştir" else "Kur",
                            onClick = onChangeMaster,
                            modifier = Modifier.weight(1f)
                        )
                        if (masterSet) {
                            OButton(
                                "Kaldır",
                                onClick = { confirmRemove = true },
                                modifier = Modifier.weight(1f),
                                filled = false
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ---- uygulama kilitleri
                OCard {
                    Text("Uygulama kilitleri", color = OGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kilitli uygulama sayısı: $lockedCount\nUygulama kilitlemek için ana ekranda uygulamaya basılı tut → \"Kilit koy\".",
                        color = Color.White, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OButton("Tüm şifreleri yönet", onClick = { showManage = true }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))

                // ---- kaldırma koruması
                OCard {
                    Text("Kaldırma ve kilit koruması", color = OGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    OSwitchRow(
                        checked = guardOn,
                        enabled = masterSet,
                        title = "Korumayı aç",
                        desc = "Bu uygulamayı kaldırmadan, durdurmadan ya da koruma hizmetini kapatmadan önce ana kilit sorulur"
                    ) {
                        LockStore.setGuard(ctx, it)
                        ver++
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Erişilebilirlik hizmeti: " + (if (accOn) "Açık ✓" else "KAPALI ✗"),
                        color = if (accOn) Color(0xFF6FD08C) else ORed, fontSize = 14.sp
                    )
                    Text(
                        "Üstte gösterme izni: " + (if (overlayOn) "Açık ✓" else "KAPALI ✗"),
                        color = if (overlayOn) Color(0xFF6FD08C) else ORed, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!accOn) {
                        OButton(
                            "Erişilebilirlik ayarlarını aç",
                            onClick = { safeOpen(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(), filled = false
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!overlayOn) {
                        OButton(
                            "Üstte gösterme iznini aç",
                            onClick = {
                                safeOpen(
                                    ctx,
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth(), filled = false
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    OButton(
                        "Uygulama bilgisi (izinler)",
                        onClick = {
                            safeOpen(
                                ctx,
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth(), filled = false
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Erişilebilirlik listesinde \"Osmanlı Koruma\" hizmetini aç.\n" +
                            "• Android 13+ ve APK ile kurulduysa hizmet gri görünebilir: Uygulama bilgisi → sağ üst ⋮ → \"Kısıtlanmış ayarlara izin ver\".\n" +
                            "• Xiaomi/MIUI: Uygulama bilgisi → Diğer izinler → \"Arka planda açılan pencereler\" → İzin ver; ayrıca Pil → Kısıtlama yok.\n" +
                            "• Ayarlara gidip döndüğünde Kilit Ekranı'na yeniden gir.",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                OLink("Kapat") { onClose() }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showManage) {
        ManageLocksScreen(
            appLabels = appLabels,
            onChanged = {
                ver++
                onChanged()
            },
            onClose = { showManage = false }
        )
    }

    if (confirmRemove) {
        OConfirm(
            title = "Ana kilit kaldırılsın mı?",
            text = "Ana kilidi kullanan uygulama kilitleri de kalkar. Uygulamaya özel şifreler etkilenmez.",
            okText = "Kaldır",
            onOk = {
                LockStore.removeMaster(ctx)
                confirmRemove = false
                ver++
                onChanged()
            },
            onCancel = { confirmRemove = false }
        )
    }
}

@Composable
fun ManageLocksScreen(
    appLabels: Map<String, String>,
    onChanged: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val pkgs = remember(ver) {
        LockStore.lockedApps(ctx).toList().sortedBy { (appLabels[it] ?: it).lowercase() }
    }

    OttomanFullDialog(onDismiss = onClose) {
        OttomanBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))
                StarEmblem(48.dp)
                Spacer(Modifier.height(6.dp))
                Text("Tüm şifreleri yönet", color = OGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pkgs.isEmpty()) {
                        Text("Kilitli uygulama yok.", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                    }
                    for (pkg in pkgs) {
                        OCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(appLabels[pkg] ?: pkg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (LockStore.appUsesOwn(ctx, pkg)) "Özel şifre: " + LockStore.describe(ctx, "app.$pkg")
                                        else "Ana kilit kullanılıyor",
                                        color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                OButton("Kilidi kaldır", onClick = {
                                    LockStore.unlockApp(ctx, pkg)
                                    ver++
                                    onChanged()
                                }, filled = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OLink("Geri") { onClose() }
            }
        }
    }
}
