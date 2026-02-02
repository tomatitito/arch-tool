package com.breuninger.entdecken.domain.model

import java.util.Locale

case class Farbe(
    id: String,
    farbbezeichnung: Map[Locale, String],
    rgbfilterfarbname: Option[String],
    herstellerfarbe: Option[String]
)
