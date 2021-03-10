package src.main.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Imagine this logger is used to capture business events that are written to a
 * file that is eventually fed into an ETL pipeline in our data warehouse. The
 * events are transformed into metrics and KPIs that product owners use to
 * monitor the health of the business. The code and pipeline are very old and
 * rarely change.
 *
 * One day when looking at the application logs, you notice that the for loop
 * below randomly throws ConcurrentModificationExceptions and loses some events
 * in the process. What are your recommendations for fixing the bug and
 * improving this code? How would you go about testing and releasing your
 * changes to minimize any side effects downstream in the data warehouse?
 */
public class EntryLogger {

  private static final Logger SYSTEM_LOG = Logger.getLogger(EntryLogger.class.getName());

  private Thread loggerThread;

  // These objects contain information about business domains that metrics can be derived from
  // Use concurrent collection to be thread-safe
  private ConcurrentLinkedQueue<Object> logEntries = new ConcurrentLinkedQueue<>();

  private PrintWriter logWriter = null;

  public EntryLogger(long flushInterval) {
    loggerThread = new Thread(() -> {

      while (true) {
        try {
          
          //Move this code outside the loop to save time
          try {
            logWriter = getLogWriter();
          } catch (IOException ioe) {
            SYSTEM_LOG.severe(String.format("Error on getting the logger: %s", ioe.getMessage()));
          }
          
          /*
            Instead of doing a copy, save the current size, then iterate that
            number of times, getting always the first element, and removing it
            at the same time.
          */
          int size = logEntries.size();

          for (int i = 0; i < size; i++) {
            Object logEntry = logEntries.poll();
            logWriter.println(logEntry);
            MetricsSingleton.getInstance().recordMetrics(logEntry);
          }

          logWriter.flush();
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

  //Wrapper method to access counter in MetricsSingleton
  public int getNumberOfLogs() {
    return MetricsSingleton.getInstance().getCounter();
  }
  
  /**
   * Throughout this codebase, singletons are commonly used for instance
   * management. MetricsSingleton doesn't do anything, but is a placeholder to
   * represent coupling to a Singleton in EntryLogger.
   */
  static class MetricsSingleton {

    private int sillyCounter = 0;
    private static final Logger METRICS_LOG = Logger.getLogger(MetricsSingleton.class.getName());

    private static MetricsSingleton instance = new MetricsSingleton();

    private MetricsSingleton() {
    }

    //Add getter method to compare the number of logs.
    public int getCounter() {
      return sillyCounter;
    }
    
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
