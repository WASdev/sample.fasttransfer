## Post Installation Setup##

In order to use the feature we need to set up a few things on the controller host. 

### Server.xml

First, we need to edit the server.xml for the controller server. To enable the feature add
```xml
<featureManager> 
  <feature>usr:net.wasdev.fasttransfer</feature>
</featureManager>
``` 
To allow the controller to run commands on the hosts add 
```xml
<hostAccess enableCustomActions="true" useSftp="false"/>
```
 We also need the server to be able to write to directories on the host machine. To include a directory add 
```xml
<remoteFileAccess>
    <writeDir>/path/to/a/directory</writeDir>
</remoteFileAccess>
```

### Directory Config

Now, create two folders following the pattern x and x-config. For example, package and package-config. package is where you will keep files that you want to distribute. package-config is used by the feature. Move FTClient.jar to package-config. Next, move the truststore for your controller server to package-config. This can be found in ${LibertyUserDirectory}/servers/myController/resources/security/trust.jks


### Other
Make sure that /etc/hosts doesn't map the localhost address to the hostname. We need to send an actual IP address in the transfer request to the target hosts. Make sure that this is also the case on the target hosts so they can connect to each other. 
