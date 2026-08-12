// functionnameextractorkt
// what is this for you ask its for function name extractor ofc

package com.example.musicfy.utils.cipher

import timber.log.Timber

object FunctionNameExtractor {
    private const val TAG = "musicfy_CipherFnExtract"

    // modern 2025+ signature deobfuscation function patterns
    // the sig function is called as: func(number
    // within a logical expression: var && (var = func(num
    private val SIG_FUNCTION_PATTERNS = listOf(
        // pattern 1 (2025+): &&(var=func(numdecodeuricomponent(var))
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        // classic patterns (pre-2025 kept as fallback)
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9${'$'}]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\("""),
        Regex("""\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
    )

    // n-parameter (throttle) transform function patterns
    // the n-function transforms the 'n' parameter in streaming urls to avoid
    // pattern: get("n"))&&(b=func[index](a[0]))  or  get("n"))&&(b=func(a[0]))
    private val N_FUNCTION_PATTERNS = listOf(
        // pattern 1: get("n"))&&(b=func[idx](var)
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        // pattern 2: get("n"))&&(func=var[idx](func)  (2025+ variant)
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
        // pattern 3: stringfromcharcode(110) variant (110 = 'n')
        Regex("""\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)"""),
        // pattern 4: enhanced_except_ function pattern
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_"""),
    )

    data class SigFunctionInfo(
        val name: String,
        val constantArg: Int? // the first numeric argument (eg 8 in de(8 sig))
    )

    data class NFunctionInfo(
        val name: String,
        val arrayIndex: Int? // eg func[0] -> index=0
    )

    fun extractSigFunctionInfo(playerJs: String): SigFunctionInfo? {
        for ((index, pattern) in SIG_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val name = match.groupValues[1]
                val constArg = if (match.groupValues.size > 2) match.groupValues[2].toIntOrNull() else null
                Timber.tag(TAG).d("Sig function found with pattern $index: $name (constantArg=$constArg)")
                return SigFunctionInfo(name, constArg)
            }
        }
        Timber.tag(TAG).e("Could not find signature deobfuscation function name")
        return null
    }

    fun extractNFunctionInfo(playerJs: String): NFunctionInfo? {
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                when (index) {
                    0 -> {
                        // pattern 1: group1=funcname group2=arrayindex (optional)
                        val name = match.groupValues[1]
                        val arrayIdx = match.groupValues[2].toIntOrNull()
                        Timber.tag(TAG).d("N-function found with pattern $index: $name (arrayIndex=$arrayIdx)")
                        return NFunctionInfo(name, arrayIdx)
                    }
                    1 -> {
                        // pattern 2: group2=funcname group3=arrayindex (optional)
                        val name = match.groupValues[2]
                        val arrayIdx = match.groupValues[3].toIntOrNull()
                        Timber.tag(TAG).d("N-function found with pattern $index: $name (arrayIndex=$arrayIdx)")
                        return NFunctionInfo(name, arrayIdx)
                    }
                    else -> {
                        val name = match.groupValues[1]
                        Timber.tag(TAG).d("N-function found with pattern $index: $name")
                        return NFunctionInfo(name, null)
                    }
                }
            }
        }
        Timber.tag(TAG).e("Could not find n-transform function name")
        return null
    }
}
