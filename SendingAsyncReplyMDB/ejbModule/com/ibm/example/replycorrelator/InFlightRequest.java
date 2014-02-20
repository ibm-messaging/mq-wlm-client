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

import java.util.concurrent.atomic.AtomicLong;

import javax.jms.Message;

/**
 * An in-flight-request.
 * A unique identifier is given to each request, and then the correlationID
 * is set when it's known after the request has been sent.
 * We implement Comparable on the unique identifier, to allow use of a
 * Set for efficiency.
 */
public class InFlightRequest implements Comparable<InFlightRequest> {
  
  /** A way of allocating unique request IDs */
  static final AtomicLong nextRequestID = new AtomicLong(0);
      
  /** The ID allocated to this request */
  private final Long requestID = Long.valueOf(nextRequestID.incrementAndGet());
  
  /** The correlation ID for this request */
  private volatile String correlationID = null;
  
  /** Whether this request is complete */
  private boolean complete = false;
  
  /** The reply message */
  private Message replyMessage = null;
  
  /**
   * Dirty read the correlation ID
   * @return The correlation ID
   */
  public String getCorrelationID() {
    return correlationID;
  }
  
  /**
   * Get the correlation ID, waiting for it to be set or us to
   * become complete due to a timeout on the requester.
   * @return The correlation ID
   */
  public synchronized String waitForCorrelationID() {
    while (correlationID == null && !complete) {
      try {
        wait();
      }
      catch (InterruptedException e) {
        // Check and continue
      }
    }
    return correlationID;
  }
  
  /**
   * @param correlationID The correlation ID to set
   */
  synchronized void setCorelationID(String correlationID) {
    // Allow null-checking on the correlationID to determine if it's initialised
    if (correlationID == null) this.correlationID = "";
    else this.correlationID = correlationID;
    // Notify as there might be MDB threads waiting in waitForCorrelationID
    notifyAll();
  }
  
  /**
   * Mark that this request is complete.
   * @param replyMessage The reply message or null in the case of a timeout
   */
  synchronized void markComplete(Message replyMessage) {    
    this.replyMessage = replyMessage;
    this.complete = true;
    // Notify as there might be MDB threads waiting in waitForCorrelationID
    notifyAll();
  }

  /**
   * Cancel the request if outstanding (just a wrapper for markComplete(null))
   */
  void cancel() {
    markComplete(null);
  }

  /**
   * Wait for the request to complete
   * @param timeout The timeout to wait for a response
   * @return
   */
  synchronized Message waitForReplyOrTimeout(long timeout) {
    if (timeout <= 0) throw new IllegalArgumentException();
    if (complete) throw new IllegalStateException();
    long startTime = System.currentTimeMillis();
    long remainingTime = timeout;
    while (!complete && remainingTime > 0) {
      try {
        wait(remainingTime);
      }
      catch (InterruptedException e) {
        // Ignore and keep waiting
      }
      if (!complete) remainingTime = 
          timeout - (System.currentTimeMillis() - startTime);
    }
    return replyMessage;
  }
  
  /**
   * Set comparator
   */
  public int compareTo(InFlightRequest o) {
    return requestID.compareTo(o.requestID);
  }
  
  /**
   * Equals consistent with comparator
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InFlightRequest)) return false;
    return requestID.equals(((InFlightRequest)o).requestID);
  }
  
  /**
   * Hashcode consistent with comparator
   */
  @Override
  public int hashCode() {
    return requestID.intValue();
  }    
}

