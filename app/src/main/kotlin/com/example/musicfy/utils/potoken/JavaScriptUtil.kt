// javascriptutilkt
// the file functioned as java script util

package com.example.musicfy.utils.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

// parses the raw challenge data obtained from the create endpoint and returns an
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = Json.parseToJsonElement(rawChallengeData).jsonArray

    val challengeData = if (scrambled.size > 1 && scrambled[1].jsonPrimitive.isString) {
        val descrambled = descramble(scrambled[1].jsonPrimitive.content)
        Json.parseToJsonElement(descrambled).jsonArray
    } else {
        scrambled[0].jsonArray
    }

    val messageId = challengeData[0].jsonPrimitive.content
    val interpreterHash = challengeData[3].jsonPrimitive.content
    val program = challengeData[4].jsonPrimitive.content
    val globalName = challengeData[5].jsonPrimitive.content
    val clientExperimentsStateBlob = challengeData[7].jsonPrimitive.content

    val privateDoNotAccessOrElseSafeScriptWrappedValue = challengeData[1]
        .takeIf { it !is JsonNull }
        ?.jsonArray
        ?.find { it.jsonPrimitive.isString }
    val privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = challengeData[2]
        .takeIf { it !is JsonNull }
        ?.jsonArray
        ?.find { it.jsonPrimitive.isString }

    return Json.encodeToString(
        JsonObject.serializer(), JsonObject(
            mapOf(
                "messageId" to JsonPrimitive(messageId),
                "interpreterJavascript" to JsonObject(
                    mapOf(
                        "privateDoNotAccessOrElseSafeScriptWrappedValue" to (privateDoNotAccessOrElseSafeScriptWrappedValue
                            ?: JsonNull),
                        "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to (privateDoNotAccessOrElseTrustedResourceUrlWrappedValue
                            ?: JsonNull)
                    )
                ),
                "interpreterHash" to JsonPrimitive(interpreterHash),
                "program" to JsonPrimitive(program),
                "globalName" to JsonPrimitive(globalName),
                "clientExperimentsStateBlob" to JsonPrimitive(clientExperimentsStateBlob)
            )
        )
    )
}

// parses the raw integrity token data obtained from the generateit endpoint to a
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = Json.parseToJsonElement(rawIntegrityTokenData).jsonArray
    return base64ToU8(integrityTokenData[0].jsonPrimitive.content) to integrityTokenData[1].jsonPrimitive.long
}

// converts a string usually the identifier used as input to obtainpotoken to
fun stringToU8(identifier: String): String {
    return newUint8Array(identifier.toByteArray())
}

// takes a potoken encoded as a sequence of bytes represented as integers
fun u8ToBase64(poToken: String): String {
    return poToken.split(",")
        .map { it.toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")
}

// takes the scrambled challenge decodes it from base64 adds 97 to each byte
private fun descramble(scrambledChallenge: String): String {
    return base64ToByteString(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()
}

// decodes a base64 string encoded in the specific base64 representation used by
private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteString(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"
}

// decodes a base64 string encoded in the specific base64 representation used by
private fun base64ToByteString(base64: String): ByteArray {
    val base64Mod = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')

    return (base64Mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode"))
        .toByteArray()
}
