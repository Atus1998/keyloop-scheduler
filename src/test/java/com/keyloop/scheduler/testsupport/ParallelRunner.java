package com.keyloop.scheduler.testsupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs tasks on their own platform threads and releases them at the same moment. Returns only when
 * every task has ended, so nothing is still running when the next test cleans the database.
 * Platform threads, not virtual ones: on JDK 21 a virtual thread blocked in {@code synchronized}
 * pins its carrier, and the winning transaction could wait for a carrier to commit.
 */
public final class ParallelRunner {

  private ParallelRunner() {}

  /**
   * Results in the order of {@code tasks}. A task that throws fails the whole run. {@code timeout}
   * is one deadline for starting every task and collecting every result; shutting down may wait up
   * to {@code timeout} more.
   */
  public static <T> List<T> runTogether(List<Callable<T>> tasks, Duration timeout)
      throws Exception {
    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("No tasks to run");
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      if (!ready.await(remaining(deadline), TimeUnit.NANOSECONDS)) {
        throw new AssertionError("Tasks did not start within " + timeout);
      }
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(remaining(deadline), TimeUnit.NANOSECONDS));
      }
      return List.copyOf(results);
    } finally {
      executor.shutdownNow();
      if (!executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
        throw new AssertionError("Tasks did not end within " + timeout);
      }
    }
  }

  private static long remaining(long deadline) {
    return Math.max(0, deadline - System.nanoTime());
  }
}
