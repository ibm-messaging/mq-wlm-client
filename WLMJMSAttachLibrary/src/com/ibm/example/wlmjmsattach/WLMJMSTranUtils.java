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

import java.lang.reflect.Method;

import javax.naming.InitialContext;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

/**
 * This class contains helper methods to print out transaction status.
 * These are not intended for functional use, but rather to aid in diagnosing
 * what the particular transaction context is in any particular environment.
 * 
 * This class uses the UOWSynchronizationRegistry class that is specific
 * to WebSphere Application Server environments.
 * In other JavaEE environments, the transaction status will not be shown.
 * 
 * NOTE: This class performance JNDI lookups and java reflection simply to
 * display the transaction context. This is provided to aid with experimentation
 * of various transaction contexts in a JavaEE environment, but is not optimal
 * for the critical path of a real application. So ensure you either remove
 * it, or check the a suitable LogLevel is enabled before calling it, in
 * a real-world application scenario. 
 */
public class WLMJMSTranUtils {

  /** Logger */
  private static final WLMJMSLogger log = new WLMJMSLogger(WLMJMSTranUtils.class);
  
  /** The WebSphere Application Server method for getting the current UOW type */
  private static final Method getUOWTypeMethod;
  
  /** The WebSphere Application Server method for getting the current UOW status */
  private static final Method getUOWStatusMethod;
  
  /**
   * Static initializer to see if we can obtain the UOWSynchronizationRegistry class.
   * This will not be available in non WebSphere Application Server environments,
   * so we use reflection here rather than referring to the class directly.
   */
  static {
    final String methodName = "<static init>";
    Method tGetUOWTypeMethod = null;
    Method tGetUOWStatusMethod = null;
    try {
      Class<?> uowSynchronizationRegistryClass =
          Class.forName("com.ibm.websphere.uow.UOWSynchronizationRegistry");
      if (uowSynchronizationRegistryClass != null) {
        tGetUOWTypeMethod = uowSynchronizationRegistryClass.getMethod("getUOWType", new Class[]{});
        tGetUOWStatusMethod = uowSynchronizationRegistryClass.getMethod("getUOWStatus", new Class[]{});
      }
    }
    catch (Throwable e) {
      // Assume we're simply not in an JavaEE environment that has the class
      if (log.enabled()) log.logRootExMsg(methodName, methodName, e);
      if (e instanceof ThreadDeath) throw (ThreadDeath)e; // Never suppress this
    }
    getUOWTypeMethod = tGetUOWTypeMethod;
    getUOWStatusMethod = tGetUOWStatusMethod;
  }
  
  /**
   * A WebSphere Application Server specific way to show the actual
   * transaction context on a thread.
   * We use Java reflection to access the class, so this method
   * can be run in any JavaEE environment (although it will be a no-op).
   * @return
   */
  public static String inspectWASTxnContext() {
    final String methodName = "inspectWASTxnContext";
    StringBuilder retval = new StringBuilder();
    if (getUOWTypeMethod != null && getUOWStatusMethod != null) {
      InitialContext ctx = null;
      try {
        ctx = new InitialContext();
        Object uowRegistry = ctx.lookup("java:comp/websphere/UOWSynchronizationRegistry");
        if (uowRegistry != null) {
          Object uowTypeO = getUOWTypeMethod.invoke(uowRegistry, new Object[]{});
          Object uowStatusO = getUOWStatusMethod.invoke(uowRegistry, new Object[]{});
          if (uowTypeO instanceof Integer &&
              uowStatusO instanceof Integer) {
            int uowType = ((Integer)uowTypeO).intValue();
            int uowStatus = ((Integer)uowStatusO).intValue();
            // Format the data, from UOWSynchronizationRegistry constant field values
            retval.append("Type: ");
            switch(uowType) {
            case 0: retval.append("Local"); break;
            case 1: retval.append("Global"); break;
            case 2: retval.append("Activity Session"); break;
            default: retval.append(uowType);
            }
            retval.append(", Status: ");
            switch(uowStatus) {
            case 0: retval.append("Active"); break;
            case 1: retval.append("Rollback Only"); break;
            case 2: retval.append("Completing"); break;
            case 3: retval.append("Committed"); break;
            case 4: retval.append("Rolled Back"); break;
            case 5: retval.append("None"); break;
            default: retval.append(uowStatus);
            }
          }
        }
      }
      catch (Exception e) {
        if (log.enabled()) log.debug(methodName, "UOW debug info not available (" + WLMJMSLogger.findRootExceptionMessage(e) + ")");
      }
      finally {
        try {
          if (ctx != null) ctx.close();
        }
        catch (Exception e) {
          if (log.enabled()) log.logExStack(methodName, "Error closing InitialContext", e);
        }
      }
    }
    return retval.toString();
  }
 
  /**
   * Simple helper to get a string representation of the status from UserTransaction
   * @param userTransaction
   * @return
   */
  public static String summarizeUserTranStatus(UserTransaction userTransaction) {
    StringBuilder retval = new StringBuilder();
    retval.append("UserTransaction Status: ");
    if (userTransaction != null) {
      try {
        int status = userTransaction.getStatus();
        switch (status) {
        case Status.STATUS_ACTIVE: retval.append("Active"); break;
        case Status.STATUS_COMMITTED: retval.append("Committed"); break;
        case Status.STATUS_COMMITTING: retval.append("Committing"); break;
        case Status.STATUS_MARKED_ROLLBACK: retval.append("Marked Rollback"); break;
        case Status.STATUS_NO_TRANSACTION: retval.append("No Transaction"); break;
        case Status.STATUS_PREPARED: retval.append("Prepared"); break;
        case Status.STATUS_PREPARING: retval.append("Preparing"); break;
        case Status.STATUS_ROLLEDBACK: retval.append("Rolled Back"); break;
        case Status.STATUS_ROLLING_BACK: retval.append("Rolling Back"); break;
        case Status.STATUS_UNKNOWN: retval.append("Unknown"); break;
        default: retval.append(status);
        }
        String wasContext = inspectWASTxnContext();
        if (wasContext.length() > 0) {
          retval.append(". WebSphere Application Server Transaction Context: " + wasContext);
        }
      }
      catch (SystemException e) {
        retval.append("Error getting status from UserTransaction: " + e);
      }
    }
    else {
      retval.append("UserTransaction is null");
    }
    return retval.toString();
  }
  

}
