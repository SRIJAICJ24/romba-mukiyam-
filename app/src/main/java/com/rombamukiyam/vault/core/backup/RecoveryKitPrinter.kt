package com.rombamukiyam.vault.core.backup

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream

/**
 * Prints the Emergency Recovery Kit through the Android print framework (PDF/printer chosen by the user).
 * The recovery key is included ONLY when the user explicitly asks for it; otherwise the line stays blank
 * to be filled in by hand. No file is written to app storage.
 */
class RecoveryKitPrinter(private val context: Context) {

    data class Kit(val ownerName: String?, val recoveryKeyLines: List<String>?, val appVersion: String, val dateText: String)

    fun print(kit: Kit) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print("Romba Mukiyam Recovery Kit", KitAdapter(kit), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    private class KitAdapter(private val kit: Kit) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (signal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder("RombaMukiyam_RecoveryKit.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, signal: CancellationSignal?, callback: WriteResultCallback) {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // A4 @ 72dpi
            val c = page.canvas
            val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val body = Paint().apply { textSize = 12f; isAntiAlias = true }
            val mono = Paint().apply { textSize = 14f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
            val small = Paint().apply { textSize = 9f; isAntiAlias = true }
            var y = 70f
            c.drawText("ROMBA MUKIYAM", 50f, y, title); y += 28f
            c.drawText("Emergency Recovery Kit", 50f, y, Paint(title).apply { textSize = 16f }); y += 36f
            c.drawText("Printed: ${kit.dateText}   App version: ${kit.appVersion}", 50f, y, small); y += 30f

            fun field(label: String, value: String?) {
                c.drawText(label, 50f, y, body); y += 22f
                if (value != null) { c.drawText(value, 60f, y, mono) } else { c.drawLine(60f, y, 540f, y, body) }
                y += 30f
            }
            field("Vault owner:", kit.ownerName)
            c.drawText("Recovery key:", 50f, y, body); y += 22f
            if (kit.recoveryKeyLines != null) {
                kit.recoveryKeyLines.forEach { line -> c.drawText(line, 60f, y, mono); y += 22f }
            } else {
                repeat(2) { c.drawLine(60f, y, 540f, y, body); y += 26f }
            }
            y += 10f
            field("Backup file location (USB / SD card / computer):", null)
            field("Date of last backup:", null)
            c.drawText("Master passphrase:", 50f, y, body); y += 20f
            c.drawText("DO NOT write it here unless you deliberately choose to. Anyone holding this page", 60f, y, small); y += 14f
            c.drawText("together with the passphrase can open every backup.", 60f, y, small); y += 34f

            c.drawText("How to recover", 50f, y, Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }); y += 20f
            listOf(
                "1. Forgot the master passphrase, phone still works: open the app, tap Recovery, type the recovery key,",
                "   choose a new master passphrase.",
                "2. Phone lost or broken: install Romba Mukiyam on a new phone, create a new vault, then Settings >",
                "   Backup & Recovery > Restore and choose the .rmvault file. Enter the master passphrase that was in use",
                "   when that backup was made.",
                "3. Nothing on this page and no passphrase: the vault cannot be opened by anyone. There is no back door.",
            ).forEach { c.drawText(it, 50f, y, small); y += 14f }
            y += 20f
            c.drawText("Keep this page somewhere physically secure (locker, safe, with important family papers).", 50f, y, small)

            doc.finishPage(page)
            try {
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(null)
            } finally {
                doc.close()
            }
        }
    }
}
