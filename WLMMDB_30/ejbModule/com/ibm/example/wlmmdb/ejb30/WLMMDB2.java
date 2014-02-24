/*******************************************************************************
 * Copyright © 2012,2014 IBM Corporation and other Contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial Contribution
 *******************************************************************************/
package com.ibm.example.wlmmdb.ejb30;

import javax.annotation.Resource;
import javax.annotation.Resource.AuthenticationType;
import javax.annotation.Resources;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;

import com.ibm.example.wlmmdb.WLMMDBBase;

/**
 * EJB 3.0 MDB implementation class, showing use of annotations.
 * We have two identical sub-classes that share the same business logic.
 */
@MessageDriven(
		activationConfig = { @ActivationConfigProperty(
				propertyName = "destinationType", propertyValue = "javax.jms.Queue"
		) }, 
		mappedName = "jms/WLMMDBQueue2")
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
@TransactionManagement(TransactionManagementType.CONTAINER)
public class WLMMDB2 extends WLMMDBBase implements MessageListener {

    @Resource(name="jms/DefaultReplyQ")
    private Queue replyQ;
    
    @Resource
    private MessageDrivenContext mdbCtx;
  
    @Override
    protected Destination getDefaultReplyDestination() {
      return replyQ;
    }
  
    @Override
    protected MessageDrivenContext getMessageDrivenCtx() {
      return mdbCtx;
    }

    /**
     * @see MessageListener#onMessage(Message)
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void onMessage(Message message) {
      super.onMessage(message);
    }

}
