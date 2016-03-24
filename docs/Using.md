## Using the Feature

Make sure your controller server is started and that you have a copy of the truststore on your local machine. Again, this can be found in ${LibertyUserDirectory}/servers/myController/resources/security/trust.jks. Run FTAdmin.jar to get instructions on the arguments you need to input to distribute files.
```bash
$java -jar FTAdmin.jar 

Takes six arguments: config_file path_to_package dest_dir hosts onController logs_dir

Config_file should be formatted as follows:
quick start security username
quick start security password
trustStore Path
trustStore Password
controller host
controller port
controller packakge directory path
clear controller package directory before upload (true or false)

hosts should be a list of hostnames separated by newlines

onController indicates whether the package is already on the Controller. Its value is either true or false. If it is true, path_to_package can just be the name of the file.

logs_dir is the directory where information about the transfer will be stored
```  

#### Example (package does not exist on controller)
If you need to upload the package to the controller, the 2nd argument should be the path to and including the file. The `onController` argument (5th argument) should be `false` to indicate the package does not already exist on the controller.
```bash
$java -jar FTAdmin.jar config uploadthisfile /home/ibmadmin/torrentdrop hosts false logs/

Uploading package to Controller...
Package upload finished in 0.393 seconds!
Starting fast transfer process...

``` 

### Example (package already exists on controller)
If the package already exists on the controller, the 2nd argument should be the name of the package. The directory path containing the package is specified in the config file (see config for more details). Set `onController` argument to `true`.

```bash
$java -jar FTAdmin.jar config testFile3_23 /home/ibmadmin/torrentdrop hosts true logs/

Skipping upload of file...
Starting fast transfer process...
```
