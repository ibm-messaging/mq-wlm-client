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
package com.ibm.example.replycorrelator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import com.ibm.example.wlmjmsattach.WLMJMSAttach;

/**
 * Singleton containing the ReplyCorrelator logic.
 */
public class ReplyCorrelator {

  /** Singleton instance */
  private static final ReplyCorrelator INSTANCE = new ReplyCorrelator(); 
  
  /**
   * Private constructor
   */
  private ReplyCorrelator() {
  }

  /**
   * @return The singleton ReplyCorrelator instance
   */
  public static ReplyCorrelator getInstance() {
    return INSTANCE;
  }

  /** The set of requests, managed under our synchronization */
  private HashSet<InFlightRequest> inFlightRequestSet = new HashSet<InFlightRequest>();
  
  /**
   * Create a new request. Must be called before sending the request.
   * @return The new request, with a unique identifier, but no correlation ID set yet
   */
  private synchronized InFlightRequest newRequest() {
    InFlightRequest request = new InFlightRequest();
    inFlightRequestSet.add(request);
    return request;
  }
  
  /**
   * Remove a request. Caller must ensure all requests are removed via finally processing.
   * @param request
   */
  private synchronized void remove(InFlightRequest request) {
    // We need to mark it complete (and notify) 
    request.cancel();
    inFlightRequestSet.remove(request);
  }
  
  /**
   * Update a request with the correlationID
   * @param request
   * @param correlationID
   */
  private synchronized void updateCorrelationID(InFlightRequest request, String correlationID) {
    // Note we have synchronized on the outer class first, then the inner.
    request.setCorelationID(correlationID);
  }
  
  /**
   * Returns a list of all requests that either match the correlationID specified,
   * or do not yet have a correlation ID set, which means they are in-flight requests
   * that could match once the correlation ID is set.
   * @param requiredCorrelID
   * @return A list of correlationIDs
   */
  synchronized List<InFlightRequest> getPossibleMatches(String requiredCorrelID) {
    
    // Create our return list (we expect only one match in most cases so optimize for that)
    List<InFlightRequest> returnList = new ArrayList<InFlightRequest>(1);
    
    // Look through all in-flight requests, adding them to our list if
    // they are either a match, or currently have a null correlation ID.
    // Our synchronization here means neither the list or the correlation IDs are
    // changing underneath us.
    for (InFlightRequest request : inFlightRequestSet) {
      String requestCorrelID = request.getCorrelationID();
      if (requestCorrelID == null || requestCorrelID.equals(requiredCorrelID)) {
        returnList.add(request);
      }
    }
    return returnList;
  }
  
  /**
   * Send the request message, and wait for the response to arrive on any
   * of the queue instances that the MDB is listening to.
   * @param producer The JMS MessageProducer to use to send the request, as contained in the connection returned from {@link WLMJMSAttach} 
   * @param requestMessage The request message
   * @param deliveryMode The JMS delivery mode. Usually {@link DeliveryMode#NON_PERSISTENT} when setExpiry is true, and {@link DeliveryMode#PERSISTENT} when setExpiry is false
   * @param priority The priority for the request message
   * @param timeout The timeout to wait for a response. Responses that arrive after this time will drive exception processing in the MDB
   * @param setExpiry Whether to set an expiry on the requests. When true the timeout is used for the time-to-live on the JMS request message. 
   * @return The response message, or null if a timeout occurs
   * @throws JMSException if a error occurs sending the request
   */
  public Message requestReply(MessageProducer producer, Message requestMessage, 
      int deliveryMode, int priority, long timeout, boolean setExpiry) throws JMSException {
    
    // First we create our request 
    InFlightRequest inFlightRequest = newRequest();
    
    // Setup a finally to ensure we always remove requests
    try {
      // Send the request
      producer.send(requestMessage, deliveryMode, priority, setExpiry?timeout:0);
      
      // Update the request with the correlation identifier from the request message ID.
      updateCorrelationID(inFlightRequest, requestMessage.getJMSMessageID());
      
      // Wait for the response
      return inFlightRequest.waitForReplyOrTimeout(timeout);
    }
    finally {
      // Remove our in-flight request
      remove(inFlightRequest);
    }    
  }
  
}
