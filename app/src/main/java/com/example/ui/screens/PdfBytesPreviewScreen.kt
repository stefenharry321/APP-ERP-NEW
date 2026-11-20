package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RenderedPdfPreviewRequest
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ErpPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class RenderedPdfPage(
    val bitmap: ImageBitmap,
    val aspectRatio: Float
)

/**
 * Native Android PdfRenderer viewer for exact original Windows ERP PDFs pulled via
 * `erp_mobile_invoice_pull` (preserves official Kumar Foods stamp, UPI QR, and layout pixel-for-pixel).
 */
@Composable
fun PdfBytesPreviewScreen(
    request: RenderedPdfPreviewRequest,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var pages by remember(request) { mutableStateOf<List<RenderedPdfPage>>(emptyList()) }
    var cachedFile by remember(request) { mutableStateOf<File?>(null) }
    var renderError by remember(request) { mutableStateOf<String?>(null) }
    var isRendering by remember(request) { mutableStateOf(true) }

    LaunchedEffect(request) {
        isRendering = true
        renderError = null
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val outDir = File(context.cacheDir, "erp_pdfs").apply { mkdirs() }
                val safeName = request.filename
                    .ifBlank { "KumarFoods_Office_Invoice.pdf" }
                    .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                    .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
                val file = File(outDir, safeName)
                file.writeBytes(request.pdfBytes)

                val rendered = mutableListOf<RenderedPdfPage>()
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(pfd)
                    try {
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            try {
                                val targetW = (page.width * 2).coerceIn(900, 1800)
                                val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                                val targetH = (page.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                rendered.add(
                                    RenderedPdfPage(
                                        bitmap = bmp.asImageBitmap(),
                                        aspectRatio = targetW.toFloat() / targetH.toFloat()
                                    )
                                )
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                } finally {
                    pfd.close()
                }
                file to rendered
            }
        }
        outcome.onSuccess { (file, bitmaps) ->
            cachedFile = file
            pages = bitmaps
            isRendering = false
        }.onFailure { err ->
            renderError = err.localizedMessage ?: "Unable to render original Office PDF."
            isRendering = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("office_pdf_bytes_preview_screen")
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("office_pdf_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = EmeraldOptimal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Original Windows ERP Verified PDF • ${pages.size.coerceAtLeast(1)} page(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOptimal
                    )
                }
            }
        }

        // Document Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ObsidianVoid),
            contentAlignment = Alignment.Center
        ) {
            when {
                isRendering -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TelemetryCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Rendering Original Office PDF...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                renderError != null -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = renderError ?: "PDF Error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CrimsonCritical
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(pages) { idx, page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(14.dp, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                            ) {
                                Image(
                                    bitmap = page.bitmap,
                                    contentDescription = "Original Invoice Page ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(page.aspectRatio)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom SAVE/SHARE & PRINT Sticky Bar (matches Screenshot #2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val file = cachedFile
                    if (file != null && file.exists()) {
                        runCatching {
                            ErpPdfGenerator.sharePdfFile(context, file, request.title)
                        }.onFailure {
                            Toast.makeText(context, "Share failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = cachedFile != null,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("office_pdf_share_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TelemetryCyan)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE / SHARE", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val file = cachedFile
                    if (file != null && file.exists()) {
                        runCatching {
                            ErpPdfGenerator.printPdfFile(context, file, request.title)
                        }.onFailure {
                            Toast.makeText(context, "Print failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = cachedFile != null,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("office_pdf_print_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoltenGold,
                    contentColor = ObsidianVoid
                )
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PRINT", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
