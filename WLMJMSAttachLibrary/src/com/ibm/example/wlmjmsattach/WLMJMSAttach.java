/*******************************************************************************
 * Copyright � 2012,2014 IBM Corporation and other Contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial Contribution
 *******************************************************************************/
package com.ibm.example.wlmjmsattach;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Helper class example to provide workload balancing attachment between two Gateways
 * in a JavaEE environment, with retry logic up to a given timeout.
 * The interface creates the session and the producer, as connection pooling
 * within the application server means a broken connection might not be detected
 * until accessing the queue/topic.
 * If an application wants to receive replies, or send to additional destinations
 * using the same connection, it can create additional consumers/producers
 * against the Session in the returned object.
 */
public class WLMJMSAttach {

  /** All users of this class (within a classloader) must have the same number of
   *  gateways (2 is generally the magic number). */  
  public static final int WLM_GATEWAY_COUNT = 2;
  
  /** A default prefix to use if you do not want to choose a special one for your application */  
  public static final String DEFAULT_RES_REF_PREFIX = "jms/GWCF";
  
  /** A default initial delay to use if you do not want to choose a special one for your application */  
  public static final int DEFAULT_INITIAL_DELAY_MS = 200;
  
  /** A default timeout to use if you do not want to choose a special one for your application */  
  public static final int DEFAULT_TIMEOUT_MS = 30000;
  
  /** A default interval for retrying connections to single failed gateway in the WLM pool */  
  public static final int DEFAULT_FAILED_GATEWAY_RETRY_MS = 60000;

  /** A logger */
  private static final WLMJMSLogger log = new WLMJMSLogger(WLMJMSAttach.class);
  
  /** 
   * Maintain WLM state at the static level, for each resource reference prefix.
   * This means all instances of an application share the same state.
   * Also multiple applications could using the same resource reference prefix
   * could coordinate WLM across the same gateways, if this code is deployed as a
   * shared library (rather than packaged with the application).
   */
  private static final ConcurrentHashMap<String, WLMResourceReferenceState> wlmStates = new ConcurrentHashMap<String, WLMResourceReferenceState>();

  /** Keep a handle to the state applicable to our instance, once we've got it from the static hashmap */
  private final WLMResourceReferenceState wlmState;
  
  /** The list of connection factories, initialised during construction.
   *  These should be private to our instance, even though the WLM state (which we use to
   *  determines the index into this array we use each time we connect) is shared between instances. */
  private final ConnectionFactory[] connectionFactories = new ConnectionFactory[WLM_GATEWAY_COUNT];
    
  /** The initial delay to use for retry */
  private final int initialDelayMillis;
  
  /** The timeout to use for retry */
  private final int timeoutMillis;
  
  /** The amount of time to avoid attempting connections to an individual gateway after a connection attempt fails.
   *  e.g. the amount of time an individual gateway drops out of the WLM pool after a failure.
   *  This minimises delays attempting connections to gateways that are down. */
  private final int failedGatewayRetry;
  
  /**
   * Constructor, to be called during construction of a Bean instance.
   * @param resRefPrefix The prefix used by your resource references.
   * @param initialDelayMillis The initial delay to use when retrying if both CFs are unavailable, this is doubled each time we wait until we hit the timeout 
   * @param timeoutMillis The timeout after which to throw an exception if a connection cannot be established
   * @throws NamingException if any of the JNDI lookups fail
   */
  public WLMJMSAttach(String resRefPrefix, int initialDelayMillis, int timeoutMillis, int failedGatewayRetry) throws NamingException {
     this.initialDelayMillis = initialDelayMillis;
     this.timeoutMillis = timeoutMillis;
     this.failedGatewayRetry = failedGatewayRetry;
     if (initialDelayMillis <= 0 || timeoutMillis <= 0 || failedGatewayRetry <= 0) throw new IllegalArgumentException();
     
     // Lookup or create the WLM index atomic integer.
     // We use concurrent hashtable logic here for efficiency, rather than synchronization.
     WLMResourceReferenceState tmpWLMState = wlmStates.get(resRefPrefix);
     if (tmpWLMState == null) {
       tmpWLMState = new WLMResourceReferenceState();
       WLMResourceReferenceState existingState = wlmStates.putIfAbsent(resRefPrefix, tmpWLMState);
       if (existingState != null) tmpWLMState = existingState;
     }
     wlmState = tmpWLMState; // Save the eventual value to our final variable.
     
     // Lookup both of the CFs
     InitialContext ctx = new InitialContext();
     boolean complete = false;
     try {
       for (int i = 0; i < WLM_GATEWAY_COUNT; i++) {
         // Suffix the resource reference prefix with 1,2 etc. in the lookup
         connectionFactories[i] = (ConnectionFactory)ctx.lookup("java:comp/env/" + resRefPrefix + (i+1));
       }
       complete = true;
     } finally {
       try {
         ctx.close();
       }
       catch (NamingException e) {
         // Don't override any previous naming exception, but throw the close exception
         // if we were successful to this point.
         if (complete) throw e;
       }
     }
  }

  /**
   * Get a JMS QueueConnection/QueueSession/QueueSender set
   * @return
   * @throws JMSException
   */
  public WLMJMSQueueSender getQueueSender(Queue queue, boolean txn, int ackMode) throws JMSException {
    return (WLMJMSQueueSender)getConnectionWithRetry(QUEUE_CONNECTION_CREATOR, queue, txn, ackMode);
  }
  
  /**
   * Get a JMS TopicConnection/TopicSession/TopicPublisher set
   * @return
   * @throws JMSException
   */
  public WLMJMSTopicPublisher getTopicPublisher(Topic topic, boolean txn, int ackMode) throws JMSException {
    return (WLMJMSTopicPublisher)getConnectionWithRetry(TOPIC_CONNECTION_CREATOR, topic, txn, ackMode);
  }
  
  /**
   * Get a JMS 1.1 generic JMS Connection/Session/MessageProducer
   * @return
   * @throws JMSException
   */
  public WLMJMSMessageProducer getMessageProducer(Destination dest, boolean txn, int ackMode) throws JMSException {
    return (WLMJMSMessageProducer)getConnectionWithRetry(GENERIC_CONNECTION_CREATOR, dest, txn, ackMode);
  }
  
  /** Simple interface to allow our logic to be common across Queue/Topic/Generic connections */
  private static interface ConnectionCreator {
    public Object createConnAndSender(Object factory, Destination dest, boolean txn, int ackMode) throws JMSException;
  }
  
  /** Getter for QueueConnectionFactory objects */
  private static final class QueueConnectionCreator implements ConnectionCreator {
    public Object createConnAndSender(Object factory, Destination dest, boolean txn, int ackMode) throws JMSException {
      // We need to create the connection, session and sender
      // ensuring we clean-up the connection+session if we fail at any step.
      WLMJMSQueueSender wlmSender = null;
      QueueConnectionFactory queueCF = (QueueConnectionFactory)factory;
      QueueSession session = null;
      QueueConnection connection = queueCF.createQueueConnection();
      try {
        session = connection.createQueueSession(txn, ackMode);
        QueueSender sender = session.createSender((Queue)dest);
        wlmSender = new WLMJMSQueueSender(connection, session, sender);
      }
      finally {
        // Check if we need to clean-up because of an exception that's being thrown
        if (wlmSender == null) {
          try {
            if (session != null) session.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
          try {
             connection.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
        }
      }
      return wlmSender;
    }
  }
  
  /** Static singleton for QueueConnectionCreator */
  private static final QueueConnectionCreator QUEUE_CONNECTION_CREATOR = new QueueConnectionCreator();
  
  /** Getter for TopicConnectionFactory objects */
  private static final class TopicConnectionCreator implements ConnectionCreator {
    public Object createConnAndSender(Object factory, Destination dest, boolean txn, int ackMode) throws JMSException {
      // We need to create the connection, session and sender
      // ensuring we clean-up the connection+session if we fail at any step.
      WLMJMSTopicPublisher wlmSender = null;
      TopicConnectionFactory topicCF = (TopicConnectionFactory)factory;
      TopicSession session = null;
      TopicConnection connection = topicCF.createTopicConnection();
      try {
        session = connection.createTopicSession(txn, ackMode);
        TopicPublisher sender = session.createPublisher((Topic)dest);
        wlmSender = new WLMJMSTopicPublisher(connection, session, sender);
      }
      finally {
        // Check if we need to clean-up because of an exception that's being thrown
        if (wlmSender == null) {
          try {
            if (session != null) session.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
          try {
             connection.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
        }
      }
      return wlmSender;
    }
  }
  
  /** Static singleton for TopicConnectionCreator */
  private static final TopicConnectionCreator TOPIC_CONNECTION_CREATOR = new TopicConnectionCreator();
  
  /** Getter for GenericConnectionFactory objects */
  private static final class GenericConnectionCreator implements ConnectionCreator {
    public Object createConnAndSender(Object factory, Destination dest, boolean txn, int ackMode) throws JMSException {
      // We need to create the connection, session and sender
      // ensuring we clean-up the connection+session if we fail at any step.
      WLMJMSMessageProducer wlmSender = null;
      ConnectionFactory cf = (ConnectionFactory)factory;
      Session session = null;
      Connection connection = cf.createConnection();
      try {
        session = connection.createSession(txn, ackMode);
        MessageProducer sender = session.createProducer(dest);
        wlmSender = new WLMJMSMessageProducer(connection, session, sender);
      }
      finally {
        // Check if we need to clean-up because of an exception that's being thrown
        if (wlmSender == null) {
          try {
            if (session != null) session.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
          try {
             connection.close();
          }
          catch (Exception e) {
            // Nothing we can do here. We're already coping with an exception.
          }
        }
      }
      return wlmSender;
    }
  }
  
  /** Static singleton for GenericConnectionCreator */
  private static final GenericConnectionCreator GENERIC_CONNECTION_CREATOR = new GenericConnectionCreator();

  /**
   * Single-pass attempt to get a connection with WLM.
   * Starting with the next round-robin index, we try to get a good connection from one
   * of the connection factories. In most cases the first attempt will simply give
   * us a good connection from the JMS connection pool associated with the CF.
   * If the index we've chosen for WLM has failed recently, we try to avoid retrying
   * the connection too often with our failedGatewayRetry interval. However, we only
   * do this on the first pass (isRetry == false). Subsequent retry passes,
   * when we've tried/skipped all the connections once so we think all the
   * gateways are down, we always try all the connections.
   * @param connectionCreator
   * @param isRetry Is this a retry attempt. If so we always attempt to connect, r 
   * @return
   */
  private Object getConnectionWLM(ConnectionCreator connectionCreator, Destination dest, boolean txn, int ackMode, boolean isRetry) throws JMSException {
    final String methodName = "getConnectionWLM";

    // Get the initial round-robin index to try
    int firstIndex = wlmState.nextIndex();
    
    // Go through all the indexes until we get a good connection
    Object conn = null;
    JMSException lastException = null;
    for (int i = 0; i < WLM_GATEWAY_COUNT && conn == null; i++) {
      // What index are we trying on this time round the loop?
      int cfIndex = (firstIndex+i) % WLM_GATEWAY_COUNT; 
      
      // Unless we're on a retry pass, we should check this gateway looks healthy
      boolean skipUnhealthyGateway = false;
      long unhealthyConnectionAttemptTimestamp = -1;
      long lastFailureTimestamp = wlmState.getLastFailureTimestamp(cfIndex);
      if (!isRetry && lastFailureTimestamp > 0) {
        // We should only attempt the connection if our gateway timeout has expired
        unhealthyConnectionAttemptTimestamp = System.currentTimeMillis();
        skipUnhealthyGateway = 
            (unhealthyConnectionAttemptTimestamp - lastFailureTimestamp) < failedGatewayRetry;
        // If we're going to connect, we should update our retry timestamp immediately
        // to minimise the chance of other threads also trying connections.
        if (!skipUnhealthyGateway) {
          wlmState.setLastConnectionAttempt(cfIndex, unhealthyConnectionAttemptTimestamp);
          if (log.enabled()) log.debug(methodName, "Attempting previously failed connection " + cfIndex + ". Last failure time: " + new Date(unhealthyConnectionAttemptTimestamp));        
        }
        else {
          if (log.enabled()) log.debug(methodName, "Skipping connection " + cfIndex + ". Last failure time: " + new Date(unhealthyConnectionAttemptTimestamp));        
        }
      }
    
      // Attempt the connection
      if (!skipUnhealthyGateway) {        
        try {
          if (log.enabled()) log.debug(methodName, "Attempting connection " + cfIndex);
          conn = connectionCreator.createConnAndSender(connectionFactories[cfIndex], dest, txn, ackMode);
        }
        catch (JMSException e) {
          // Print out the root exception message, as this generally contains the most useful information 
          if (log.enabled()) log.logRootExMsg(methodName, "Failed", e);
          // If this is a connection we previously thought was healthy (so we haven't already
          // set our connection attempt timestamp above), then we need to mark this unhealthy.
          if (lastFailureTimestamp <= 0) wlmState.setLastConnectionFailed(cfIndex);            
          lastException = e;
        }
      }
      
      // If we just successfully connected to a gateway that was previously marked unhealthy,
      // then we need to update the state to mark it healthy.
      if (conn != null && unhealthyConnectionAttemptTimestamp > 0) {
        wlmState.setLastConnectionSuccessful(cfIndex);        
      }
    }
    
    // Either we're successfully connected, or we've run out of options.
    // If we've run out of options, we should throw the exception.
    if (conn == null) {
      if (lastException == null && !isRetry) {
        // We've hit the special case where both gateways are down,
        // and connections have been attempted recently. So in the first
        // (non-retry) phases we don't attempt any connections.
        // Throw a generic exception so the calling code enters the
        // retry phase, and calls us back with isRetry=true
        throw new JMSException("All gateways skipped due to recent failures");
      }
      else {
        throw lastException;    
      }
    }
    return conn;    
  }
  
  /**
   * @return A JMS Connection
   */
  private Object getConnectionWithRetry(ConnectionCreator connectionCreator, Destination dest, boolean txn, int ackMode) throws JMSException {
    final String methodName = "getConnectionWithRetry";
    
    // Run through the connection pool once, trying to get a connection
    Object conn = null;
    JMSException lastException = null;
    try {
      conn = getConnectionWLM(connectionCreator, dest, txn, ackMode, false /* First phase (non-retry) */);
    }
    catch (JMSException e) {
      if (log.enabled()) log.logExStack(methodName, "No gateways currently available. Entering retry logic. Last exception", e);
      lastException = e;
    }
    
    // Do we need to enter our retry logic?
    if (conn == null) {
      // Check the current time to use in our retry logic
      long startTime = System.currentTimeMillis();
      int timeWaiting = 0;
      int delay = initialDelayMillis;
      
      // Keep waiting until we get a connection or time out
      // We always retry once in this 2nd phase, as it's possible the first phase
      // didn't even try to connect, as all the servers were skipped due to connections
      // having been attempted too recently.
      do {
        if (log.enabled()) log.debug(methodName, "Retry loop. Delay=" + delay);
        
        // Sleep for the current delay
        try {
          Thread.sleep(delay);
        }
        catch (InterruptedException e) {
          // If we're interrupted throw the last exception
          throw lastException;
        }
        
        // Attempt all connections
        try {
          conn = getConnectionWLM(connectionCreator, dest, txn, ackMode, true /* Retry phase */);
        }
        catch (JMSException e) {
          lastException = e;
        }
        
        // See how long we have waited
        if (conn == null) {
          timeWaiting = (int)(System.currentTimeMillis() - startTime);
          if (timeWaiting < 0) timeWaiting = timeoutMillis; // Just in case of a clock change
          
          // Calculate the next delay
          delay = Math.min(delay * 2, timeoutMillis-timeWaiting);
        }
        
      } while (conn == null && timeWaiting < timeoutMillis);
    }
  
    // If we timed out, throw the last exception
    if (log.enabled()) log.debug(methodName, "Conn: " + conn);
    if (conn == null) throw lastException;
    
    // Return the connection
    return conn;
    
  }

}
