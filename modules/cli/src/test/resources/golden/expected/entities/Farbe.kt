package com.breuninger.entdecken.domain.model

import java.util.Locale

data class Farbe(
    val id: String,
    val farbbezeichnung: Map<Locale, String>,
    val rgbfilterfarbname: String?,
    val herstellerfarbe: String?
)
