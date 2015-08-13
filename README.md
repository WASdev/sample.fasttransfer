# WebSphere Liberty User Feature: Fast Transfer

Since version 8.5.5, WAS Liberty has supported the Liberty collective. A collective is a group of Liberty servers that consists of normal members which are then managed by special members known as controllers. The controllers can also manage hosts without Liberty servers on them. One advantage of collectives is that the controller(s) keeps track of the security credentials for all of its members, so a client only needs to connect to one machine in the collective in order to perform administrative tasks on the collective as a whole. 

The task that this sample looks at is the distribution of files within the collective. There are currently two supported ways of distributing files. The first is to call the FileTransfer or FileService MBeans, but this method is more suited to tasks on a single server. The second method is to make a REST call which can send a single file to multiple hosts as explained in this [link](http://www-01.ibm.com/support/knowledgecenter/SSAW57_8.5.5/com.ibm.websphere.wlp.nd.doc/ae/twlp_collective_file_transfer_multihost.html?cp=SSAW57_8.5.5%2F1-3-11-0-3-2-17-1&lang=en). However, the current implementation of the transfer can only send a file from the controller to one host at a time, making the transfer slow.  

This sample introduces a new feature that solves the aforementioned problems. It allows for a single MBean call that quickly and efficiently distributes files to a list of hosts using the BitTorrent protocol. Speed increases greatly because all machines are sending files to each other, instead of one machine sending at a time. See more on BitTorrent [here](https://en.wikipedia.org/wiki/BitTorrent)

* [Building with Maven](docs/Building.md)
* [Getting WAS Liberty](docs/Liberty.md)
* [Setting Up a Collective](docs/Collective.md)
* [Installing the User Feature](docs/Install.md)
* [Post Install](docs/PostInstall.md)
* [Using the Feature](docs/Using.md)
* [Understanding the Code](docs/Code.md)