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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * A wrapping object containing a JMS Connection, Session and Producer
 */
public class WLMJMSMessageProducer {

  /** A logger for debug */
  private static final WLMJMSLogger log = new WLMJMSLogger(WLMJMSMessageProducer.class);
  
  /** The JMS Connection */
  private final Connection connection;
  
  /** The JMS Session */
  private final Session session;
  
  /** The JMS MessageProducer */
  private final MessageProducer producer;
  
  /**
   * Constructor (package private)
   * @param connection
   * @param session
   * @param producer
   */
  WLMJMSMessageProducer(Connection connection, Session session, MessageProducer producer) {
    this.connection = connection;
    this.session = session;
    this.producer = producer;
  }

  /**
   * @return The JMS Connection
   */
  public Connection getConnection() {
    return connection;
  }

  /**
   * @return The JMS Session
   */
  public Session getSession() {
    return session;
  }

  /**
   * @return The JMS Producer
   */
  public MessageProducer getProducer() {
    return producer;
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
      producer.close();
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "Producer close failed", e);
      lastException = e; 
    }
    try {
      session.close();
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "Session close failed", e);
      lastException = e; 
    }
    try {
      connection.close();
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
