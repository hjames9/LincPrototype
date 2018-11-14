package io.padium.audionlp.wav

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFile(file: File) {
    companion object {
        private const val HEADER_SIZE = 44
    }

    val sampleRate: Int
    val encodingSize: Short
    val isMono: Boolean
    val dataSize: Int
    var data: ByteArray

    init {
        var stream = FileInputStream(file)
        val buffer = ByteBuffer.allocate(HEADER_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        stream.read(buffer.array(), buffer.arrayOffset(), buffer.capacity())

        buffer.rewind()
        buffer.position(buffer.position() + 20)

        val format = buffer.short
        if(format.toInt() != 1) {
            throw WavFileException("Unsupported format: $format")
        }

        val channel = buffer.short
        if(!IntRange(1, 2).contains(channel)) {
            throw WavFileException("Unsupported channel: $channel")
        }
        isMono = when(channel.toInt()) { 1 -> true; else -> false }

        sampleRate = buffer.int
        if(!IntRange(8000, 48000).contains(sampleRate)) {
            throw WavFileException("Unsupported sample rate: $sampleRate")
        }

        buffer.position(buffer.position() + 6)

        encodingSize = buffer.short
        val supportedEncodingSizes = shortArrayOf(8, 16)
        if(encodingSize !in supportedEncodingSizes) {
            throw WavFileException("Unsupported encoding size: $encodingSize")
        }

        while (buffer.int != 0x61746164) { // "data" marker
            val size = buffer.int
            stream.skip(size.toLong())

            buffer.rewind()
            stream.read(buffer.array(), buffer.arrayOffset(), 8)
            buffer.rewind()
        }

        val chunkSize = buffer.int
        if(chunkSize <= 0) {
            throw WavFileException("Wrong chunk size: $chunkSize")
        }

        stream.close()

        //Lets use the entire file size beyond the header
        dataSize = file.length().toInt() - HEADER_SIZE
        data = ByteArray(dataSize)

        stream = FileInputStream(file)
        stream.skip(HEADER_SIZE.toLong())
        stream.read(data)
        stream.close()
    }

    override fun toString(): String {
        return "sampleRate[$sampleRate] encodingSize[$encodingSize] isMono[$isMono] dataSize[$dataSize]"
    }
}