package com.breuninger.reco.richrelevanceprodukte.ports.kafka

import arrow.core.Either
import com.breuninger.reco.richrelevanceprodukte.domain.model.import.produkt.ProduktId
import com.breuninger.reco.richrelevanceprodukte.ports.kafka.dto.ProduktGoldenRecordDto
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@JvmInline
value class Delete<T>(
    val value: T,
)

@JvmInline
value class Write<T>(
    val value: T,
)

@Component
class GoldenRecordConsumerRecordConverter(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun convert(record: ConsumerRecord<String, ByteArray>): Either<Delete<ProduktId>, Write<ProduktGoldenRecordDto>>? {
        when (record.getMethod()) {
            FeedMethodHeader.PUT.name -> {
                val dto = parseJson(record.value())
                return dto?.let { Either.Right(Write(it)) }
            }

            FeedMethodHeader.DELETE.name -> {
                val id = ProduktId(record.key())
                return Either.Left(Delete(id))
            }

            else -> {
                logger.error(
                    "Can't parse ProduktGoldenRecordDto message with key ${record.key()}, the HTTP-Method header is missing or unknown: ${record.getMethod()}",
                )
                return null
            }
        }
    }

    private fun ConsumerRecord<String, ByteArray>.getMethod(): String? =
        headers().lastHeader(FeedMethodHeader.METHOD_HEADER_KEY)?.value()?.decodeToString()

    private fun parseJson(json: ByteArray): ProduktGoldenRecordDto? {
        try {
            return objectMapper.readValue(json, ProduktGoldenRecordDto::class.java)
        } catch (e: JacksonException) {
            logger.error("Failed to parse golden record json", e)
            return null
        }
    }
}
