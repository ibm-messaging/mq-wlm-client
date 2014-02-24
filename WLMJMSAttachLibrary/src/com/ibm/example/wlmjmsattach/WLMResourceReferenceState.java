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
package com.ibm.example.wlmjmsattach;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class containing the WLM state for a particular connection factory.
 */
class WLMResourceReferenceState {

	/** An atomic integer */
	private final AtomicInteger wlmCounter = new AtomicInteger(0);
	
	/** An array of indexes that have failed, containing the time
	 *  that the last failed attempt occurred.
	 *  This allows all instances of the application sharing this state
	 *  to coordinate retries in the case where just one of the connection
	 *  factories connects to an endpoint that's down. */
	private volatile long[] lastFailureTimestamps = new long[WLMJMSAttach.WLM_GATEWAY_COUNT];
	
	/**
	 * Query whether the connection factory at the specified index has failed recently,
	 * and if so when. 
	 * @param cfIndex The index of the connection factory the caller is about to attempt to use
	 * @return The last time a connection failed, or -1 if the last connection was successful 
	 */
	public long getLastFailureTimestamp(int cfIndex) {
		return lastFailureTimestamps[cfIndex];
	}

	/**
	 * Mark that a connection has just succeeded using the specified index.
	 * Doesn't matter that this isn't synchronised, as it's just a hint to avoid extra
	 * processing. The caller only sets this if they saw a last-failure timestamp
	 * before the connect.
	 * @param cfIndex The index of the connection factory the caller just connected using
	 */
	public void setLastConnectionSuccessful(int cfIndex) {
		lastFailureTimestamps[cfIndex] = -1;
	}

	/**
	 * Mark that we're beginning a new connection attempt for a connection that's previously failed.
	 * Caller might subsequently call setLastConnectionSuccessful if the connection is good,
	 * but we need to update the timestamp ASAP to minimise the number of threads that attempt
	 * the connection.
	 * Doesn't matter that this isn't synchronised, as it's just a hint to avoid extra
	 * processing.
	 * @param cfIndex The index of the connection factory the caller just failed to use
	 */
	public void setLastConnectionAttempt(int cfIndex, long connectionAttemptStartTimestamp) {
		lastFailureTimestamps[cfIndex] = connectionAttemptStartTimestamp;
	}

	/**
	 * Called when a previously working connection fails. 
	 * @param cfIndex The index of the connection factory the caller just failed to use
	 */
	public void setLastConnectionFailed(int cfIndex) {
		lastFailureTimestamps[cfIndex] = System.currentTimeMillis();
	}
	
	/**
	 * Use our shared atomic integer to determine the next starting point for attempting
	 * to connect. This is the core of our round-robin WLM strategy. 
	 * @return The next index to start with when connecting.
	 */
	public int nextIndex() {
		// We increment our atomic counter, then take the modulus against the size of the index
		// (ensuring we return a positive value).
	  int nextCounterValue = wlmCounter.getAndIncrement();
	  if (nextCounterValue < 0) {
	    nextCounterValue = 0;
	    wlmCounter.set(nextCounterValue);
	  }
		return nextCounterValue % WLMJMSAttach.WLM_GATEWAY_COUNT;
	}
	
}
