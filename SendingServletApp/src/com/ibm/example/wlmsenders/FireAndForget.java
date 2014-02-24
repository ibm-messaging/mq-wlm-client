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
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

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
@WebServlet(urlPatterns = "/FireAndForget")
public class FireAndForget extends HttpServlet {
	private static final long serialVersionUID = 20130522L;
		
  @Resource(name = "jms/FireAndForgetTarget")
  private Destination fireAndForgetTarget;
  
  @Resource
  private UserTransaction userTransaction;

  /** our WLM JMS attach instance */
  private final WLMJMSAttach wlmJMSAttach;

  /** Logger */
  private static final WLMJMSLogger log = new WLMJMSLogger(FireAndForget.class);

  /**
   * @see HttpServlet#HttpServlet()
   */
  public FireAndForget() {
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
      // Begin a transaction
      userTransaction.begin();
      
      // Print the user transaction status
      out.println(WLMJMSTranUtils.summarizeUserTranStatus(userTransaction));
      
      // Create our JMS connection/session/producer using the WLM connection logic
      // Many fire-and-forget scenarios are for exactly once delivery, where a
      // database is updated in the same transaction as sending the request.
      // For this reason in this example we begin a transaction and send
      // a persistent message.
      // As such JDBC operations could be inserted into this sample, and would
      // be coordinated in an atomic transaction with sending the request.
      //
      // If you are instead looking to send nonpersistent messages, such as publishing
      // some non-critical state data on a topic, then you should consider
      // removing the transaction, changing the transaction
      // boolean in the getMessageProducer() call to false, and setting the DeliveryMode
      // to NON_PERSISTENT when sending the message.
      out.println("JMS destination: " + fireAndForgetTarget);
      wlmConnection = wlmJMSAttach.getMessageProducer(fireAndForgetTarget, true, Session.AUTO_ACKNOWLEDGE);
      
      // TODO: Replace this section with business logic      
      // We create a temporary queue solely for the purpose of finding out where
      // we are connected, as the temporary queue name should show this.
      // This is inefficient, so it is for demonstration purposes only.
      TemporaryQueue temporaryQueue = wlmConnection.getSession().createTemporaryQueue();
      String temporaryQueueName = temporaryQueue.getQueueName();
      out.println("Temporary queue showing where we are connected:");
      out.println(temporaryQueueName);
      temporaryQueue.delete();
      String exampleMessageBody = 
          this.getClass().getName() + " (thread \"" + Thread.currentThread().getName() + "\") sending at " + new Date();
      Message message = wlmConnection.getSession().createTextMessage(exampleMessageBody);
      out.println("Sending message \"" + exampleMessageBody + "\"");
      
      // Send the message
      wlmConnection.getProducer().send(message, 
          DeliveryMode.PERSISTENT, /* We are persistent in this example */  
          wlmConnection.getProducer().getPriority() /* Default priority */, 
          0 /* Do not expire */);
      out.println("JMSMessageID: " + message.getJMSMessageID());
      
      // Commit the transaction
      userTransaction.commit();
      
      // Mark that we're complete, so that we throw any exception seen during cleanup,
      // and do not attempt rollback of the transaction
      complete = true;
    }
    catch (HeuristicMixedException e) {
      if (log.enabled()) log.logExStack(methodName, "Transaction HeuristicMixedException", e);
      throw new ServletException("Transaction HeuristicMixedException: " + e.getMessage(), e);
    }
    catch (HeuristicRollbackException e) {
      if (log.enabled()) log.logExStack(methodName, "Transaction HeuristicRollbackException", e);
      throw new ServletException("Transaction HeuristicRollbackException: " + e.getMessage(), e);
    }
    catch (RollbackException e) {
      if (log.enabled()) log.logExStack(methodName, "Transaction RollbackException", e);
      throw new ServletException("Transaction RollbackException: " + e.getMessage(), e);
    }
    catch (NotSupportedException e) {
      if (log.enabled()) log.logExStack(methodName, "Transaction NotSupportedException", e);
      throw new ServletException("Transaction NotSupportedException: " + e.getMessage(), e);
    }
    catch (SystemException e) {
      if (log.enabled()) log.logExStack(methodName, "Transaction SystemException", e);
      throw new ServletException("Transaction SystemException: " + e.getMessage(), e);
    }
    catch (JMSException e) {
      if (log.enabled()) log.logRootExMsg(methodName, "JMSException in business logic", e);
      throw new ServletException("JMSException: " + e.getMessage(), e);
    }
    finally {
      // Rollback if we didn't complete
      if (!complete) try {
        userTransaction.rollback();
      }
      catch (SystemException e) {
        // We are already on an exception path, so we should not throw this exception
        if (log.enabled()) log.logExStack(methodName, "Exception during rollback", e);
      }
      
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
