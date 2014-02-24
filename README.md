mq-wlm-client
=============

Workload Managed Client attachment samples for MQ.

Currently the repository includes Java EE samples, first published
in this series of developerWorks articles:
http://ibm.co/1bBgRwn

These samples allows you to configure an environment per the
diagram below, with XA transaction support, and desirable fail-back
characteristics in the case where one MQ queue manager is restored
after a failover.

    -----      ---------------------------       -----
	|S1 |-->   |                          |   -->|R1 |
	|   |-->   | Two or more              |   -->|   |
    -----      | IBM WebSphere MQ         |      -----
               | Queue Managers,          |      
    -----      | running remotely         |      -----
	|S2 |-->   | from the sending         |   -->|R2 |
	|   |-->   | and receiving apps.      |   -->|   |
    -----      |                          |      -----
               | Each sender WLMs its     |      
    -----      | messages across two      |      -----
	|S3 |-->   | queue managers.          |   -->|R3 |
	|   |-->   | Each receiver (MDB)      |   -->|   |
    -----      | listens to two queues.   |      -----
               |                          |      
    -----      | Connections arranged so  |      -----
	|S4 |-->   | each queue has two       |   -->|R4 |
	|   |-->   | listeners, and each app  |   -->|   |
	-----      | server has two qmgrs.    |      -----
               |                          |      
               ----------------------------
               
The MQ infraststructure might be as simple as two highly 
available queue managers servicing a single application,
or as complex as multiple interconnected hubs linking all
applications in your enterprise.

Pull requests
-------------
When submitting a pull request, you must include a statement stating you accept
the terms in [CLA.md](CLA.md).

Quick Start for WebSphere Application Server Liberty Profile:
-------------------------------------------------------------
http://ibm.co/1ffGwBg

Quick Start for WebSphere Application Server:
---------------------------------------------

You can use any existing WebSphere Application Server server.

For Network Deployment cluster environments, see http://ibm.co/OeRI6b

* Setup the MQ environment as follows:

Commands:

    crtmqm GATEWAY1
    crtmqm GATEWAY2
    strmqm GATEWAY1
    strmqm GATEWAY2
    runmqsc GATEWAY1 < ConfigScripts/MQ/Gateway1.MQSC
    runmqsc GATEWAY2 < ConfigScripts/MQ/Gateway2.MQSC

* Setup your WebSphere Application Server as follows:

Windows:

    C:\path\to\AppServer\profiles\PROFILE_NAME\bin\wsadmin -lang jython -f ConfigScripts\MQ\Configure_MQRA.py
	C:\path\to\AppServer\profiles\PROFILE_NAME\bin\wsadmin -lang jython -f ConfigScripts\MQ\Configure_JMS_resources.py
	
Linux/UNIX:

    /path/to/AppServer/profiles/PROFILE_NAME/bin/wsadmin.sh -lang jython ConfigScripts/AppServer/Configure_MQRA.py
    /path/to/AppServer/profiles/PROFILE_NAME/bin/wsadmin.sh -lang jython ConfigScripts/AppServer/Configure_JMS_resources.py

* Export the required EAR files from the appropriate Eclipse project, in the Java EE perspective

SendingServletAppEAR - all sample Servlet projects to send messages

WLMMDB_21EAR - EJB 2.1 sample MDB with two endpoints defined in the deployment descriptor
or
WLMMDB_30EAR - EJB 3.0 sample MDB with two endpoints defined via annotations

* Deploy the applications to the app server

There should be no need to configure any settings during deployment

* Access the sample Servlets with the following link - ensuring the change the port as appropriate

http://localhost:9080/SendingServletApp/index.html
