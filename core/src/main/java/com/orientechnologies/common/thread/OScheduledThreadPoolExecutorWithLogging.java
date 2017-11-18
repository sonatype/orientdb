package com.orientechnologies.common.thread;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;

import java.util.concurrent.*;

/**
 * The same as thread {@link ScheduledThreadPoolExecutor} but also logs all exceptions happened inside of the tasks which caused
 * tasks to stop.
 */
public class OScheduledThreadPoolExecutorWithLogging extends ScheduledThreadPoolExecutor {
  public OScheduledThreadPoolExecutorWithLogging(int corePoolSize) {
    super(corePoolSize);
  }

  public OScheduledThreadPoolExecutorWithLogging(int corePoolSize, ThreadFactory threadFactory) {
    super(corePoolSize, threadFactory);
  }

  public OScheduledThreadPoolExecutorWithLogging(int corePoolSize, RejectedExecutionHandler handler) {
    super(corePoolSize, handler);
  }

  public OScheduledThreadPoolExecutorWithLogging(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
    super(corePoolSize, threadFactory, handler);
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);

    if (r instanceof Future<?>) {
      final Future<?> future = (Future<?>) r;
      //scheduled futures can block execution forever if they are not done
      if (future.isDone()) {
        try {
          future.get();
        } catch (CancellationException ce) {
          //ignore it we cancel tasks on shutdown that is normal
        } catch (ExecutionException ee) {
          t = ee.getCause();
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt(); // ignore/reset
        }
      }
    }

    if (t != null) {
      final Thread thread = Thread.currentThread();
      OLogManager.instance().error(this, "Exception in thread '%s'", t, thread.getName());

      if (t instanceof Error) {
        final Orient orient = Orient.instance();
        if (orient != null)
          orient.handleJVMError((Error) t);
      }
    }
  }
}
