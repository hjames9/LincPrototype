package io.padium.utils.zip

import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.io.File
import java.io.FileOutputStream

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipUtilsTest {
    companion object {
        private const val TEST_ZIP_FILENAME = "test.zip"
        private const val TEST_TEXT_FILENAME = "text.txt"
        private const val TEST_PNG_FILENAME = "test.png"
    }

    private var zipFile : ZipFile? = null

    @Before
    fun setUp() {
        val file = File(TEST_ZIP_FILENAME)
        val stream = ZipOutputStream(FileOutputStream(file))

        val textEntry = ZipEntry(TEST_TEXT_FILENAME)
        stream.putNextEntry(textEntry)
        val sb = StringBuilder("Test String")
        val textData = sb.toString().toByteArray()
        stream.write(textData, 0, textData.size)
        stream.closeEntry()

        val pngEntry = ZipEntry(TEST_PNG_FILENAME)
        val pngData = byteArrayOf(0x89.toByte(), 0x50, 0x4e,
                0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        stream.putNextEntry(pngEntry)
        stream.write(pngData)
        stream.closeEntry()
        stream.close()

        zipFile = ZipFile(file)
    }

    @After
    fun tearDown() {
        zipFile?.close()
        if(null != zipFile) {
            File(zipFile?.name).delete()
        }
    }

    @Test
    fun testZipFile() {
        val result = ZipUtils.readZipFile(TEST_ZIP_FILENAME)
        assertEquals(2, result.size)
        result.forEach {
            val entry = it.key
            val stream = it.value

            when(entry.name) {
                TEST_TEXT_FILENAME -> {
                    assertEquals(TEST_TEXT_FILENAME, entry.name)
                    assertFalse(entry.isDirectory)
                    assertEquals(11, entry.size)
                    assertEquals(13, entry.compressedSize)
                    val buffer = ByteArray(entry.size.toInt())
                    assertEquals(11, stream.read(buffer))
                }
                TEST_PNG_FILENAME -> {
                    assertEquals(TEST_PNG_FILENAME, entry.name)
                    assertFalse(entry.isDirectory)
                    assertEquals(8, entry.size)
                    assertEquals(10, entry.compressedSize)
                    val buffer = ByteArray(entry.size.toInt())
                    assertEquals(8, stream.read(buffer))

                }
                else -> fail("Unknown file found in zip")
            }
        }

        ZipUtils.closeZipFile(TEST_ZIP_FILENAME)
    }
}
