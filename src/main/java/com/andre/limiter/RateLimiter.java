package com.andre.limiter;

import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A RateLimiter designed to manage and control the flow of requests with the following features:
 *
 * <ul>
 *   <li><b>Synchronous Limiting:</b> Restricts up to a specified number of simultaneous requests.
 *   <li><b>Priority Handling:</b> Allows certain requests to be prioritized for faster processing.
 *   <li><b>Hierarchical Rate Limiting:</b> Supports multi-level rate control (second, minute, etc).
 *   <li><b>Token Bucket:</b> Limits requests based on available capacity refilled over time.
 * </ul>
 *
 * Suitable for scenarios requiring precise and flexible rate control.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.2
 */
public class RateLimiter {

  private static final long HIGHEST_TIMEOUT = Long.MAX_VALUE;
  private static final int LOWEST_PRIORITY = 1;

  private final ArrayList<Integer> priorityList;
  private final AtomicInteger requestCount;
  private int throughput;
  private long timeout;
  private long lapsed;

  public RateLimiter(int throughput) {
    this(throughput, HIGHEST_TIMEOUT);
  }

  public RateLimiter(int throughput, long timeout) {
    this.requestCount = new AtomicInteger();
    this.priorityList = new ArrayList<>();
    this.throughput = throughput;
    this.lapsed = nanoTime();
    this.timeout = timeout;
  }

  public void acquire() throws TimeoutException {
    acquire(LOWEST_PRIORITY);
  }

  public void acquire(int priority) throws TimeoutException {
    register(priority);
    long initialTime = nanoTime();
    do {
      long currentTime = nanoTime();
      resetCounter(currentTime);
      if (acquired(priority)) return;
      await(currentTime);
    } while (!timedOut(initialTime));
    throw new TimeoutException();
  }

  private synchronized void register(int priority) {
    if (priority == LOWEST_PRIORITY) {
      priorityList.add(priority);
    } else {
      allocate(priority);
    }
  }

  private void allocate(int priority) {
    int left = 0, right = priorityList.size() - 1;
    while (left <= right) {
      int mean = (left + right) / 2;
      if (priorityList.get(mean) > priority) {
        left = mean + 1;
      } else {
        right = mean - 1;
      }
    }
    priorityList.add(left, priority);
  }

  private boolean timedOut(long initialTime) {
    return (nanoTime() - initialTime) >= timeout;
  }

  private synchronized boolean acquired(Integer priority) {
    if (requestCount.get() > throughput) return false;

    if (priorityList.get(0) == LOWEST_PRIORITY && requestCount.incrementAndGet() <= throughput) {
      System.out.println(priorityList.get(0));
      priorityList.remove(priority);
      return true;
    }


    int firstSize = throughput - requestCount.get();
    boolean contains = false;
    for (int i = 0; i <= firstSize; i++) {
      if (priorityList.get(i) == priority) {
        contains = true;
        break;
      }
    }

    if (contains && requestCount.getAndIncrement() <= throughput) {
      System.out.println(priorityList.get(0));
      priorityList.remove(priority);
      return true;
    }
    return false;
  }

  private synchronized void resetCounter(long currentTime) {
    if (currentTime - lapsed >= 1_000_000_000.0) {
      requestCount.set(0);
      lapsed = currentTime;
      System.out.println("--");
    }
  }

  private synchronized void await(long currentTime) {
    long nextCycle = 1000 - NANOSECONDS.toMillis(currentTime - lapsed);
    try {
      wait(max(1, nextCycle));
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  public void adjustTimeout(long timeout) {
    this.timeout = timeout;
  }

  public void adjustLimit(int amount) {
    this.throughput = amount;
  }

  public int queueSize() {
    return requestCount.get();
  }

  public int currentRate() {
    return throughput;
  }
}