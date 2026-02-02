package com.breuninger.entdecken.ports.kafka

import cats.effect.IO
import cats.syntax.either._
import com.breuninger.entdecken.domain.model.ProduktId
import com.breuninger.entdecken.ports.kafka.FeedMethodHeader.METHOD_HEADER_KEY
import com.breuninger.entdecken.ports.kafka.dto.ProduktGoldenRecordDto
import com.softwaremill.tagging.{@@, Tagger}
import fs2.kafka.ConsumerRecord
import io.circe.jawn.decodeByteArray
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ProduktGoldenRecordParser(implicit logger: Logger[IO] = Slf4jLogger.getLogger[IO]) {

  def parse(record: ConsumerRecord[String, Array[Byte]]): IO[Option[Either[ProduktId @@ Delete, ProduktGoldenRecordDto @@ Create]]] = {
    record.headers(METHOD_HEADER_KEY).flatMap(_.attemptAs[FeedMethodHeader].toOption) match {
      case Some(FeedMethodHeader.PUT)    => handlePut(record).map(_.map(a => Either.right(a)))
      case Some(FeedMethodHeader.DELETE) => IO.some(Either.left(handleDelete(record)))
      case None                          =>
        logger.error(s"Can't parse ProduktGoldenRecordDto message with key ${record.key}, the HTTP-Method header is missing").as(None)
    }
  }

  private def handleDelete(record: ConsumerRecord[String, Array[Byte]]): ProduktId @@ Delete =
    ProduktId.apply(record.key).taggedWith[Delete]

  private def handlePut(record: ConsumerRecord[String, Array[Byte]]): IO[Option[ProduktGoldenRecordDto @@ Create]] = {
    import com.breuninger.entdecken.ports.kafka.json.instances.ProduktGoldenRecordDtoJsonInstances._

    decodeByteArray[ProduktGoldenRecordDto](record.value) match {
      case Left(error) =>
        logger.error(s"Could not parse ${classOf[ProduktGoldenRecordDto].getSimpleName} to JSON, error: ${error.getMessage}").as(None)
      case Right(value) => IO.pure(Some(value.taggedWith[Create]))
    }
  }

}
