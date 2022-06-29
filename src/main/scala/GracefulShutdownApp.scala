import cats.effect._
import cats.implicits._
import fs2.kafka._

import scala.concurrent.duration.DurationInt

trait GracefulShutdownApp extends IOApp {
  val bootstrapServers: String
  val topic: String
  val consumerGroupId: String

  lazy val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(consumerGroupId)

  def expensiveProcessRecord(record: CommittableConsumerRecord[IO, String, String]): IO[Unit] =
    IO(println(s"Starting processing of record at offset = ${record.record.offset}")) >>
      IO.sleep(2.seconds) >>
      IO(println(s"Finished processing of record at offset = ${record.record.offset}"))

  def run(consumer: KafkaConsumer[IO, String, String]): IO[Unit] =
    consumer.subscribeTo(topic) >>
      consumer.stream
        .evalMap(msg => expensiveProcessRecord(msg).as(msg.offset))
        .through(commitBatchWithin(100, 15.seconds))
        .compile
        .drain

  def handleError(e: Throwable): IO[Unit] = IO(println(e.toString))

  override def run(args: List[String]): IO[ExitCode] = (for {
    stoppedDeferred <- Deferred[IO, Either[Throwable, Unit]]
    gracefulShutdownStartedRef <- Ref[IO].of(false)
    _ <- KafkaConsumer
      .resource(consumerSettings)
      .allocated
      .bracketCase { case (consumer, _) =>
        run(consumer).attempt.flatMap { result: Either[Throwable, Unit] =>
          gracefulShutdownStartedRef.get.flatMap {
            case true  => stoppedDeferred.complete(result)
            case false => IO.pure(result).rethrow
          }
        }.uncancelable
      } { case ((consumer, closeConsumer), exitCase) =>
        (exitCase match {
          case Outcome.Errored(e) => handleError(e)
          case _ =>
            for {
              _ <- IO(println("Starting graceful shutdown"))
              _ <- gracefulShutdownStartedRef.set(true)
              _ <- consumer.stopConsuming
              stopResult <- stoppedDeferred.get
                .timeoutTo(10.seconds, IO.pure(Left(new RuntimeException("Graceful shutdown timed out"))))
              _ <- stopResult match {
                case Right(()) => IO(println("Succeeded in graceful shutdown"))
                case Left(e)   => IO(println("Failed to shutdown gracefully")) >> IO.raiseError(e)
              }
            } yield ()
        }).guarantee(closeConsumer)
      }
  } yield ()).as(ExitCode.Success)
}
