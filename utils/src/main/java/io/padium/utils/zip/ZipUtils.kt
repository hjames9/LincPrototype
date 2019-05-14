package io.padium.utils.zip

import io.padium.utils.Utils
import java.io.IOException

import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

object ZipUtils {
    private val zipFiles = Utils.lruCache<String, ZipFile>()

    @JvmStatic
    @Throws(ZipException::class, IOException::class)
    private fun getZipFile(zipFileName: String) : ZipFile {
        var zipFile = zipFiles[zipFileName]
        if(null == zipFile) {
            zipFile = ZipFile(zipFileName)
            zipFiles[zipFileName] = zipFile
        }
        return zipFile
    }

    @JvmStatic
    @Throws(ZipException::class, IOException::class)
    fun readZipFile(zipFileName: String) : Map<ZipEntry, InputStream> {
        val data = mutableMapOf<ZipEntry, InputStream>()
        val zipFile = getZipFile(zipFileName)

        val entries = zipFile.entries()
        while(entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val stream = zipFile.getInputStream(entry)
            data[entry] = stream
        }

        return data
    }

    @JvmStatic
    @Throws(ZipException::class, IOException::class)
    fun closeZipFile(zipFileName: String) {
        zipFiles[zipFileName]?.close()
        zipFiles.remove(zipFileName)
    }
}
