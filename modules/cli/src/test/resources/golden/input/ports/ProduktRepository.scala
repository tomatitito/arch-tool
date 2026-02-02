package com.breuninger.entdecken.domain.repository

import cats.data.NonEmptyList
import cats.effect.IO
import com.breuninger.entdecken.domain.model.{ProduktDocument, ProduktId}

trait ProduktRepository {
  def save(produktDocument: ProduktDocument): IO[Unit]
  def exists(id: ProduktId): IO[Boolean]
  def deleteByIds(produktIds: NonEmptyList[ProduktId]): IO[Unit]
}
