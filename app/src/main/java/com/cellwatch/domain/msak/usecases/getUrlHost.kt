package com.cellwatch.domain.msak.usecases

fun getUrlHost(url: String): String {
    return Regex("^(http|ws)s?://([^/]+)/").find(url)?.groupValues?.get(2) ?: throw Throwable("no host found in url $url")
}