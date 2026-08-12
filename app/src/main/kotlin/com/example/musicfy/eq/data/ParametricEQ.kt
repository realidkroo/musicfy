// parametriceqkt
// this thing is for parametric eq

package com.example.musicfy.eq.data

import kotlinx.serialization.Serializable

// represents a single parametric eq filter/band supports apo parametric eq filters
@Serializable
data class ParametricEQBand(
    val frequency: Double,                      // center frequency in hz
    val gain: Double,                           // gain in db
    val q: Double = 1.41,                       // q factor (bandwidth) - default to sqrt(2)
    val filterType: FilterType = FilterType.PK, // filter type
    val enabled: Boolean = true                 // whether this band is active
)

// represents a complete parametric eq configuration for a headphone parsed from
@Serializable
data class ParametricEQ(
    val preamp: Double,                         // preamp/gain in db (to prevent clipping)
    val bands: List<ParametricEQBand>,          // list of eq bands
    val metadata: Map<String, String> = emptyMap()  // additional metadata from file
) {
    companion object {
        const val MAX_BANDS = 20  // maximum bands supported by the implementation
    }
}