package com.breuninger.entdecken.ports.kafka

import cats.effect.{IO, Resource}
import cats.implicits.{toFoldableOps, toTraverseOps}
import com.breuninger.entdecken.domain.model.{ProduktDocument, ProduktId}
import com.breuninger.entdecken.ports.kafka.dto.ProduktGoldenRecordDto
import com.softwaremill.tagging.@@
import fs2.Chunk
import fs2.kafka.consumer.KafkaConsumeChunk.CommitNow
import fs2.kafka.{ConsumerRecord, KafkaConsumer}

class GoldenRecordToProduktService(
    goldenRecordParser: ProduktGoldenRecordParser,
    inactiveProduktFilter: InactiveProduktFilter,
    conversion: Conversion,
    persistence: Persistence,
    retryHelper: RetryHelper,
    goldenRecordConsumer: Resource[IO, KafkaConsumer[IO, String, Array[Byte]]]
) {
  def start(): IO[Unit] = {
    goldenRecordConsumer.use(consumer => retryHelper.runKafkaServiceAndAlwaysRestart("GoldenRecordToProdukt", consumer.consumeChunk(process)))
  }

  def process(chunk: Chunk[ConsumerRecord[String, Array[Byte]]]): IO[CommitNow] = for {
    parsed: Chunk[Either[ProduktId @@ Delete, ProduktGoldenRecordDto @@ Create]] <- chunk.traverseFilter(cr => goldenRecordParser.parse(cr))
    (produktIdsToDelete: Chunk[ProduktId @@ Delete], dtoToSave: Chunk[ProduktGoldenRecordDto @@ Create]) = parsed.partitionEither(identity)
    now <- IO.realTimeInstant
    domainModel: Chunk[ProduktDocument] = dtoToSave.map(dto => conversion.goldenRecordToProduktDocument(dto, now))
    filtered: Chunk[ProduktDocument] <- domainModel.traverseFilter((doc: ProduktDocument) => inactiveProduktFilter.filter(doc))
    _ <- filtered.traverse(doc => persistence.persist(doc))
    _ <- produktIdsToDelete.toNel.traverse(ids => persistence.deleteByIds(ids))
  } yield CommitNow
}
