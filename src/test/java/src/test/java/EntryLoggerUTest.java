package src.test.java;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.Test;

import src.main.java.EntryLogger;

public class EntryLoggerUTest {



  /**
  * This junit test simulates production traffic and reproduces the ConcurrentModificationException in EntryLogger.
  */
  @Test
  public void concurrencyProblem() throws Exception {
    EntryLogger logger = new EntryLogger(1);

    int numThreads = 32;
    int numBatches = 1000;
    int messagesPerBatch = 100;

    ExecutorService e = Executors.newFixedThreadPool(numThreads);
    try {
      for (int j = 0; j < numBatches; j++) {
        CyclicBarrier b = new CyclicBarrier(numThreads);
        CompletableFuture<String>[] futures = Stream.generate(() -> new CompletableFuture<String>())
                                                    .limit(numThreads * messagesPerBatch)
                                                    .toArray(CompletableFuture[]::new);
        for (int i = 0; i < numThreads; i++) {
          final int threadIndex = i;
          e.submit(() -> {
            try {
              b.await();
              for (int k = 0; k < messagesPerBatch; k++) {
                final int messageIndex = k;
                TestEntry entry = new TestEntry(futures[threadIndex * messagesPerBatch + messageIndex]);
                logger.addEntry(entry);
              }
            } catch (Exception ex) {
              System.out.println(ex.getMessage());
            }
          });
        }
        // TestEntry completes these futures on flush
        CompletableFuture f = CompletableFuture.allOf(futures);
        f.get(5000, TimeUnit.MILLISECONDS);
      }
    } catch (TimeoutException tex) {
      // short-circuit in the event of a ConcurrentModificationException
    }
    e.shutdown();
  }

  static class TestEntry {
    private final CompletableFuture f;

    TestEntry(CompletableFuture f) {
      this.f = f;
    }

    @Override
    public String toString() {
      f.complete("entry flushed");
      return Instant.now().toString();
    }
  }
}



