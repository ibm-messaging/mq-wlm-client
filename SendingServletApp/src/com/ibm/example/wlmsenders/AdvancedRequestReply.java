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
package com.ibm.example.wlmsenders;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.annotation.Resource;
import javax.annotation.Resource.AuthenticationType;
import javax.annotation.Resources;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import com.ibm.example.replycorrelator.ReplyCorrelator;
import com.ibm.example.wlmjmsattach.WLMJMSAttach;
import com.ibm.example.wlmjmsattach.WLMJMSLogger;
import com.ibm.example.wlmjmsattach.WLMJMSMessageProducer;
import com.ibm.example.wlmjmsattach.WLMJMSTranUtils;

/**
 * Servlet implementation class FireAndForget
 */
@Resources({
  @Resource(            
    type = javax.jms.ConnectionFactory.class,
    name = "jms/GWCF1",
    authenticationType = AuthenticationType.CONTAINER,
    shareable = false
  ),
  @Resource(            
      type = javax.jms.ConnectionFactory.class,
      name = "jms/GWCF2",
      authenticationType = AuthenticationType.CONTAINER,
      shareable = false
    ),
})
@WebServlet(urlPatterns = "/AdvancedRequestReply")
public class AdvancedRequestReply extends HttpServlet {
  private static final long serialVersionUID = 20130522L;
		
  @Resource(name = "jms/RequestQueue")
  private Queue requestQueue;
  
  @Resource(name = "jms/AdvancedReplyQueue")
  private Queue advancedReplyQueue;
  
  @Resource
  private UserTransaction userTransaction;

  /** our WLM JMS attach instance */
  private final WLMJMSAttach wlmJMSAttach;

  /** Logger */
  private static final WLMJMSLogger log = new WLMJMSLogger(AdvancedRequestReply.class);
  
  /**
   * @see HttpServlet#HttpServlet()
   */
  public AdvancedRequestReply() {
    super();
    
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
   * Example for sending a message fire and forget
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
   *      response)
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    final String methodName = "doGet";
    
    // We simply return some text showing what we've done
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    
    WLMJMSMessageProducer wlmConnection = null;
    boolean complete = false;    
    try {
      // Print the user transaction status
      out.println(WLMJMSTranUtils.summarizeUserTranStatus(userTransaction));
      
      // Create our JMS connection/session/producer using the WLM connection logic
      // Note there is no transaction context established in this Servlet
      // and the JMS Session is non-transacted.
      // This is important as we need the message to be committed as
      // soon as we send it, as we will begin listening for a reply.
      out.println("JMS request destination: " + requestQueue);
      wlmConnection = wlmJMSAttach.getMessageProducer(requestQueue, false, Session.AUTO_ACKNOWLEDGE);

      // TODO: Replace this section with business logic      
      // We create a temporary queue solely for the purpose of finding out where
      // we are connected, as the temporary queue name should show this.
      // This is inefficient, so it is for demonstration purposes only.
      TemporaryQueue temporaryQueue = wlmConnection.getSession().createTemporaryQueue();
      String temporaryQueueName = temporaryQueue.getQueueName();
      out.println("Temporary queue showing where we are connected to send the request:");
      out.println(temporaryQueueName);
      temporaryQueue.delete();
      String exampleMessageBody = 
          this.getClass().getName() + " (thread \"" + Thread.currentThread().getName() + "\") sending at " + new Date();
      Message message = wlmConnection.getSession().createTextMessage(exampleMessageBody);
      out.println("Sending message \"" + exampleMessageBody + "\"");

      // Setup for request/reply
      out.println("JMS reply destination: " + advancedReplyQueue);
      message.setJMSReplyTo(advancedReplyQueue);

      // Most request/reply scenarios should be non-persistent, with an expiry on
      // the message. If you are considering a persistent scenario, where each
      // message must be recovered if the requester times out, then you need
      // to think carefully about how to handle the scenarios.
      // A common approach is to set an expiry with a report that contains
      // a full copy of the message, and to have an asynchronous task that
      // looks on the reply-queue for report messages and performs recovery,
      // or notifies an operator.
      int deliveryMode = DeliveryMode.PERSISTENT;  
      long timeToLive = 30000;
      
      // Invoke the advanced reply correlator to obtainer our reply regardless
      // of which clustered queue instance it goes to
      long receiveStartTime = System.currentTimeMillis();
      Message replyMessage = ReplyCorrelator.getInstance().requestReply(wlmConnection.getProducer(), 
          message, deliveryMode, wlmConnection.getProducer().getPriority(),
          timeToLive, true /* Expire the requests */ );
      if (replyMessage == null) {
        throw new ServletException("Did not receive a reply message. Waited for " +
            (System.currentTimeMillis() - receiveStartTime) + "ms.");
      }
      
      // TODO: Replace this section with business logic
      out.println("Received reply \"" + replyMessage.getJMSMessageID() + "\". Class: " + replyMessage.getClass().getName());
      if (replyMessage instanceof TextMessage) {
        out.println("TextMessage reply body:");
        out.println(((TextMessage)replyMessage).getText());
      }
      
      // Mark that we're complete, so that we throw any exception seen during cleanup
      complete = true;
    }
    catch (JMSException e) {
      if (log.enabled()) log.logRootExMsg(methodName, "JMSException in business logic", e);
      throw new ServletException("JMSException: " + e.getMessage(), e);
    }
    finally {
      // Close off our resources, ensuring we only throw an exception if we completed
      // our earlier logic, so we don't override an earlier exception that being thrown.
      if (wlmConnection != null) try {
        wlmConnection.close(complete);
      }
      catch (JMSException e) {
        throw new ServletException("JMSException on Connection close: " + e.getMessage(), e); 
      }
    }
    
  }

}
