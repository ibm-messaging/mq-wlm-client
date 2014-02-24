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
package com.ibm.example.wlmmdb.ejb21;

import javax.ejb.EJBException;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.Destination;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.ibm.example.wlmmdb.WLMMDBBase;

/**
 * Single EJB 2.1 MDB implementation class.
 * Deployed multiple times via our deployment descriptor.
 */
public class WLMMDB extends WLMMDBBase implements MessageListener, MessageDrivenBean {

  /** */
  private static final long serialVersionUID = 20140212l;

  /** The default replyTo queue */
  private final Queue replyQ;
  
  /** The MDB context */
  private MessageDrivenContext mdbCtx;
  
  /**
   * Constructor
   */
  public WLMMDB() {
    // Construct the WLM JMS Attachment object
    try {
      InitialContext ctx = new InitialContext();
      replyQ = (Queue)ctx.lookup("java:comp/env/jms/DefaultReplyQ");
      ctx.close();
    }
    catch (NamingException e) {
      throw new RuntimeException("Failed to initialize: " + e.getMessage(), e);
    }
  }  
  
  public void ejbCreate() throws EJBException {
  }

  public void ejbRemove() throws EJBException {
  }

  public void setMessageDrivenContext(MessageDrivenContext ctx)
      throws EJBException {
    this.mdbCtx = ctx;
  }

  /**
   * @return the default replyQ looked up in our constructor
   */
  protected Destination getDefaultReplyDestination() {
    return replyQ;
  }

  /**
   * @return the MDB context
   */
  protected MessageDrivenContext getMessageDrivenCtx() {
    return mdbCtx;
  }

}
