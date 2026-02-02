package com.breuninger.entdecken.domain.model

import java.util.Locale

case class Groesse(
    id: String,
    dimsortierung: Map[Locale, Int],
    groessenanzeige: Map[Locale, String],
    groessenfilter: Map[Locale, String]
)
