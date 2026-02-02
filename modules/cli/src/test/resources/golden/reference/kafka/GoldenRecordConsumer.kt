package com.breuninger.reco.richrelevanceprodukte.ports.kafka

import arrow.core.separateEither
import arrow.core.toNonEmptyListOrNull
import com.breuninger.reco.richrelevanceprodukte.application.ProduktImportService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class GoldenRecordConsumer(
    private val produktImportService: ProduktImportService,
    private val goldenRecordDtoToProduktConverter: GoldenRecordDtoToProduktConverter,
    private val goldenRecordConsumerRecordConverter: GoldenRecordConsumerRecordConverter,
) {
    @KafkaListener(
        topics = ["${app.kafka.consumer-topic}"],
        concurrency = "${app.kafka.concurrency}",
        batch = "true",
    )
    suspend fun consume(records: List<ConsumerRecord<String, ByteArray>>) {
        val deduplicated = records.associateBy { it.key() }.values.toList()

        val (deletes, writes) =
            deduplicated
                .mapNotNull { record ->
                    goldenRecordConsumerRecordConverter.convert(record)
                }.separateEither()

        writes
            .toNonEmptyListOrNull()
            ?.map { goldenRecordDtoToProduktConverter.convert(it.value) }
            ?.let { produktImportService.import(it) }

        deletes
            .toNonEmptyListOrNull()
            ?.map { it.value }
            ?.let { produktImportService.deleteByProduktIds(it) }
    }
}
