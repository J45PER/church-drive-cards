package com.churchdrive.satellites

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.churchdrive.app.ui.BrandFont
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

private val Teal = Color(0xFF2FB5A5)
private val Base = Color(0xFF6A5BC2)
private val Purple = Color(0xFFAB47BC)
private val Panel = Color(0xFF14181F)

/**
 * The Clive QR scanner (satellite/design/scanner-b.html, idea B2): the camera fills the screen, dimmed, and shows clearly only through the
 * overlap of two squircles in Clive's glow colours: the cut in Clive's logo. When a satellite's code is read the frame turns teal and a card
 * underneath confirms it, to be checked against the satellite's screen or sticker before going on.
 */
@Composable
fun CliveScanner(onResult: (PairCode) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ScannerContent(onResult, onClose)
    }
}

@Composable
private fun ScannerContent(onResult: (PairCode) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var found by remember { mutableStateOf<PairCode?>(null) }
    var other by remember { mutableStateOf(false) }
    val foundAmount by animateFloatAsState(if (found != null) 1f else 0f, tween(450), label = "found")
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "drift",
    )
    LaunchedEffect(other) { if (other) { kotlinx.coroutines.delay(2500); other = false } }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                analyse(proxy, scanner) { raw ->
                    val parsed = PairCode.parse(raw)
                    if (parsed != null) { if (found == null) found = parsed } else other = true
                }
            }
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll(); executor.shutdown(); scanner.close() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // The dimmed screen with the camera clear only where the two squircles overlap, and the two squircles in Clive's glow.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val s = size.width * 0.68f
            val off = s * 0.126f + sin(drift) * 3.dp.toPx() * (1f - foundAmount)
            val r = s * 0.395f
            val cx = size.width / 2f
            val cy = size.height * 0.37f
            fun squircle(dx: Float, dy: Float) = Path().apply {
                addRoundRect(RoundRect(cx - s / 2 + dx, cy - s / 2 + dy, cx + s / 2 + dx, cy + s / 2 + dy, CornerRadius(r)))
            }
            val a = squircle(-off, off)
            val b = squircle(off, -off)
            drawRect(Color.Black.copy(alpha = 0.72f))
            drawPath(Path.combine(PathOperation.Intersect, a, b), Color.Black, blendMode = BlendMode.Clear)
            drawPath(
                Path.combine(PathOperation.Xor, a, b),
                Brush.radialGradient(listOf(Purple, Base), Offset(cx + s * 0.3f, cy - s * 0.25f), s * 0.8f),
                alpha = 0.8f - 0.25f * foundAmount,
            )
            val line = lerp(Color.White, Teal, foundAmount)
            drawPath(a, line, style = Stroke(2.5.dp.toPx()))
            drawPath(b, line, style = Stroke(2.5.dp.toPx()))
        }

        // The close button and title at the top.
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClickLabel = "Close", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 18.sp) }
            Text("Add a satellite", color = Color.White, fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
        }

        // The card underneath: the hint while looking, the code to check once found.
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Panel.copy(alpha = 0.94f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            val f = found
            if (f == null) {
                Text(if (other) "That isn't a satellite's code." else "Point at the satellite's QR code", color = Color.White, fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                Text("on its screen, or the sticker underneath", color = Color.White.copy(alpha = 0.62f), fontFamily = BrandFont, fontSize = 13.sp)
                ScanButton("Type the code instead", false, onClose)
            } else {
                Text("FOUND A SATELLITE", color = Teal, fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.4.sp)
                Text(f.shown, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = 2.sp)
                Text(
                    if (f.code.isNotEmpty()) "${f.id?.text ?: f.device} · check it matches the screen" else "${f.id?.modelName ?: "Satellite"} · check it matches the sticker",
                    color = Color.White.copy(alpha = 0.62f), fontFamily = BrandFont, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                ScanButton("It matches", true) { onResult(f) }
                Text("Scan again", color = Color.White.copy(alpha = 0.7f), fontFamily = BrandFont, fontSize = 13.sp, modifier = Modifier.clickable { found = null }.padding(6.dp))
            }
        }
    }
}

@Composable
private fun ScanButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (filled) Teal else Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) Color(0xFF0B1210) else Color.White, fontFamily = BrandFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyse(proxy: ImageProxy, scanner: BarcodeScanner, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
