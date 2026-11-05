package edu.gatech.cc.cellwatch

import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * The 64-bit native libraries in the installed app, read from its APK.
 *
 * From 2027-02-01 Google Play rejects app updates whose 64-bit libraries don't support 16 KB memory
 * pages: every LOAD segment has to be aligned to a multiple of 16 KB, and a library stored
 * uncompressed has to start on a 16 KB boundary in the APK.
 */
object NativeLibraries {
    private const val PAGE = 16 * 1024L
    private const val PT_LOAD = 1
    private val LIBRARY_64_BIT = Regex("lib/(arm64-v8a|x86_64)/[^/]+\\.so")

    class Library(
        /** Where it is in the APK, such as lib/arm64-v8a/libh3-java.so. */
        val path: String,
        /** The alignment of each of its LOAD segments. */
        val loadAlignments: List<Long>,
        /** Where its bytes start in the APK, or null when it's compressed. */
        val storedAt: Long?,
    ) {
        val name get() = path.substringAfterLast('/')

        val supports16KbPages get() =
            loadAlignments.isNotEmpty() && loadAlignments.all { it % PAGE == 0L } && (storedAt ?: 0) % PAGE == 0L

        override fun toString() = "$path (LOAD alignments $loadAlignments, stored at $storedAt)"
    }

    fun inInstalledApp(): List<Library> {
        val apk = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.sourceDir
        val storedAt = storedEntryOffsets(apk)
        return ZipFile(apk).use { zip ->
            zip.entries().toList().filter { LIBRARY_64_BIT.matches(it.name) }.map { entry ->
                val alignments = zip.getInputStream(entry).use { loadAlignments(DataInputStream(it)) }
                Library(entry.name, alignments, storedAt[entry.name])
            }
        }
    }

    /** The p_align of each PT_LOAD program header in a little-endian ELF64 file. */
    private fun loadAlignments(elf: DataInputStream): List<Long> {
        val header = ByteArray(64).also { elf.readFully(it) }
        val fields = littleEndian(header)
        check(fields.getInt(0) == 0x464c457f && header[4] == 2.toByte() && header[5] == 1.toByte()) {
            "not a little-endian ELF64 file"
        }
        val tableAt = fields.getLong(0x20)
        val entrySize = fields.getShort(0x36).toInt() and 0xFFFF
        val entries = fields.getShort(0x38).toInt() and 0xFFFF
        elf.readFully(ByteArray((tableAt - header.size).toInt()))
        val table = littleEndian(ByteArray(entrySize * entries).also { elf.readFully(it) })
        return (0 until entries).map { it * entrySize }
            .filter { table.getInt(it) == PT_LOAD }
            .map { table.getLong(it + 0x30) }
    }

    /** Where the data of each uncompressed entry starts in the zip file at [path]. */
    private fun storedEntryOffsets(path: String): Map<String, Long> = RandomAccessFile(path, "r").use { zip ->
        // The end of central directory record closes the file, before a comment of up to 64 KB.
        val tail = ByteArray(minOf(zip.length(), 22L + 0xFFFF).toInt())
        zip.seek(zip.length() - tail.size)
        zip.readFully(tail)
        val end = littleEndian(tail)
        val endAt = (tail.size - 22 downTo 0).first { end.getInt(it) == 0x06054b50 }
        val directory = littleEndian(ByteArray(end.getInt(endAt + 12)).also {
            zip.seek(end.getInt(endAt + 16).toLong() and 0xFFFFFFFFL)
            zip.readFully(it)
        })

        val offsets = mutableMapOf<String, Long>()
        var at = 0
        repeat(end.getShort(endAt + 10).toInt() and 0xFFFF) {
            val nameLength = directory.getShort(at + 28).toInt() and 0xFFFF
            val name = String(directory.array(), at + 46, nameLength, Charsets.UTF_8)
            if (directory.getShort(at + 10).toInt() == 0) {
                val localAt = directory.getInt(at + 42).toLong() and 0xFFFFFFFFL
                val local = littleEndian(ByteArray(30).also { zip.seek(localAt); zip.readFully(it) })
                offsets[name] = localAt + 30 + (local.getShort(26).toInt() and 0xFFFF) + (local.getShort(28).toInt() and 0xFFFF)
            }
            at += 46 + nameLength + (directory.getShort(at + 30).toInt() and 0xFFFF) + (directory.getShort(at + 32).toInt() and 0xFFFF)
        }
        offsets
    }

    private fun littleEndian(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
}
