// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.odb.api.service

import lucuma.odb.api.service.ErrorFormatter.syntax._
import clue.model.StreamingMessage._
import clue.model.StreamingMessage.FromServer._
import cats.effect.{ConcurrentEffect, Fiber}
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{Pipe, Stream}
import fs2.concurrent.{NoneTerminatedQueue, SignallingRef}
import io.circe.Json
import org.log4s.getLogger


/**
 * A GraphQL subscription in effect type F.
 */
trait Subscriptions[F[_]] {

  /**
   * Adds a new subscription receiving events from the provided `Stream`.
   *
   * @param id     client-provided id for the subscription
   * @param events stream of Either errors or Json results that match the
   *               subscription query
   */
  def add(id: String, events: Stream[F, Either[Throwable, Json]]): F[Unit]

  /**
   * Removes a subscription so that it no longer provides events to the client.
   *
   * @param id client-provided id
   */
  def remove(id: String): F[Unit]

  /**
   * Removes all subscriptions.
   */
  def terminate: F[Unit]

}

object Subscriptions {

  import syntax.json._

  private[this] val logger = getLogger

  /**
   * Tracks a single client subscription.
   *
   * @param id      Client-supplied identification for the subscription.
   *
   * @param results Underlying stream of results, each of which is an Either
   *                error or subscription query match
   */
  private final class Subscription[F[_]: ConcurrentEffect](
    val id:      String,
    val results: Fiber[F, Unit],
    val stopped: SignallingRef[F, Boolean]
  ) {

    val isStopped: F[Boolean] =
      stopped.get

    val stop: F[Unit] =
      for {
        _ <- stopped.set(true)
        _ <- results.cancel
      } yield ()

  }

  // Converts raw graphQL subscription events into FromServer messages.
  private def fromServerPipe[F[_]](id: String): Pipe[F, Either[Throwable, Json], FromServer] =
    _.map {
      case Left(err)   => Error(id, err.format)
      case Right(json) => json.toDataMessage(id)
    }

  def apply[F[_]](
    replyQueue: NoneTerminatedQueue[F, FromServer]
  )(implicit F: ConcurrentEffect[F]): F[Subscriptions[F]] =
    Ref[F].of(Map.empty[String, Subscription[F]]).map { subscriptions =>
      new Subscriptions[F]() {

        def info(m: String): F[Unit] =
          F.delay(logger.info(m))

        def send(m: FromServer): F[Unit] =
          for {
            _ <- info(s"Subscriptions enqueueing $m")
            _ <- replyQueue.enqueue1(Some(m))
          } yield ()

        def replySink(id: String): Pipe[F, Either[Throwable, Json], Unit] =
          events => fromServerPipe(id)(events).evalMap(send)

        override def add(id: String, events: Stream[F, Either[Throwable, Json]]): F[Unit] =
          for {
            r <- SignallingRef(false)
            in = r.discrete.evalTap(v => info(s"signalling ref = $v"))
            es = events.through(replySink(id)).interruptWhen(in)
            f <- F.start(es.compile.drain)
            _ <- subscriptions.update(_.updated(id, new Subscription(id, f, r)))
          } yield ()

        override def remove(id: String): F[Unit] =
          for {
            m <- subscriptions.getAndUpdate(_.removed(id))
            _ <- m.get(id).fold(().pure[F])(_.stop)
            _ <- send(Complete(id))  // TODO: is this expected?
          } yield ()

        override def terminate: F[Unit] =
          for {
            m <- subscriptions.getAndSet(Map.empty[String, Subscription[F]])
            _ <- m.values.toList.traverse_(_.stop)
            _ <- m.keys.toList.traverse_(id => send(Complete(id))) // TODO: yes?
          } yield ()

      }
    }
}
