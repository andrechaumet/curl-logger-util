package org.example.limiter;

import org.example.RateLimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.lang.System.nanoTime;

/**
 * Rate Limiter: This component restricts the number of times a function can be invoked within a
 * specified time period and taking priorities in count. It aims to prevent system or external
 * service overload and ensures controlled resource usage.
 *
 * <p>A rate limiter can be used, for example, to ensure that no more than X requests per second are
 * made to an API.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.1
 */
public class StandardRateLimiter implements RateLimiter {
  private final AtomicInteger requestCount;
  private long startTime;
  private int throughput;

  public StandardRateLimiter(int throughput) {
    this.throughput = throughput;
    this.requestCount = new AtomicInteger(0);
    this.startTime = nanoTime();
  }

  @Override
  public void acquire() throws InterruptedException {
    while (true) {
      long currentTime = nanoTime();
      resetCounter(currentTime);
      if (withinLimit()) {
        return;
      } else {
        await(currentTime);
      }
    }
  }

  private boolean withinLimit() {
    return requestCount.incrementAndGet() <= throughput;
  }

  private void resetCounter(long currentTime) {
    if (currentTime - startTime >= 1_000_000_000.0) {
      requestCount.set(0);
      startTime = currentTime;
    }
  }

  private synchronized void await(long currentTime) throws InterruptedException {
    requestCount.decrementAndGet();
    long waitTime = 1000 - TimeUnit.NANOSECONDS.toMillis(currentTime - startTime);
    wait(max(1, waitTime));
  }

  @Override
  public void increase(int amount) {
    throughput += amount;
  }

  @Override
  public void decrease(int amount) {
    throughput -= amount;
  }

  @Override
  public int currentRate() {
    return throughput;
  }

  @Override
  public int queueSize() {
    return requestCount.get();
  }
}
