package com.example.usagetracker.importer

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Opens one entry of an in-memory zip via its central directory. Unlike ZipInputStream, it never
 * inflates other entries, so a zip bomb elsewhere in the file costs nothing. Inflation is capped.
 */
internal object ZipEntryReader {
    private const val EOCD_SIG = 0x06054b50
    private const val CEN_SIG = 0x02014b50
    private const val LOC_SIG = 0x04034b50

    /** Below this many inflated bytes the ratio isn't checked, so tiny entries never trip it. */
    private const val RATIO_GRACE_BYTES = 1L * 1024 * 1024

    class ZipLimitException(message: String) : IOException(message)

    /** @return the inflated entry stream, or null if [name] isn't in the zip. @throws IOException if malformed. */
    fun open(zip: ByteArray, name: String, maxBytes: Long, maxRatio: Int): InputStream? {
        val eocd = findEocd(zip) ?: throw IOException("Not a zip file")
        val entries = u16(zip, eocd + 10)
        var p = u32(zip, eocd + 16).toInt()
        repeat(entries) {
            check(zip, p, 46)
            if (s32(zip, p) != CEN_SIG) throw IOException("Bad central directory")
            val flags = u16(zip, p + 8)
            val method = u16(zip, p + 10)
            val compressed = u32(zip, p + 20)
            val declaredSize = u32(zip, p + 24)
            val nameLen = u16(zip, p + 28)
            val extraLen = u16(zip, p + 30)
            val commentLen = u16(zip, p + 32)
            val localOffset = u32(zip, p + 42).toInt()
            check(zip, p + 46, nameLen)
            val entryName = String(zip, p + 46, nameLen, Charsets.UTF_8)
            if (entryName == name) {
                if (flags and 1 != 0) throw IOException("Encrypted entry")
                if (declaredSize > maxBytes || (compressed > 0 && declaredSize / compressed > maxRatio)) {
                    throw ZipLimitException("Entry too large")
                }
                check(zip, localOffset, 30)
                if (s32(zip, localOffset) != LOC_SIG) throw IOException("Bad local header")
                val dataStart = localOffset + 30 + u16(zip, localOffset + 26) + u16(zip, localOffset + 28)
                check(zip, dataStart, compressed.toInt())
                val raw = ByteArrayInputStream(zip, dataStart, compressed.toInt())
                val data = when (method) {
                    0 -> raw
                    8 -> InflaterInputStream(raw, Inflater(true), 8192)
                    else -> throw IOException("Unsupported compression")
                }
                return LimitedStream(data, maxBytes, maxRatio.toLong() * maxOf(compressed, 1))
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return null
    }

    private fun findEocd(zip: ByteArray): Int? {
        val last = zip.size - 22
        val first = maxOf(0, last - 65535)
        for (i in last downTo first) if (s32(zip, i) == EOCD_SIG) return i
        return null
    }

    private fun check(zip: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset.toLong() + length > zip.size) throw IOException("Truncated zip")
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    private fun s32(b: ByteArray, i: Int) = u16(b, i) or (u16(b, i + 2) shl 16)
    private fun u32(b: ByteArray, i: Int) = s32(b, i).toLong() and 0xffffffffL

    /** Fails once more than [maxBytes] are read, or more than [ratioLimit] past the grace size. */
    private class LimitedStream(input: InputStream, private val maxBytes: Long, private val ratioLimit: Long) :
        FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) add(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) add(n.toLong())
            return n
        }

        private fun add(n: Long) {
            count += n
            if (count > maxBytes) throw ZipLimitException("Uncompressed size limit exceeded")
            if (count > RATIO_GRACE_BYTES && count > ratioLimit) throw ZipLimitException("Suspicious compression ratio")
        }
    }
}
