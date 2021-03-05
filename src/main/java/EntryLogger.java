package src.main.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Imagine this logger is used to capture business events that are written to a file that is eventually fed into an ETL
 * pipeline in our data warehouse.  The events are transformed into metrics and KPIs that product owners use to
 * monitor the health of the business.  The code and pipeline are very old and rarely change.
 *
 * One day when looking at the application logs, you notice that the for loop below randomly throws
 * ConcurrentModificationExceptions and loses some events in the process.  What are your recommendations for fixing
 * the bug and improving this code?  How would you go about testing and releasing your changes to minimize any side
 * effects downstream in the data warehouse?
 */
public class EntryLogger {

  private static final Logger SYSTEM_LOG = Logger.getLogger(EntryLogger.class.getName());

  private Thread loggerThread;

  // These objects contain information about business domains that metrics can be derived from
  private List<Object> logEntries = Collections.synchronizedList(new LinkedList<>());

  private PrintWriter logWriter = null;

  public EntryLogger(long flushInterval) {
    loggerThread = new Thread(() -> {

      while (true) {
        try {
          List<Object> copy;
          synchronized (logEntries) {
            copy = logEntries;
            logEntries = Collections.synchronizedList(new LinkedList<>());

            // toggle to see more log detail
            // SYSTEM_LOG.info(String.format("logging %d events", copy.size()));
          }
          if (!copy.isEmpty()) {

            // this loop throws a ConcurrentModificationException in production

            for (Object logEntry : copy) {
              logWriter = getLogWriter();
              logWriter.println(logEntry);
              MetricsSingleton.getInstance().recordMetrics(logEntry);
            }
            logWriter.flush();
          }
          Thread.sleep(flushInterval);

        } catch (Throwable ex) {
          SYSTEM_LOG.severe(String.format("Error logging events: %s", ex.getClass().getSimpleName()));
        }
      }
    });
    loggerThread.start();
  }

  // addEntry() is called from many different places by the application at request time to record events
  public void addEntry(Object e) {
    logEntries.add(e);
  }

  private PrintWriter getLogWriter() throws IOException {
    String filename = new SimpleDateFormat("yyyy-MM-dd-HH").format(new Date()) + ".log";
    File logFile = new File(filename);

    if (!logFile.exists()) {
      logFile.createNewFile();
    }
    
    if (logWriter != null) {
      logWriter.close();
    }
    return new PrintWriter(new FileOutputStream(logFile, true));
  }

  /**
  * Throughout this codebase, singletons are commonly used for instance management.   MetricsSingleton doesn't do anything,
  * but is a placeholder to represent coupling to a Singleton in EntryLogger.
  */
  static class MetricsSingleton {
    private int sillyCounter = 0;
    private static final Logger METRICS_LOG = Logger.getLogger(MetricsSingleton.class.getName());

    private static MetricsSingleton instance = new MetricsSingleton();
    private MetricsSingleton() { }

    static MetricsSingleton getInstance() {
      return instance;
    }

    public void recordMetrics(Object o) {

      // imagine this is an APM operation that relies on an external dependency (DB, network call, etc.)
      sillyCounter++;

      if (sillyCounter % 100 == 0) {
        METRICS_LOG.info("entriesLogged=" + sillyCounter);
      }
    }
  }
}
