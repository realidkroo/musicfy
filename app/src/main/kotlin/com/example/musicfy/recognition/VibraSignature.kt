// vibrasignaturekt
// what is this for you ask its for vibra signature ofc

package com.example.musicfy.recognition

// native library interface for generating shazam-compatible audio fingerprints
object VibraSignature {

    const val REQUIRED_SAMPLE_RATE = 16_000

    // generates a shazam signature from pcm audio data
    @JvmStatic
    fun fromI16(samples: ByteArray): String = ShazamSignatureGenerator.fromI16(samples)
}
