package com.kosd.log_inattendancesafeguard.services

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.kosd.log_inattendancesafeguard.BuildConfig

/**
 * Generates QR code bitmaps using the ZXing core library.
 *
 * The QR encodes the full Edge Function URL for guest check-in:
 * `<SUPABASE_URL>/functions/v1/event-check-in?slug=<slug>&t=<token>`
 */
object QRService {

    private val SUPABASE_FUNCTIONS_BASE =
        "${BuildConfig.SUPABASE_URL}/functions/v1/event-check-in"

    /** Build the full check-in URL for a given event slug + token. */
    fun buildCheckInUrl(slug: String, token: String): String =
        "$SUPABASE_FUNCTIONS_BASE?slug=$slug&t=$token"

    /**
     * Generate a QR code bitmap for the given content string.
     * Returns null if encoding fails.
     */
    fun generateQRBitmap(content: String, size: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix: BitMatrix = MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
