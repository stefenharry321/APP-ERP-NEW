package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SalesRegisterEntry
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ErpPdfGenerator
import java.util.Locale
import kotlin.math.roundToLong

private val InvoiceNavy = Color(0xFF12345C)
private val InvoiceAccentBlue = Color(0xFF1A68A8)
private val InvoiceLightBg = Color(0xFFF3F7FB)
private val InvoicePaperBorder = Color(0xFFD0DCE8)
private val InvoiceInk = Color(0xFF151E28)
private val InvoiceMutedInk = Color(0xFF556272)

@Composable
fun InvoicePreviewScreen(
    sale: SalesRegisterEntry,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("invoice_preview_screen")
    ) {
        // Top Liquid Header
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
                    .testTag("invoice_preview_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Invoice ${sale.invoiceNo} • Preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (sale.officePdfUrl != null) "Verified Supabase Office PDF linked"
                    else "Kumar Foods Tax Invoice • Ready to Print/Share",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sale.officePdfUrl != null) TelemetryCyan else TextSecondary
                )
            }
            if (!sale.officePdfUrl.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        runCatching {
                            val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sale.officePdfUrl))
                            context.startActivity(openIntent)
                        }.onFailure {
                            Toast.makeText(context, "Unable to open Office PDF link.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_original_office_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = "Open Original Supabase Office PDF",
                        tint = TelemetryCyan
                    )
                }
            }
        }

        // Authentic A4 Paper Preview Sheet
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, InvoicePaperBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Company + UPI QR + Invoice Info Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, InvoicePaperBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(InvoiceNavy),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("KF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "KUMAR FOODS",
                                    color = InvoiceNavy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 13.sp
                                )
                                Text(
                                    text = "PROCESSING COMPANY",
                                    color = InvoiceNavy,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(InvoiceNavy)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PREMIUM QUALITY | TRUSTED ALWAYS",
                                color = Color.White,
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // UPI QR
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("FOR PAYMENT", color = InvoiceInk, fontSize = 6.5.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, InvoiceNavy)
                                .padding(3.dp)
                        ) {
                            UpiQrCanvas(modifier = Modifier.fillMaxSize())
                        }
                        Text("9758435000-6@ybl", color = InvoiceMutedInk, fontSize = 6.sp)
                    }

                    // Meta Box
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(InvoiceLightBg)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "INV / ${sale.invoiceNo}",
                            color = InvoiceNavy,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        InvoiceMetaLine("Sale No.", sale.saleNo)
                        InvoiceMetaLine("Date", sale.date)
                        InvoiceMetaLine("Vehicle", sale.vehicleNo.ifBlank { "Local" })
                        InvoiceMetaLine("Supply", "UP (09)")
                    }
                }

                // TAX INVOICE Center Header
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "TAX INVOICE",
                        color = InvoiceNavy,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "GSTIN: 09DYOPS4169F2ZO  •  State: Uttar Pradesh (09)",
                            color = InvoiceInk,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "ORIGINAL FOR RECIPIENT",
                            color = InvoiceAccentBlue,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // BILL TO & SHIP TO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddressBox(
                        header = "BILL TO",
                        partyName = sale.partyName,
                        address = sale.partyAddress,
                        gstin = sale.partyGstin,
                        mobile = sale.partyMobile,
                        state = sale.partyState,
                        modifier = Modifier.weight(1f)
                    )
                    AddressBox(
                        header = "SHIP TO",
                        partyName = sale.partyName,
                        address = sale.partyAddress,
                        gstin = null,
                        mobile = sale.partyMobile,
                        state = sale.partyState,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ORDER / ITEM DETAILS Scrollable Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, InvoicePaperBorder, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(InvoiceNavy)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ORDER / ITEM DETAILS",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF1F4977))
                                .padding(vertical = 5.dp, horizontal = 6.dp)
                        ) {
                            TableCell("#", 22.dp, Color.White, true)
                            TableCell("PRODUCT", 78.dp, Color.White, true)
                            TableCell("HSN", 38.dp, Color.White, true)
                            TableCell("PACK(KG)", 52.dp, Color.White, true)
                            TableCell("BAGS", 36.dp, Color.White, true)
                            TableCell("WEIGHT", 58.dp, Color.White, true)
                            TableCell("RATE/KG", 54.dp, Color.White, true)
                            TableCell("RATE/PK", 56.dp, Color.White, true)
                            TableCell("TAXABLE", 64.dp, Color.White, true)
                        }
                        sale.items.forEachIndexed { idx, line ->
                            Row(
                                modifier = Modifier
                                    .background(if (idx % 2 == 1) InvoiceLightBg else Color.White)
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                TableCell("${idx + 1}", 22.dp, InvoiceInk, false)
                                TableCell(line.productName, 78.dp, InvoiceInk, false)
                                TableCell(line.hsnCode, 38.dp, InvoiceMutedInk, false)
                                TableCell(String.format(Locale.US, "%.3f", line.packKg), 52.dp, InvoiceInk, false)
                                TableCell("${line.bags}", 36.dp, InvoiceInk, true)
                                TableCell(String.format(Locale.US, "%.3f", line.totalWeightKg), 58.dp, InvoiceInk, false)
                                TableCell(String.format(Locale.US, "%.2f", line.ratePerKgInclGst), 54.dp, InvoiceInk, false)
                                TableCell(String.format(Locale.US, "%.2f", line.ratePerPackExclGst), 56.dp, InvoiceInk, false)
                                TableCell(String.format(Locale.US, "%.2f", line.taxableAmountInr), 64.dp, InvoiceInk, true)
                            }
                            HorizontalDivider(color = InvoicePaperBorder, thickness = 0.5.dp)
                        }
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFE7EFF7))
                                .padding(vertical = 5.dp, horizontal = 6.dp)
                        ) {
                            TableCell("", 22.dp, InvoiceNavy, true)
                            TableCell("TOTAL", 78.dp, InvoiceNavy, true)
                            TableCell("", 38.dp, InvoiceNavy, true)
                            TableCell("", 52.dp, InvoiceNavy, true)
                            TableCell("${sale.totalBags}", 36.dp, InvoiceNavy, true)
                            TableCell("${String.format(Locale.US, "%.2f", sale.totalWeightKg)} Kg", 58.dp, InvoiceNavy, true)
                            TableCell("", 54.dp, InvoiceNavy, true)
                            TableCell("", 56.dp, InvoiceNavy, true)
                            TableCell(String.format(Locale.US, "%.2f", sale.taxableValueInr), 64.dp, InvoiceNavy, true)
                        }
                    }
                }

                // Tax Details & Grand Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .border(1.dp, InvoicePaperBorder, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(InvoiceNavy)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("TAX DETAILS", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TaxLine("CGST (${String.format(Locale.US, "%.2f", if (sale.isInterState) 0.0 else sale.gstRatePct / 2.0)}%)", sale.cgstInr)
                            TaxLine("SGST (${String.format(Locale.US, "%.2f", if (sale.isInterState) 0.0 else sale.gstRatePct / 2.0)}%)", sale.sgstInr)
                            TaxLine("IGST (${String.format(Locale.US, "%.2f", if (sale.isInterState) sale.gstRatePct else 0.0)}%)", sale.igstInr)
                            HorizontalDivider(color = InvoicePaperBorder)
                            TaxLine("TOTAL (${String.format(Locale.US, "%.2f", sale.gstRatePct)}%)", sale.totalGstInr, bold = true)
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TaxLine("Taxable Value", sale.taxableValueInr)
                        TaxLine("Total GST", sale.totalGstInr)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(InvoiceNavy)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("INVOICE TOTAL", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Rs. ${String.format(Locale.US, "%.2f", sale.totalAmountInr)}",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(InvoiceLightBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("BALANCE DUE", color = InvoiceNavy, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Rs. ${String.format(Locale.US, "%.2f", sale.balanceDueInr)}",
                                color = InvoiceNavy,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Amount in words & Terms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, InvoicePaperBorder, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(InvoiceNavy)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("AMOUNT IN WORDS", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${ErpPdfGenerator.numberToIndianWords(sale.totalAmountInr.roundToLong())} Rupees Only",
                            color = InvoiceInk,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, InvoicePaperBorder, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(InvoiceNavy)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("TERMS & CONDITIONS", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "1. Goods once sold will not be taken back.\n2. Interest @ 18% p.a. on delayed payments.\n3. Subject to Kasganj jurisdiction only.",
                            color = InvoiceMutedInk,
                            fontSize = 6.8.sp,
                            lineHeight = 9.5.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                // Authorised Signatory Seal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "For KUMAR FOODS PROCESSING COMPANY",
                            color = InvoiceInk,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .border(1.5.dp, Color(0xFF1A46A0), CircleShape)
                                .padding(3.dp)
                                .border(0.8.dp, Color(0xFF1A46A0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "KUMAR\nFOODS",
                                color = Color(0xFF1A46A0),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                lineHeight = 8.sp
                            )
                        }
                        Text("Authorised Signatory", color = InvoiceMutedInk, fontSize = 7.sp)
                    }
                }

                // Footer strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InvoiceNavy)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BADAMPUR ROAD MOHANPURA DISTRICT KASGANJ",
                        color = Color.White,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mob. 9758435000",
                        color = Color.White,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sticky Bottom Action Bar: SAVE / SHARE PDF & PRINT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        val pdfFile = ErpPdfGenerator.generateTaxInvoicePdf(context, sale)
                        ErpPdfGenerator.sharePdfFile(context, pdfFile, "Kumar Foods Invoice ${sale.invoiceNo}")
                    }.onFailure {
                        Toast.makeText(context, "PDF Share error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("invoice_save_share_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TelemetryCyan)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE / SHARE")
            }
            Button(
                onClick = {
                    runCatching {
                        val pdfFile = ErpPdfGenerator.generateTaxInvoicePdf(context, sale)
                        ErpPdfGenerator.printPdfFile(context, pdfFile, "KumarFoods_${sale.invoiceNo}")
                    }.onFailure {
                        Toast.makeText(context, "Print error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("invoice_print_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoltenGold,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PRINT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InvoiceMetaLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = InvoiceMutedInk, fontSize = 6.8.sp)
        Text(value, color = InvoiceInk, fontSize = 6.8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddressBox(
    header: String,
    partyName: String,
    address: String,
    gstin: String?,
    mobile: String,
    state: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.border(1.dp, InvoicePaperBorder, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(InvoiceNavy)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(header, color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            Text(partyName, color = InvoiceInk, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, color = InvoiceMutedInk, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (gstin != null) {
                Text("GSTIN: $gstin", color = InvoiceInk, fontSize = 6.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (mobile.isNotBlank()) {
                Text("Mobile: $mobile", color = InvoiceInk, fontSize = 6.8.sp)
            }
            Text("State: $state", color = InvoiceMutedInk, fontSize = 6.8.sp)
        }
    }
}

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, color: Color, bold: Boolean) {
    Text(
        text = text,
        color = color,
        fontSize = 7.5.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TaxLine(label: String, amount: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (bold) InvoiceNavy else InvoiceMutedInk, fontSize = 7.5.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text("Rs. ${String.format(Locale.US, "%.2f", amount)}", color = InvoiceInk, fontSize = 7.5.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

@Composable
private fun UpiQrCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pattern = arrayOf(
            "11110101111",
            "10010011001",
            "10110101011",
            "11110111111",
            "00001010000",
            "10110110101",
            "01001011010",
            "11110100110",
            "10010110101",
            "10110010011",
            "11110101101"
        )
        val cellW = size.width / pattern.size
        val cellH = size.height / pattern.size
        for (r in pattern.indices) {
            for (c in pattern[r].indices) {
                if (pattern[r][c] == '1') {
                    drawRect(
                        color = InvoiceNavy,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                }
            }
        }
    }
}
