package com.breuninger.entdecken.domain.model

import java.time.Instant
import java.util.Locale

data class Artikel(
    val id: ArtikelId,
    val ansichtenV2: Map<Int, String>,
    val beschreibungen: Map<Locale, Beschreibungen>,
    val farbe: Farbe?,
    val groesse: Groesse?,
    val startzeitpunktV2: Instant?,
    val endzeitpunktV2: Instant?,
    val neuBisDatum: Map<Land, Instant>,
    val firstSetActive: Map<Land, Instant>,
    val mengeV2: MengeV2?,
    val materialangaben: Materialangaben?,
    val attributeV2: List<AttributV2>,
    val vertriebsinfos: List<Vertriebsinfo>
)
