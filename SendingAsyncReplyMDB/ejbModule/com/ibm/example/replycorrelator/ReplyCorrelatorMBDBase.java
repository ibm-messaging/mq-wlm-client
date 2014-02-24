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
package com.ibm.example.replycorrelator;

import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * This class implements the MDB logic for the reply correlator.
 * The interface logic is contained in the singleton ReplyCorrelator class
 */
public abstract class ReplyCorrelatorMBDBase implements MessageListener {

  /**
   * Constructor
   */
  public ReplyCorrelatorMBDBase() {
  }
    
  /**
   * This is the logic for handling a bad reply message.
   * In our example, we drive the normal exception handling of the activation specification.
   * For the MQ RA, if TransactionAttributeType.NOT_SUPPORTED is specified on the onMessage
   * method, the message will be swallowed.
   * If this is changed to TransactionAttributeType.REQUIRED, the message will be rolled back,
   * and depending on the configuration of the activation specification and queue:
   * - The endpoint will stop if stopEndpointIfDeliveryFails is true (we set this to false in the example)
   * - Delivered to the backout queue almost immediately if BOTHRESH(1) and a BOQNAME are set on the queue 
   * - Otherwise redelivered until the BOTHRESH is met, or indefinitely if BOTHRESH is not set
   * @param replyMessage
   */
  private void handleInvalidOrOrphanedReply(Message replyMessage, String correlationID) {
    // Remove this throw to discard orphaned replies
    throw new RuntimeException("Invalid or orphaned reply message. CorrelationID=" + correlationID);
  }
  
  /**
   * The onMessage implementation of the reply correlator
   */
  public void onMessage(Message replyMessage) {    
    try {
      // Obtain the correlation ID of the reply message
      String correlationID = replyMessage.getJMSCorrelationID();
      if (correlationID == null) {
        handleInvalidOrOrphanedReply(replyMessage, correlationID);
      }
      else {
        // Find a list of requests that might be applicable
        List<InFlightRequest> possibleRequestMatches = 
            ReplyCorrelator.getInstance().getPossibleMatches(correlationID);
        
        // First do a dirty check around the list to find one that is an exact
        // match. It's extremely likely that this will find our request.
        InFlightRequest matchingRequest = null;
        for (InFlightRequest request : possibleRequestMatches) {
          if (correlationID.equals(request.getCorrelationID())) {
            matchingRequest = request;
            break;
          }
        }
        
        // If we haven't found the matching request, then there is a (small)
        // possibility that the thread that sent the request just hasn't got
        // around to setting the correlation ID yet.
        // So we go round again with synchronization and blocking.
        if (matchingRequest == null) {
          for (InFlightRequest request : possibleRequestMatches) {
            if (correlationID.equals(request.waitForCorrelationID())) {
              matchingRequest = request;
              break;
            }
          }          
        }
        
        if (matchingRequest == null) {
          // If at this point we still can't find a reply, then this is
          // an orphaned reply message
          handleInvalidOrOrphanedReply(replyMessage, correlationID);
        }
        else {
          // If we've found a requester, then we tap them on the shoulder
          // with the message.
          matchingRequest.markComplete(replyMessage);

          // NOTE: In a scenario where the response must be kept even if the
          //       requester fails or times out, a tap on the shoulder is insufficient.
          //       Once we complete our onMessage the message will have disappeared
          //       from the queue, so if the requesting thread fails we will not
          //       recover the message to a backout queue for manual (or automated)
          //       compensation. To account for this scenario, this logic would need
          //       to be updated to wait for a callback from the requesting thread
          //       to say it is safe to remove the message from the queue
          //       (noting this cannot be coordinated with their own transaction).
          //       
          //       IF YOU ARE IN THIS SITUATION, YOU SHOULD SERIOUSLY CONSIDER
          //       WHETHER REQUEST/REPLY PROCESSING IS THE BEST FIT FOR YOUR NEEDS.
          //       FIRE & FORGET PROCESSING IS A BETTER SOLUTION FOR MOST EXACTLY-ONCE
          //       SCENARIOS, WHERE THE REQUESTER DOES NOT RELY ON A RESPONSE COMING
          //       BACK, AND INSTEAD RELIES ON MQ TO RETRY DELIVERY UNTIL THE REQUEST
          //       COMPLETES.
          
        }
      }
      
    }
    catch (JMSException e) {
      // In the case of a JMS exception, drive normal MDB retry logic via a RuntimException
      throw new RuntimeException("JMSException: " + e.getMessage(), e);
    }    
  }

}
