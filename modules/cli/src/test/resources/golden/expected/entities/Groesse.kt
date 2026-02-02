package com.breuninger.entdecken.domain.model

import java.util.Locale

data class Groesse(
    val id: String,
    val dimsortierung: Map<Locale, Int>,
    val groessenanzeige: Map<Locale, String>,
    val groessenfilter: Map<Locale, String>
)
