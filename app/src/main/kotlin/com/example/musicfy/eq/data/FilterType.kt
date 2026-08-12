// filtertypekt
// this thing is for filter type

package com.example.musicfy.eq.data

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    // peaking filter boosts or cuts around a center frequency
    PK,
    // low shelf filter affects frequencies below the cutoff
    LSC,
    // high shelf filter affects frequencies above the cutoff
    HSC,
    // low pass filter attenuates frequencies above the cutoff
    LPQ,
    // high pass filter attenuates frequencies below the cutoff
    HPQ
}