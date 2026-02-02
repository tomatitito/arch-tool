package com.breuninger.entdecken.ports.kafka

import cats.effect.{IO, Resource}
import com.breuninger.entdecken.KafkaConfig
import fs2.kafka.{ConsumerSettings, KafkaConsumer, KeyDeserializer, ValueDeserializer}

object KafkaConsumerFactory {
  def createKafkaConsumer[K, V](kafkaConfig: KafkaConfig)(topic: String)(implicit
      keyDeserializer: Resource[IO, KeyDeserializer[IO, K]],
      valueDeserializer: Resource[IO, ValueDeserializer[IO, V]]
  ): Resource[IO, KafkaConsumer[IO, K, V]] = {
    KafkaConsumer.resource(consumerSettings[K, V](kafkaConfig)).evalTap(_.subscribeTo(topic))
  }

  private def consumerSettings[K, V](
      kafkaConfig: KafkaConfig
  )(implicit keyDeserializer: Resource[IO, KeyDeserializer[IO, K]], valueDeserializer: Resource[IO, ValueDeserializer[IO, V]]) = {
    ConsumerSettings[IO, K, V]
      .withBootstrapServers(kafkaConfig.brokerUrl)
      .withProperties(kafkaConfig.consumerSettings)
  }
}
