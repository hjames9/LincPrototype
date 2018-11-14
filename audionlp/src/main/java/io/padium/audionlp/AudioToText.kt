package io.padium.audionlp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.util.Log
import io.padium.audionlp.impl.AudioParameters
import java.util.concurrent.TimeUnit
import io.padium.audionlp.impl.PocketSphinxAudioToTextImpl
import io.padium.audionlp.impl.WitAudioToTextImpl
import io.padium.audionlp.wav.WavFile
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class AudioProcessorLocation {
    LOCAL,
    CLOUD,
    ANY
}

class AudioToText(context: Context, val listener: AudioToTextListener): Closeable {
    companion object {
        private val TAG = AudioToText::class.java.simpleName
    }

    private val recording = AtomicBoolean(false)
    private val pocketSphinx = PocketSphinxAudioToTextImpl(context, recording)
    private val wit = WitAudioToTextImpl(context, recording)
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val executor = Executors.newFixedThreadPool(2)

    override fun close() {
        wit.close()
        pocketSphinx.close()
    }

    @Throws(AudioException::class)
    fun startMicrophoneText(processorLocation: AudioProcessorLocation = AudioProcessorLocation.ANY): Long {
        recording.set(true)
        return processAudioRecording(processorLocation)
    }

    @Throws(AudioException::class)
    fun stopMicrophoneText() {
        recording.set(false)
    }

    @Throws(AudioException::class)
    fun getMicrophoneTextTimed(listenTime: Long, timeUnit: TimeUnit,
                               processorLocation: AudioProcessorLocation = AudioProcessorLocation.ANY): Long {
        recording.set(true)
        scheduledExecutor.schedule({recording.set(false)}, listenTime, timeUnit)
        return processAudioRecording(processorLocation)
    }

    @Throws(AudioException::class)
    fun getWavFileText(file : File, processorLocation: AudioProcessorLocation = AudioProcessorLocation.ANY) {
        val audioQueues = mutableListOf<LinkedBlockingQueue<Pair<ShortArray, Int>>>()
        val parameters = AudioParameters(processorLocation)
        val wavFile = WavFile(file)

        parameters.endian = ByteOrder.LITTLE_ENDIAN
        parameters.sampleRate = wavFile.sampleRate
        parameters.encodingSize = when(wavFile.encodingSize.toInt()) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            else -> AudioFormat.ENCODING_DEFAULT
        }
        parameters.channel = when(wavFile.isMono) {
            true -> AudioFormat.CHANNEL_IN_MONO
            false -> AudioFormat.CHANNEL_IN_STEREO
        }

        if(processorLocation == AudioProcessorLocation.LOCAL || processorLocation == AudioProcessorLocation.ANY) {
            audioQueues.add(pocketSphinx.audioQueue)
            executor.submit {pocketSphinx.process(listener, parameters)}
        }

        if(processorLocation == AudioProcessorLocation.CLOUD || processorLocation == AudioProcessorLocation.ANY) {
            audioQueues.add(wit.audioQueue)
            executor.submit {wit.process(listener, parameters)}
        }

        Log.i(TAG, "Processing wav file $wavFile")
        Log.i(TAG, "Processing parameters $parameters")

        //Get short array
        val buffer = ShortArray(wavFile.dataSize / 2)
        ByteBuffer.wrap(wavFile.data).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(buffer)

        audioQueues.forEach { audioQueue ->
            audioQueue.put(Pair(buffer, buffer.size))
        }
    }

    @Throws(AudioException::class)
    private fun processAudioRecording(processorLocation: AudioProcessorLocation = AudioProcessorLocation.ANY): Long {
        val audioQueues = mutableListOf<LinkedBlockingQueue<Pair<ShortArray, Int>>>()
        val parameters = AudioParameters(processorLocation)

        if(processorLocation == AudioProcessorLocation.LOCAL || processorLocation == AudioProcessorLocation.ANY) {
            audioQueues.add(pocketSphinx.audioQueue)
            executor.submit {pocketSphinx.process(listener, parameters)}
        }

        if(processorLocation == AudioProcessorLocation.CLOUD || processorLocation == AudioProcessorLocation.ANY) {
            audioQueues.add(wit.audioQueue)
            executor.submit {wit.process(listener, parameters)}
        }

        val bufferSize = AudioRecord.getMinBufferSize(parameters.sampleRate, parameters.channel, parameters.encodingSize)
        val buffer = ShortArray(bufferSize / 4)

        val audioRecord = AudioRecord(AudioSource.VOICE_RECOGNITION, parameters.sampleRate, parameters.channel,
                parameters.encodingSize, bufferSize)

        val startTime = System.currentTimeMillis()
        try {
            audioRecord.startRecording()
            while (recording.get()) {
                val result = audioRecord.read(buffer, 0, bufferSize / 4, AudioRecord.READ_BLOCKING)
                var brokenQueue = 0
                when (result) {
                    AudioRecord.ERROR -> throw AudioException("AudioRecord reading error")
                    AudioRecord.ERROR_BAD_VALUE -> throw AudioException("AudioRecord invalid parameters")
                    AudioRecord.ERROR_DEAD_OBJECT -> throw AudioException("AudioRecord not valid anymore")
                    AudioRecord.ERROR_INVALID_OPERATION -> throw AudioException("AudioRecord isn't properly initialized")
                    else -> {
                        audioQueues.forEach { audioQueue ->
                            if (audioQueue.remainingCapacity() - 1 <= 0) {
                                brokenQueue++
                            } else {
                                audioQueue.put(Pair(buffer, result))
                            }
                        }
                    }
                }

                if (brokenQueue >= audioQueues.size) {
                    Log.e(TAG, "Queue capacities have been reached")
                    break
                }
            }
        } finally {
            if(AudioRecord.RECORDSTATE_RECORDING == audioRecord.recordingState) {
                audioRecord.stop()
            }
            audioRecord.release()
            return System.currentTimeMillis() - startTime
        }
    }
}