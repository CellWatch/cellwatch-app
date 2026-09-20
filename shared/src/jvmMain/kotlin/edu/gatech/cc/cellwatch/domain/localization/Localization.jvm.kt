package edu.gatech.cc.cellwatch.domain.localization

import java.util.Locale

actual fun currentLanguageCode(): String = Locale.getDefault().language
