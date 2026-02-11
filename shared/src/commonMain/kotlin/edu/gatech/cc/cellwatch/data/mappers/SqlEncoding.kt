package edu.gatech.cc.cellwatch.data.mappers

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val json = Json

fun Boolean?.toSqlBoolean(): Long? = when (this) {
    true -> 1L
    false -> 0L
    null -> null
}

fun Long?.toBooleanOrNull(): Boolean? = when (this) {
    1L -> true
    0L -> false
    else -> null
}

fun List<String>?.toSqlStringList(): String? = this?.let { json.encodeToString(it) }

fun String?.toStringListOrNull(): List<String>? = this?.let { json.decodeFromString<List<String>>(it) }
