package com.ibm.example.wlmjmsattach;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience wrapper for Java Logging.
 * Each class that needs logging constructs a static
 * instance of this class, with its class object.
 */
public class WLMJMSLogger {

  /** Keep a handle to the class name for logging */
  private final String className;
  
  /** Use Java logging for trace data */
  private final Logger log;
  
  /** The level to log at. FINE will be enabled with trace, INFO will go to SystemOut.
   *  In this simple example, all entries are logged at the same level. */
  private final Level LEVEL = Level.INFO;
  
  /**
   * Constructor to initialize Java Logging
   * @param clazz
   */
  public WLMJMSLogger(Class<?> clazz) {
    className = clazz.getName();
    log = Logger.getLogger(className);
  }
  
  /**
   * The owning class should check this for performance
   * before calling any of the logging methods.
   * @return Whether Java Logging is enabled for the configured log level
   */
  public boolean enabled() {
    return log.isLoggable(LEVEL);
  }
  
  /**
   * Log the specified string
   * Caller should first check LOG.isLoggable(LOG_LEVEL) for performance with trace turned off
   * @param methodName
   * @param msg
   */
  public void debug(String methodName, String msg) {
    log.logp(LEVEL, className, methodName, msg);
  }
  
  /**
   * Log an exception
   * @param methodName
   * @param msg
   * @param e
   */
  public void logExStack(String methodName, String msg, Exception e) {
    StringWriter strWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(strWriter);
    e.printStackTrace(printWriter);
    printWriter.flush();    
    log.logp(LEVEL, className, methodName, msg + ": " + strWriter.toString());
  }

  /**
   * Log the root cause exception
   * @param methodName
   * @param msg
   * @param e
   */
  public void logRootExMsg(String methodName, String msg, Throwable e) {
    log.logp(LEVEL, className, methodName, msg + ". Root exception msg: " + findRootExceptionMessage(e));
  }

  /**
   * Simple helper to get the root cause exception message from an exception stack.
   * @param e
   * @return
   */
  public static String findRootExceptionMessage(Throwable e) {
    Throwable root = e;
    Throwable cause = e.getCause();
    while (cause != null) {
      root = cause;
      cause = cause.getCause();
    }
    return root.getMessage();
  }

}
