package edu.gatech.cc.cellwatch.domain.consent

import java.util.Locale

actual fun currentLanguageCode(): String = Locale.getDefault().language
