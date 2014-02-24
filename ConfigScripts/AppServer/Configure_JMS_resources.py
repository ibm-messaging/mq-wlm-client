# *******************************************************************************
# * Copyright � 2012,2014 IBM Corporation and other Contributors.
# *
# * All rights reserved. This program and the accompanying materials
# * are made available under the terms of the Eclipse Public License v1.0
# * which accompanies this distribution, and is available at
# * http://www.eclipse.org/legal/epl-v10.html
# *
# * Contributors:
# * IBM - Initial Contribution
# *******************************************************************************
 
# Environment configuration
gateway1Host = "localhost"
gateway1Port = "1421"
gateway2Host = "localhost"
gateway2Port = "1422"

# We will define everything at Cell scope in this example.
allCells = AdminUtilities.convertToList(AdminConfig.getid('/Cell:/'))
cell = allCells[0]
cellName = AdminConfig.showAttribute(cell, 'name');
print "Defining objects at scope: " + cellName

# Define the destinations
print AdminTask.createWMQQueue(cell, ["-name WLMMDB.REQUEST -jndiName jms/WLMMDB.REQUEST -queueName WLMMDB.REQUEST"])
print AdminTask.createWMQQueue(cell, ["-name WLMMDB.BACKOUT -jndiName jms/WLMMDB.BACKOUT -queueName WLMMDB.BACKOUT"])
print AdminTask.createWMQQueue(cell, ["-name SENDINGAPP.REPLY -jndiName jms/SENDINGAPP.REPLY -queueName SENDINGAPP.REPLY"])
print AdminTask.createWMQQueue(cell, ["-name SENDINGAPP.BADREPLIES -jndiName jms/SENDINGAPP.BADREPLIES -queueName SENDINGAPP.BADREPLIES"])

# For the advanced reply example, with clustered reply queues serviced via MDBs, this scripting example is
# *** INCOMPLETE FOR A WebSphere Application Server CLUSTER DEPLOYMENT ***
# If you are investigating using the advanced reply example as a basis for a production deployment,
# then these queues should be configured instead at server scope, so that each server is bound to a
# separate MQ clustered queue manager alias for that instance, and has a separate listener queue.
# For example:
# - On clusmember1 a server-scoped createWMQQueue is run so that
#   * "jms/SENDINGAPP.LISTENER" points to queueName=SENDINGAPP.INST1.LISTENER
#   * "jms/SENDINGAPP.REPLY_QMALIAS" points to queueName=SENDINGAPP.INST1.LISTENER, qmgr=SENDINGAPP.INST1
# - On clusmember2 a server-scoped createWMQQueue is run so that
#   * "jms/SENDINGAPP.LISTENER" points to queueName=SENDINGAPP.INST2.LISTENER
#   * "jms/SENDINGAPP.REPLY_QMALIAS" points to queueName=SENDINGAPP.INST2.LISTENER, qmgr=SENDINGAPP.INST2
print AdminTask.createWMQQueue(cell, ["-name SENDINGAPP.LISTENER -jndiName jms/SENDINGAPP.LISTENER -queueName SENDINGAPP.INST1.LISTENER"])
print AdminTask.createWMQQueue(cell, ["-name SENDINGAPP.APPINST.QMALIAS -jndiName jms/SENDINGAPP.APPINST.QMALIAS -queueName SENDINGAPP.INST1.LISTENER -qmgr SENDINGAPP.INST1"])

# Define the activation specifications for GW 1 & 2
# Note the example settings here disable stopping the endpoint on delivery failure (we'll use a backout queue instead)
# and show how to increase the maximum number of concurrent invocations through the maxPoolSize.
commonConfig = " -wmqTransportType CLIENT -ccsid 1208 -qmgrSvrconnChannel WAS.CLIENTS -stopEndpointIfDeliveryFails false -maxPoolSize 20 -destinationType javax.jms.Queue -destinationJndiName invalid/CHANGE_FOR_EACH_APP"
print AdminTask.createWMQActivationSpec(cell, ["-name GATEWAY1_AS -jndiName jms/GATEWAY1_AS -qmgrName GATEWAY1 -qmgrHostname " + gateway1Host + " -qmgrPortNumber " + gateway1Port + commonConfig])
print AdminTask.createWMQActivationSpec(cell, ["-name GATEWAY2_AS -jndiName jms/GATEWAY2_AS -qmgrName GATEWAY2 -qmgrHostname " + gateway2Host + " -qmgrPortNumber " + gateway2Port + commonConfig])

# Define the connection factories for sending replies
commonConfig = " -wmqTransportType CLIENT -ccsid 1208 -qmgrSvrconnChannel WAS.CLIENTS"
gateway1CF = AdminTask.createWMQConnectionFactory(cell, ["-name GATEWAY1_CF -jndiName jms/GATEWAY1_CF -qmgrName GATEWAY1 -qmgrHostname " + gateway1Host + " -qmgrPortNumber " + gateway1Port + commonConfig])
print gateway1CF
gateway2CF = AdminTask.createWMQConnectionFactory(cell, ["-name GATEWAY2_CF -jndiName jms/GATEWAY2_CF -qmgrName GATEWAY2 -qmgrHostname " + gateway2Host + " -qmgrPortNumber " + gateway2Port + commonConfig])
print gateway2CF

# Configure the connection and session pools
jmsConnectionPoolSettings = [["maxConnections","20"]]
jmsSessionPoolSettings = [["maxConnections","20"]]
print AdminConfig.create("ConnectionPool", gateway1CF, jmsConnectionPoolSettings, "connectionPool")
print AdminConfig.create("ConnectionPool", gateway1CF, jmsSessionPoolSettings, "sessionPool")
print AdminConfig.create("ConnectionPool", gateway2CF, jmsConnectionPoolSettings, "connectionPool")
print AdminConfig.create("ConnectionPool", gateway2CF, jmsSessionPoolSettings, "sessionPool")

# Save the configuration
AdminConfig.save()
