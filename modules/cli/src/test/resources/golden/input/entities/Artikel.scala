package com.breuninger.entdecken.domain.model

import java.time.Instant
import java.util.Locale

case class Artikel(
    id: ArtikelId,
    ansichtenV2: Map[Int, String],
    beschreibungen: Map[Locale, Beschreibungen],
    farbe: Option[Farbe],
    groesse: Option[Groesse],
    startzeitpunktV2: Option[Instant],
    endzeitpunktV2: Option[Instant],
    neuBisDatum: Map[Land, Instant],
    firstSetActive: Map[Land, Instant],
    mengeV2: Option[MengeV2],
    materialangaben: Option[Materialangaben],
    attributeV2: List[AttributV2],
    vertriebsinfos: List[Vertriebsinfo]
)
