package io.padium.audionlp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.util.Log
import io.padium.audionlp.impl.*
import java.util.concurrent.TimeUnit
import io.padium.audionlp.wav.WavFile
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class AudioToText(context: Context, val listener: AudioToTextListener): Closeable {
    companion object {
        private val TAG = AudioToText::class.java.simpleName
    }

    private val recording = AtomicBoolean(false)
    private val pocketSphinx = PocketSphinxAudioToTextImpl(context, recording)
    private val wit = WitAudioToTextImpl(context, recording)
    private val keenAsr = KeenASRAudioToTextImpl(context)
    private val googleLocal = GoogleAudioToTextImpl(context, AudioProcessorLocation.LOCAL)
    private val googleCloud = GoogleAudioToTextImpl(context, AudioProcessorLocation.CLOUD)
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val executor = Executors.newFixedThreadPool(2)

    override fun close() {
        wit.close()
        pocketSphinx.close()
        keenAsr.close()
        googleLocal.close()
        googleCloud.close()
    }

    @Throws(AudioException::class)
    fun startMicrophoneText(processorServices: Set<AudioProcessorService> = setOf(AudioProcessorService.GOOGLE_LOCAL)): Long {
        recording.set(true)
        return getMicrophoneText(processorServices)
    }

    @Throws(AudioException::class)
    fun stopMicrophoneText() {
        recording.set(false)
    }

    @Throws(AudioException::class)
    fun startMicrophoneTextTimed(listenTime: Long, timeUnit: TimeUnit,
                                 processorServices: Set<AudioProcessorService> = setOf(AudioProcessorService.GOOGLE_LOCAL)): Long {
        recording.set(true)
        scheduledExecutor.schedule({recording.set(false)}, listenTime, timeUnit)
        return getMicrophoneText(processorServices)
    }

    @Throws(AudioException::class)
    fun getWavFileText(file : File, processorServices: Set<AudioProcessorService>
            = setOf(AudioProcessorService.POCKET_SPHINX, AudioProcessorService.WIT)) {
        if(null != processorServices.find{it.handleRecording}) {
            throw AudioException("Only processor that doesn't handle its own recording may be used")
        } else if(processorServices.isEmpty()) {
            throw AudioException("Must specify at least one audio processor")
        }

        val audioQueues = mutableListOf<LinkedBlockingQueue<Pair<ShortArray, Int>>>()
        val wavFile = WavFile(file)

        processorServices.forEach { processorService ->
            val parameters = AudioParameters(processorService.processorLocation)
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

            when (processorService) {
                AudioProcessorService.POCKET_SPHINX -> {
                    Log.i(TAG, "Processing parameters $parameters")
                    audioQueues.add(pocketSphinx.audioQueue)
                    executor.submit{pocketSphinx.process(listener, parameters)}
                }
                AudioProcessorService.WIT -> {
                    Log.i(TAG, "Processing parameters $parameters")
                    audioQueues.add(wit.audioQueue)
                    executor.submit{wit.process(listener, parameters)}
                }
                else -> {
                    Log.e(TAG, "Ignored $processorService since it handles its own recording")
                    return
                }
            }
        }

        Log.i(TAG, "Processing wav file $wavFile")

        //Get short array
        val buffer = ShortArray(wavFile.dataSize / 2)
        ByteBuffer.wrap(wavFile.data).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(buffer)

        audioQueues.forEach { audioQueue ->
            audioQueue.put(Pair(buffer, buffer.size))
        }
    }

    @Throws(AudioException::class)
    private fun getMicrophoneText(processorServices: Set<AudioProcessorService>): Long {
        if(processorServices.size > 1 && null != processorServices.find{it.handleRecording}) {
            throw AudioException("Only one audio processor that handles its own recording may be used")
        } else if(processorServices.isEmpty()) {
            throw AudioException("Must specify at least one audio processor")
        }

        val localProcessorService = processorServices.find{it.isLocal()}
        val parameters = if(null != localProcessorService) {
            AudioParameters(localProcessorService.processorLocation)
        } else {
            AudioParameters(processorServices.iterator().next().processorLocation)
        }

        val audioQueues = mutableListOf<LinkedBlockingQueue<Pair<ShortArray, Int>>>()
        processorServices.forEach { processorService ->
            if (!processorService.handleRecording) {
                when(processorService) {
                    AudioProcessorService.POCKET_SPHINX -> {
                        audioQueues.add(pocketSphinx.audioQueue)
                        executor.submit {pocketSphinx.process(listener, parameters)}
                    }
                    AudioProcessorService.WIT -> {
                        audioQueues.add(wit.audioQueue)
                        executor.submit {wit.process(listener, parameters)}
                    }
                    else -> throw AudioException("Misconfigured audio processor")
                }
            } else {
                when (processorService) {
                    AudioProcessorService.GOOGLE_LOCAL -> googleLocal.process(listener, AudioParameters(processorService.processorLocation))
                    AudioProcessorService.GOOGLE_CLOUD -> googleCloud.process(listener, AudioParameters(processorService.processorLocation))
                    AudioProcessorService.KEEN_ASR -> keenAsr.process(listener, AudioParameters(processorService.processorLocation))
                    else -> throw AudioException("Misconfigured audio processor")
                }
                //Should technically only iterate once here, so we can exit...
                return -1 //Can implement timing later if it really matters...
            }
        }

        return processAudioRecording(audioQueues, parameters)
    }

    @Throws(AudioException::class)
    private fun processAudioRecording(audioQueues: List<LinkedBlockingQueue<Pair<ShortArray, Int>>>,
                                      parameters: AudioParameters): Long {
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
