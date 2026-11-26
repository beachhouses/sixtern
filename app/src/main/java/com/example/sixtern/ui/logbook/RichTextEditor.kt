package com.example.sixtern.ui.logbook

import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText

/**
 * Editor teks ala Word untuk kolom deskripsi logbook — tanpa library tambahan.
 * Tombol toolbar memformat teks TERPILIH secara langsung (Spannable), jadi
 * user langsung melihat hasil tebal/miring/warna di kolom input.
 * Simpan sebagai HTML agar tampil di daftar & review mentor.
 */
object RichTextEditor {

    // ---------- Warna teks ----------
    // Nilai ARGB mentah (tanpa Color.parseColor) agar object bisa dipakai
    // di unit test JVM yang tidak punya framework Android.

    val TEXT_COLORS = listOf(
        "Hitam" to 0xFF000000.toInt(),
        "Merah" to 0xFFC0392B.toInt(),
        "Biru" to 0xFF1D4ED8.toInt(),
        "Hijau" to 0xFF0B7A55.toInt(),
        "Ungu" to 0xFF6D28D9.toInt(),
        "Abu-abu" to 0xFF6B7280.toInt()
    )

    // ---------- Toggle format blok terpilih ----------

    /** Tebal: pasang span bila belum penuh, lepas bila seluruh rentang sudah tebal. */
    fun toggleBold(input: EditText) {
        val text = input.text ?: return
        val (s, e) = selection(input) ?: return
        if (s >= e) return
        val existing = text.getSpans(s, e, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
            .filter { text.getSpanStart(it) <= s && text.getSpanEnd(it) >= e }
        if (existing.isNotEmpty()) existing.forEach { text.removeSpan(it) }
        else text.setSpan(
            StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /** Miring: sama seperti tebal dengan style ITALIC. */
    fun toggleItalic(input: EditText) {
        val text = input.text ?: return
        val (s, e) = selection(input) ?: return
        if (s >= e) return
        val existing = text.getSpans(s, e, StyleSpan::class.java)
            .filter { it.style == Typeface.ITALIC }
            .filter { text.getSpanStart(it) <= s && text.getSpanEnd(it) >= e }
        if (existing.isNotEmpty()) existing.forEach { text.removeSpan(it) }
        else text.setSpan(
            StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /** Garis bawah: toggle UnderlineSpan pada rentang terpilih. */
    fun toggleUnderline(input: EditText) {
        val text = input.text ?: return
        val (s, e) = selection(input) ?: return
        if (s >= e) return
        val existing = text.getSpans(s, e, UnderlineSpan::class.java)
            .filter { text.getSpanStart(it) <= s && text.getSpanEnd(it) >= e }
        if (existing.isNotEmpty()) existing.forEach { text.removeSpan(it) }
        else text.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** Toggle bullet per baris yang tersentuh seleksi. */
    fun toggleBullets(input: EditText) {
        val text = input.text ?: return
        val (s, e) = selection(input) ?: return
        val lineStart = text.lineStartOf(s)
        val lineEnd = text.lineEndOf(e.coerceAtLeast(s))
        val existing = text.getSpans(lineStart, lineEnd, BulletSpan::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { text.removeSpan(it) }
        } else {
            var pos = lineStart
            while (pos <= lineEnd && pos <= text.length) {
                val ls = text.lineStartOf(pos)
                val le = text.lineEndOf(pos)
                if (le > ls) {
                    text.setSpan(BulletSpan(24), ls, le, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (le >= text.length) break
                pos = le + 1
                if (pos <= ls) break
            }
        }
    }

    private fun Editable.lineStartOf(offset: Int): Int {
        val o = offset.coerceIn(0, length)
        if (o == 0) return 0
        val idx = lastIndexOf('\n', o - 1)
        return if (idx == -1) 0 else idx + 1
    }

    private fun Editable.lineEndOf(offset: Int): Int {
        val o = offset.coerceIn(0, length)
        val idx = indexOf('\n', o)
        return if (idx == -1) length else idx
    }

    // ---------- Warna teks ----------

    /** Warnai teks terpilih; tanpa seleksi -> kembalikan hitam di posisi kursor. */
    fun applyColor(input: EditText, color: Int) {
        val text = input.text ?: return
        val (s, e) = selection(input) ?: return
        if (s >= e) {
            text.getSpans(s, s, ForegroundColorSpan::class.java)
                .forEach { text.removeSpan(it) }
            return
        }
        text.getSpans(s, e, ForegroundColorSpan::class.java)
            .filter { text.getSpanStart(it) < e && text.getSpanEnd(it) > s }
            .forEach { text.removeSpan(it) }
        text.setSpan(ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** Bersihkan SEMUA format di teks terpilih (atau seluruh teks bila tanpa seleksi). */
    fun clearFormatting(input: EditText) {
        val text = input.text ?: return
        val sel = selection(input) ?: return
        var from = sel.first.coerceIn(0, text.length)
        var to = sel.second.coerceIn(0, text.length)
        if (from >= to) {
            from = 0
            to = text.length
        }
        listOf(
            StyleSpan::class.java,
            UnderlineSpan::class.java,
            ForegroundColorSpan::class.java,
            BulletSpan::class.java
        ).forEach { cls ->
            text.getSpans(from, to, cls).forEach { text.removeSpan(it) }
        }
    }

    // ---------- HTML <-> Spannable ----------

    fun fromHtml(html: String): android.text.Spanned {
        if (html.isBlank()) return android.text.SpannedString("")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    fun toHtml(spanned: android.text.Spanned): String {
        if (spanned.isBlank()) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.toHtml(spanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        } else {
            @Suppress("DEPRECATION")
            Html.toHtml(spanned)
        }
    }

    /** True bila teks mengandung tag format — logika murni, bisa di-unit-test. */
    fun containsHtmlTag(raw: String): Boolean =
        "<(b|i|u|ul|li|p|br|font)".toRegex().containsMatchIn(raw)

    // ---------- Util ----------

    private fun selection(input: EditText): Pair<Int, Int>? {
        val len = input.text?.length ?: return null
        if (len == 0) return 0 to 0
        val start = input.selectionStart.coerceAtLeast(0).coerceAtMost(len)
        val end = input.selectionEnd.coerceAtLeast(0).coerceAtMost(len)
        return if (start <= end) start to end else end to start
    }
}
