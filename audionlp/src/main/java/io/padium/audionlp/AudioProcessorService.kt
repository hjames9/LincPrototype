package io.padium.audionlp

enum class AudioProcessorLocation {
    LOCAL,
    CLOUD
}

enum class AudioProcessorService(val processorLocation: AudioProcessorLocation,
                                 val handleRecording: Boolean) {
    WIT(AudioProcessorLocation.CLOUD, false),
    POCKET_SPHINX(AudioProcessorLocation.LOCAL, false),
    KEEN_ASR(AudioProcessorLocation.LOCAL, true),
    GOOGLE_LOCAL(AudioProcessorLocation.LOCAL, true),
    GOOGLE_CLOUD(AudioProcessorLocation.CLOUD, true);

    fun isCloud(): Boolean {
        return when(processorLocation) {
            AudioProcessorLocation.CLOUD -> true
            AudioProcessorLocation.LOCAL -> false
        }
    }

    fun isLocal(): Boolean {
        return when(processorLocation) {
            AudioProcessorLocation.CLOUD -> false
            AudioProcessorLocation.LOCAL -> true
        }
    }
}