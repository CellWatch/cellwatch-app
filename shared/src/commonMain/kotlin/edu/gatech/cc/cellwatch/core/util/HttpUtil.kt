//package edu.gatech.cc.cellwatch.core.util
//
//object HttpUtil {
//    private val codeRx = Regex("""code=(\d{3})""")
//    fun code(t: Throwable?, extra: String? = null): Int? {
//        val s = buildString {
//            append(t?.toString() ?: "")
//            if (!extra.isNullOrBlank()) append(' ').append(extra)
//        }
//        return codeRx.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
//    }
//}
