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
package com.ibm.example.wlmmdb;

import java.util.Date;

import javax.ejb.MessageDrivenContext;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.naming.NamingException;

import com.ibm.example.wlmjmsattach.WLMJMSAttach;
import com.ibm.example.wlmjmsattach.WLMJMSLogger;
import com.ibm.example.wlmjmsattach.WLMJMSMessageProducer;
import com.ibm.example.wlmjmsattach.WLMJMSTranUtils;

/**
 * Contains the actual logic
 */
public abstract class WLMMDBBase implements MessageListener {

  /** The WLM JMS Attachment object */
  private final WLMJMSAttach wlmJMSAttach;
  
  /** Set up logging */
  private static final WLMJMSLogger log = new WLMJMSLogger(WLMMDBBase.class);
  
  /**
   * An example of how to have a resource reference defined in the sub-classes.
   * @return Here it's the default reply destination if the request messages doesn't have one.
   */
  protected abstract Destination getDefaultReplyDestination();
  
  /**
   * An example of how to have access to the MessageDrivenContext
   * @return The implementation returns the MessageDrivenContext via standard EJB semantics
   */
  protected abstract MessageDrivenContext getMessageDrivenCtx();
  
  /**
   * Constructor
   */
  public WLMMDBBase() {
    // Construct the WLM JMS Attachment object
    try {
      wlmJMSAttach = new WLMJMSAttach(
          WLMJMSAttach.DEFAULT_RES_REF_PREFIX,
          WLMJMSAttach.DEFAULT_INITIAL_DELAY_MS,
          WLMJMSAttach.DEFAULT_TIMEOUT_MS,
          WLMJMSAttach.DEFAULT_FAILED_GATEWAY_RETRY_MS);
    }
    catch (NamingException e) {
      throw new RuntimeException("Failed to initialize: " + e.getMessage(), e);
    }
  }

  /**
   * The example onMessage in this case shows how to send a reply back.
   * In this example the body we use is a simple text message.
   */
  public void onMessage(Message message) {
    final String methodName = "onMessage";
    
    WLMJMSMessageProducer wlmConnection = null;
    boolean complete = false;
    try {
      // To help you investigate the various transaction contexts in a WebSphere
      // Application Server environment, we inspect and log the transaction context here.
      // For other JavaEE environments, nothing will be logged
      if (log.enabled()) {
        String txnContext = WLMJMSTranUtils.inspectWASTxnContext();
        if (txnContext.length() > 0)
          log.debug(methodName, "WebSphere Application Server txn context: " + txnContext);
      }      
      
      // TODO: Put the business logic for processing the request here (before you obtain
      //       the connection+session from the pool to build & send the reply).

      // Attach to the reply queue using our WLM gateway logic
      // In this example to support both request/reply and fire&forget messages we
      // check if a reply destination has been specified, and if one hasn't then
      // we send it to a default destination. For example, this could be bound to
      // the back-out queue during deploy.
      Destination replyDestination = message.getJMSReplyTo();
      if (replyDestination == null) replyDestination = getDefaultReplyDestination();
      wlmConnection = wlmJMSAttach.getMessageProducer(replyDestination, true, Session.AUTO_ACKNOWLEDGE);

      // TODO: Replace this section with the business logic for generating the reply 
      // We create a temporary queue solely for the purpose of finding out where
      // we are connected TO SEND THE REPLY, as the temporary queue name should show this.
      // This is inefficient, so it is for demonstration purposes only.
      // There is also no JMS way to see where the MDB received the message from, as
      // JMS does not provide access to the MQ connection used by the resource adapter. 
      TemporaryQueue temporaryQueue = wlmConnection.getSession().createTemporaryQueue();
      String temporaryQueueName = temporaryQueue.getQueueName();
      temporaryQueue.delete();
      String exampleMessageBody = 
          this.getClass().getName() + " (thread \"" + Thread.currentThread().getName() + 
          "\") received \"" + message.getJMSMessageID() + "\" at " + new Date() +
          ".\nTempQ to show where MDB connected to send the reply: " + temporaryQueueName;
      if (log.enabled()) log.debug(methodName, exampleMessageBody);
      Message replyMessage = wlmConnection.getSession().createTextMessage(exampleMessageBody);
      
      // Set the headers on the reply message appropriately
      int replyPersistence = message.getJMSDeliveryMode();
      int replyPriority = message.getJMSPriority();
      long replyExpiration = message.getJMSExpiration();
      replyMessage.setJMSCorrelationID(message.getJMSMessageID());
      
      // Send the request message back
      wlmConnection.getProducer().send(replyMessage, replyPersistence, replyPriority, replyExpiration);
      if (log.enabled()) log.debug(methodName, "Reply JMSMessageID: " + replyMessage.getJMSMessageID());
      
      // Mark that we're complete, so that finally logic knows to throw exceptions
      complete = true;
    }
    catch (JMSException e) {
      // TODO: Consider exception handling. This runtime exception will cause standard
      // re-delivery logic to be triggered in the app server.
      if (log.enabled()) log.logRootExMsg(methodName, "JMSException", e);
      throw new RuntimeException("JMSException in business logic: " + e.getMessage(), e);
    }
    finally {
      // Ensure we close off our resources, only throwing an exception if we successfully
      // completed the above logic (e.g. we're not already throwing an earlier exception)
      if (wlmConnection != null) {
        try {
          wlmConnection.close(complete);
        }
        catch (JMSException e) {
          throw new RuntimeException("JMSException on Connection close: " + e.getMessage(), e); 
        }
      }
    }
    
  }

}
