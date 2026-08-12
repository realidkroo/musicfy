// parametriceqparserkt
// this thing is for parametric eqparser

package com.example.musicfy.eq.data

import java.io.File

// parser for autoeq parametriceqtxt files these files contain parametric eq
object ParametricEQParser {

    // parse a parametriceq file
    fun parseFile(file: File): ParametricEQ {
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }

        return parseText(file.readText())
    }

    // parse a parametriceq file from a path string
    fun parseFile(filePath: String): ParametricEQ {
        return parseFile(File(filePath))
    }

    // parse parametriceq text content
    fun parseText(content: String): ParametricEQ {
        val lines = content.lines()
        var preamp = 0.0
        val bands = mutableListOf<ParametricEQBand>()
        val metadata = mutableMapOf<String, String>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            when {
                // parse preamp line: "preamp: -52 db"
                trimmedLine.startsWith("Preamp:", ignoreCase = true) -> {
                    preamp = parsePreamp(trimmedLine)
                }

                // parse filter line: "filter 1: on lsc fc 105 hz gain 88 db q 070"
                trimmedLine.startsWith("Filter", ignoreCase = true) -> {
                    val band = parseFilterLine(trimmedLine)
                    if (band != null) {
                        bands.add(band)
                    }
                }

                // store other lines as metadata
                else -> {
                    val parts = trimmedLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        metadata[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        }

        return ParametricEQ(
            preamp = preamp,
            bands = bands,
            metadata = metadata
        )
    }

    // parse the preamp line example: "preamp: -52 db"
    private fun parsePreamp(line: String): Double {
        val regex = Regex("""Preamp:\s*([-+]?\d+\.?\d*)\s*dB""", RegexOption.IGNORE_CASE)
        val match = regex.find(line)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    // parse a filter line example: "filter 1: on lsc fc 105 hz gain 88 db q 070"
    private fun parseFilterLine(line: String): ParametricEQBand? {
        try {
            // check if filter is on
            if (!line.contains("ON", ignoreCase = true)) {
                return null
            }

            // extract filter type (lsc hsc pk lpq hpq)
            val filterType = parseFilterType(line) ?: return null

            // extract frequency: "fc 105 hz"
            val frequency = parseValue(line, "Fc", "Hz") ?: return null

            // extract gain: "gain 88 db"
            val gain = parseValue(line, "Gain", "dB") ?: return null

            // extract q factor: "q 070"
            val q = parseValue(line, "Q", null) ?: return null

            return ParametricEQBand(
                filterType = filterType,
                frequency = frequency,
                gain = gain,
                q = q
            )
        } catch (e: Exception) {
            println("Warning: Failed to parse filter line: $line")
            println("Error: ${e.message}")
            return null
        }
    }

    // parse filter type from line
    private fun parseFilterType(line: String): FilterType? {
        return when {
            line.contains("LSC", ignoreCase = true) -> FilterType.LSC
            line.contains("HSC", ignoreCase = true) -> FilterType.HSC
            line.contains("PK", ignoreCase = true) -> FilterType.PK
            line.contains("LPQ", ignoreCase = true) -> FilterType.LPQ
            line.contains("HPQ", ignoreCase = true) -> FilterType.HPQ
            else -> null
        }
    }

    // parse a numeric value from the line example: parsevalue(" fc 105 hz " "fc"
    private fun parseValue(line: String, keyword: String, unit: String?): Double? {
        val unitPattern = if (unit != null) "\\s*$unit" else ""
        val regex = Regex("""$keyword\s+([-+]?\d+\.?\d*)$unitPattern""", RegexOption.IGNORE_CASE)
        val match = regex.find(line)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // convert parametriceq to a human-readable string
    fun toString(eq: ParametricEQ): String {
        val sb = StringBuilder()
        sb.appendLine("Preamp: ${eq.preamp} dB")
        eq.bands.forEachIndexed { index, band ->
            sb.appendLine(
                "Filter ${index + 1}: ${band.filterType} Fc ${band.frequency} Hz " +
                        "Gain ${band.gain} dB Q ${band.q}"
            )
        }
        return sb.toString()
    }

    // format parametriceq for export to file
    fun toFileFormat(eq: ParametricEQ): String {
        val sb = StringBuilder()
        sb.appendLine("Preamp: ${eq.preamp} dB")
        eq.bands.forEachIndexed { index, band ->
            sb.appendLine(
                "Filter ${index + 1}: ON ${band.filterType} " +
                        "Fc ${band.frequency.toInt()} Hz " +
                        "Gain ${band.gain} dB " +
                        "Q ${String.format("%.2f", band.q)}"
            )
        }
        return sb.toString()
    }

    // validate a parametriceq profile returns a list of validation error messages
    fun validate(eq: ParametricEQ): List<String> {
        val errors = mutableListOf<String>()

        // validate preamp
        if (eq.preamp < -50.0 || eq.preamp > 50.0) {
            errors.add("Preamp value ${eq.preamp} dB is out of range (-50 to +50 dB)")
        }

        // validate bands exist
        if (eq.bands.isEmpty()) {
            errors.add("EQ profile must have at least one band")
        }

        // validate number of bands
        if (eq.bands.size > ParametricEQ.MAX_BANDS) {
            errors.add("EQ profile has ${eq.bands.size} bands, maximum is ${ParametricEQ.MAX_BANDS}")
        }

        // validate each band
        eq.bands.forEachIndexed { index, band ->
            // validate frequency
            if (band.frequency <= 0.0 || band.frequency > 100000.0) {
                errors.add("Band ${index + 1}: Frequency ${band.frequency} Hz is out of range (1 to 100000 Hz)")
            }

            // validate gain
            if (band.gain < -30.0 || band.gain > 30.0) {
                errors.add("Band ${index + 1}: Gain ${band.gain} dB is out of range (-30 to +30 dB)")
            }

            // validate q factor
            if (band.q <= 0.0 || band.q > 20.0) {
                errors.add("Band ${index + 1}: Q factor ${band.q} is out of range (0.01 to 20)")
            }
        }

        return errors
    }
}