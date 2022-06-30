import cats.effect.{IO, Resource}
import fs2.Stream
import fs2.kafka._
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import munit.CatsEffectSuite
import org.apache.kafka.common.TopicPartition

import scala.concurrent.duration.{Duration, DurationInt}
import scala.jdk.CollectionConverters.MapHasAsScala

class GracefulShutdownAppTest extends CatsEffectSuite with EmbeddedKafka {

  override def munitTimeout: Duration = 60.seconds

  def produceHelloForever(brokers: String, topic: String): IO[Unit] = {
    KafkaProducer
      .stream(
        ProducerSettings(
          RecordSerializer[IO, String],
          RecordSerializer[IO, String]
        ).withBootstrapServers(brokers)
      )
      .flatMap { producer =>
        Stream
          .emit("hello")
          .repeat
          .covary[IO]
          .metered(1.second)
          .evalMap(v => producer.produce(ProducerRecords.one(ProducerRecord(topic, v, v))).flatten)
      }
      .compile
      .drain
  }

  val kafkaResource: Resource[IO, EmbeddedKafkaConfig] = Resource.make({
    val config = EmbeddedKafkaConfig()
    IO(EmbeddedKafka.start()(config)) >> IO.pure(config)
  })(_ => IO(EmbeddedKafka.stop()))

  test("GracefulShutdown.run should shut down gracefully") {
    kafkaResource.use { config =>
      val _brokers = s"127.0.0.1:${config.kafkaPort}"
      val _topic = "mytopic"
      val _groupId = "mygroupid"
      val app = new GracefulShutdownApp {
        override val bootstrapServers: String = _brokers
        override val topic: String = _topic
        override val consumerGroupId: String = _groupId
      }

      for {
        fib1 <- produceHelloForever(_brokers, _topic).start
        fib2 <- app.run(List.empty[String]).start
        _ <- IO.sleep(10.seconds)
        _ <- IO(println("Cancelling task!"))
        _ <- fib2.cancel
        _ <- fib1.cancel
        committedOffset <- IO.fromTry(
          withAdminClient(
            _.listConsumerGroupOffsets(_groupId)
              .partitionsToOffsetAndMetadata()
              .get()
              .asScala
              .map { case (tp, offsetResult) => tp -> offsetResult.offset() }
              .toMap
          )
        )
      } yield {
        assertEquals(committedOffset, Map(new TopicPartition(_topic, 0) -> 9L))
      }
    }
  }

}
