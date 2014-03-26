package com.netaporter.salad.metrics.cache

import spray.caching.Cache
import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.atomic.AtomicLong
import akka.actor.Actor
import com.netaporter.salad.metrics.actor.factory.MetricsActorFactory
import com.netaporter.salad.metrics.messages.MetricEventMessage.GaugeEvent

trait CacheMetrics {
  this: Actor =>

  protected val eventActor = MetricsActorFactory.eventActor()(context)

  def cacheMetrics[V](delegate: Cache[V], metricsName: String): Cache[V] = new MetricsCache(delegate, metricsName)

  class MetricsCache[V](delegate: Cache[V], metricsName: String) extends Cache[V] {

    val total = new AtomicLong(0l)
    val misses = new AtomicLong(0l)
    def hits = total.longValue - misses.longValue

    def hitRatio =
      if (total.longValue == 0l) 0l
      else hits.toDouble / total.longValue.toDouble

    eventActor ! GaugeEvent(metricsName, hitRatio _)

    /**
     * Returns either the cached Future for the given key or evaluates the given value generating
     * function producing a `Future[V]`.
     */
    def apply(key: Any, genValue: () ⇒ Future[V])(implicit ec: ExecutionContext): Future[V] = {
      total.incrementAndGet()

      val incOnMiss = () => {
        misses.incrementAndGet()
        genValue.apply()
      }

      delegate.apply(key, incOnMiss)
    }

    /**
     * Retrieves the future instance that is currently in the cache for the given key.
     * Returns None if the key has no corresponding cache entry.
     */
    def get(key: Any) = {
      total.incrementAndGet()

      val res = delegate.get(key)

      if (res.isEmpty) {
        misses.incrementAndGet()
      }

      res
    }

    /**
     * Removes the cache item for the given key. Returns the removed item if it was found (and removed).
     */
    def remove(key: Any) = delegate.remove(key)
    def clear() = delegate.clear()
    def size = delegate.size
  }
}