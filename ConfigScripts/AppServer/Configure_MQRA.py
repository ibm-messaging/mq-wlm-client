# Sample program 
# (C) COPYRIGHT International Business Machines Corp. 2012,2013
# All Rights Reserved # Licensed Materials - Property of IBM
#
# This sample program is provided AS IS and may be used, executed,
# copied and modified without royalty payment by customer
#
# (a) for its own instruction and study,
# (b) in order to develop applications designed to run with an IBM
#     WebSphere product for the customer's own internal use.

# We need to configure the MQ Resource adapter (at all scopes in the cell) so that it
# keeps retrying as much as possible, and most importantly allows an application to restart
# if one of the endpoints inside the application are down.
# This includes ensuring the MQ resource adapter does not spend too long attempting
# to establish TCP/IP connections to a server that is unavailable.
#
# We also want to ensure that the resource adapter does not place any arbitrary limits
# or contention on connections. This makes the assumption we want to manage/tune
# instances and connection pools at the level of the individual application level.
# As a result, some limitations that exist in the default MQ resource adapter configuration
# are lifted by this script.
#
# Some of this functionality was added as JVM system properties in MQ V7.0, which is the version
# of the MQ client shipped in V7.0 and V8.0 of WebSphere Application Server.
# See APAR IZ76343, for the system properties.
# In MQ V7.1 these were changed into resource adapter custom properties, to match the existing
# custom properties for retry after an application has started.
# The script takes the approach of setting the JVM setting in all cases, and
# if the resource adapter has a custom property for the setting, it is set there as well.
# This approach ensures compatibility with all releases.

# Start by finding all MQ resource adapters, at all scopes.
allRAs = AdminUtilities.convertToList(AdminConfig.list('J2CResourceAdapter'))
mqRAs = [];
for ra in allRAs :
    desc = AdminConfig.showAttribute(ra, "description")
    if (desc != None and desc.count("Built In WebSphere MQ Resource Adapter") > 0):
        mqRAs.append(ra);

# Now for each MQ RA, we need to tweak the settings.
# We set the same value at every scope, as the resource adapter configuration in MQ
# is treated as a JVM-wide setting. The fact that WebSphere Application Server actually
# has three MQ RAs per server (at server, node & cell scope) means they should all be
# kept consistent to avoid confusion. Tuning the settings on a per-node basis would
# build a dependency on the order in which WebSphere Application Server initialises
# the three MQ Resource Adapters within the JVM.
MAX_INT = "2147483647"
for mqRA in mqRAs:
    propertySet = AdminConfig.showAttribute(mqRA, "propertySet")
    resourceProperties = AdminUtilities.convertToList(AdminConfig.showAttribute(propertySet, "resourceProperties"))
    for resourceProperty in resourceProperties:
        name = AdminConfig.showAttribute(resourceProperty, "name")
        if ("maxConnections" == name):
            print "Setting maxConnections=" + MAX_INT + " on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", MAX_INT]])
        if ("connectionConcurrency" == name):
            print "Setting connectionConcurrency=1 on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", "1"]]);
        if ("reconnectionRetryCount" == name):
            print "Setting reconnectionRetryCount=" + MAX_INT + " on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", MAX_INT]])
        if ("reconnectionRetryInterval" == name):
            print "Setting reconnectionRetryInterval=5000 on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", "5000"]])
        if ("startupRetryCount" == name):
            print "Setting startupRetryCount=" + MAX_INT + " on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", MAX_INT]])
        if ("startupRetryInterval" == name):
            print "Setting startupRetryInterval=5000 on " + mqRA 
            AdminConfig.modify(resourceProperty, [["value", "5000"]])

# Utility function for finding and setting a property an the JVM config of a server
import re
def setOrChangeJVMProperty(jvm, propName, newValue):
    # We need to replace in both generic and debug
    jvmArgNames = ["genericJvmArguments","debugArgs"]
    for jvmArgName in jvmArgNames:
        prevArgValue = AdminConfig.showAttribute(jvm, jvmArgName)
        reMatch = re.search('-D' + propName + '=\d+', prevArgValue)
        if (reMatch != None):
            newArgValue = prevArgValue[0:reMatch.start(0)]
            newArgValue = newArgValue + "-D" + propName + "=" + newValue
            if (reMatch.end(0) < len(prevArgValue)):
                newArgValue = newArgValue + prevArgValue[reMatch.end(0):]
        else:
            newArgValue = prevArgValue + " -D" + propName + "=" + newValue
        if (newArgValue != prevArgValue):
            print "Setting " + jvmArgName + "=\"" + newArgValue + "\" on " + jvm
            AdminConfig.modify(jvm, [[jvmArgName,newArgValue]])

# Now find every non-nodeagent/dmgr server's JVM configuration
allServers = AdminUtilities.convertToList(AdminConfig.getid('/Cell:/Node:/Server:/'))
for server in allServers:
    jvms = AdminUtilities.convertToList(AdminConfig.list("JavaVirtualMachine", server))
    for jvm in jvms:
        # The following two are redundant in V8.5 and later servers, where the MQ RA has custom properties
        setOrChangeJVMProperty(jvm, "com.ibm.mq.jms.tuning.startupReconnectionRetryCount", MAX_INT)
        setOrChangeJVMProperty(jvm, "com.ibm.mq.jms.tuning.startupReconnectionRetryInterval", "5000")
        # The following is applicable in all servers
        setOrChangeJVMProperty(jvm, "com.ibm.mq.cfg.TCP.Connect_Timeout", "10")

# Save the configuration
AdminConfig.save()
