package pipelines.rest.socket

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import pipelines.rest.socket

final class WrappedPublisher[A](prefix: String, publisher: Publisher[A]) extends Publisher[A] {
  private val subscriptions = new AtomicInteger(0)
  override def subscribe(s: Subscriber[_ >: A]): Unit = {
    val newPrefix = s"$prefix-${subscriptions.incrementAndGet}"
    WrappedPublisher.info(s"$newPrefix::subscribe($s)")
    val wrapped = new socket.WrappedPublisher.WrappedSubscriber[A](newPrefix, s)
    publisher.subscribe(wrapped)
  }
}

object WrappedPublisher extends StrictLogging {
  private def info(msg: String) = {
    logger.debug(s"\n$msg\n")
  }
  class WrappedSubscription(prefix: String, s: Subscription) extends Subscription {
    override def request(n: Long): Unit = {
      info(s"$prefix::request($n)")
      s.request(n)
    }

    override def cancel(): Unit = {
      info(s"$prefix::cancel()")
      s.cancel()
    }
  }

  class WrappedSubscriber[A](prefix: String, subscriber: Subscriber[_ >: A]) extends Subscriber[A] {
    override def onSubscribe(s: Subscription): Unit = {
      val w = new WrappedSubscription(prefix, s)
      info(s"$prefix::onSubscribe($s)")
      subscriber.onSubscribe(w)
    }

    override def onNext(t: A): Unit = {
      info(s"$prefix::onNext($t)")
      subscriber.onNext(t)
    }

    override def onError(t: Throwable): Unit = {
      info(s"$prefix::onError($t)")
      subscriber.onError(t)
    }

    override def onComplete(): Unit = {
      info(s"$prefix::onComplete()")
      subscriber.onComplete()

    }
  }
}
