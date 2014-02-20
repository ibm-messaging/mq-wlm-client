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