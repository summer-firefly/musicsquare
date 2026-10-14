package com.musicsquare.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.asArrayOrNull(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> this["data"]?.asArrayOrNull()
    else -> null
}

fun JsonObject.str(vararg keys: String): String {
    for (key in keys) {
        val value = this[key] ?: continue
        when (value) {
            is JsonPrimitive -> {
                val text = value.contentOrNull ?: value.toString()
                if (text.isNotBlank() && text != "null") return text
            }
            else -> Unit
        }
    }
    return ""
}

fun JsonObject.strOrNull(vararg keys: String): String? =
    str(*keys).ifBlank { null }

fun JsonElement?.primitiveContentOrNull(): String? =
    this?.jsonPrimitive?.contentOrNull
