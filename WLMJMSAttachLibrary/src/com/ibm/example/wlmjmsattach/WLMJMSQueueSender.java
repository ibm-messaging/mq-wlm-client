/*
 * Sample program 
 * © COPYRIGHT International Business Machines Corp. 2012,2013
 * All Rights Reserved * Licensed Materials - Property of IBM
 *
 * This sample program is provided AS IS and may be used, executed,
 * copied and modified without royalty payment by customer
 *
 * (a) for its own instruction and study,
 * (b) in order to develop applications designed to run with an IBM
 *     WebSphere product for the customer's own internal use.
 */
package com.ibm.example.wlmjmsattach;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;

/**
 * A wrapping object containing a JMS QueueConnection, QueueSession and QueueSender
 */
public class WLMJMSQueueSender {

  /** A logger for debug */
  private static final WLMJMSLogger log = new WLMJMSLogger(WLMJMSQueueSender.class);
  
  /** The JMS Queue Connection */
  private final QueueConnection queueConnection;
  
  /** The JMS Queue Session */
  private final Session queueSession;
  
  /** The JMS QueueSender */
  private final QueueSender queueSender;
  
  /**
   * Constructor (package private)
   * @param queueConnection
   * @param queueSession
   * @param queueSender
   */
  WLMJMSQueueSender(QueueConnection queueConnection, QueueSession queueSession, QueueSender queueSender) {
    this.queueConnection = queueConnection;
    this.queueSession = queueSession;
    this.queueSender = queueSender;
  }

  /**
   * @return The JMS QueueConnection
   */
  public QueueConnection getQueueConnection() {
    return queueConnection;
  }

  /**
   * @return The JMS QueueSession
   */
  public Session getQueueSession() {
    return queueSession;
  }

  /**
   * @return The JMS QueueSender
   */
  public QueueSender getQueueSender() {
    return queueSender;
  }

  /**
   * Close all the resource under this object. 
   * @param throwExceptions JMSExceptions are only thrown when this is set to true. Otherwise they are suppressed
   * @throws JMSException The last exception encountered during the close, if any and throwExceptions set
   */
  public void close(boolean throwExceptions) throws JMSException {
    final String methodName = "close";
    JMSException lastException = null;
    try {
      queueSender.close();
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "Producer close failed", e);
      lastException = e; 
    }
    try {
      queueSession.close();
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "Session close failed", e);
      lastException = e; 
    }
    try {
      queueConnection.close();
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "Connection close failed", e);
      lastException = e; 
    }
    if (lastException != null && throwExceptions) {
      if (log.enabled()) log.logRootExMsg(methodName, "Throwing. Root exception", lastException);
      throw lastException;
    }
  }
  
}
