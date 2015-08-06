package net.wasdev.ftadmin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.SSLContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;

import com.ibm.websphere.jmx.connector.rest.ConnectorSettings;

public class FTAdmin {
	public static void main(String args[]) throws UnsupportedEncodingException {
		// needed to stop HttpClient logging errors
		System.setProperty("org.apache.commons.logging.Log",
				"org.apache.commons.logging.impl.Jdk14Logger");

		if (args.length != 5) {
			System.out
					.println("Takes five arguments: config_file path_to_package dest_dir hosts onController\n");
			System.out.println("dest_dir should not end with a slash\n");
			System.out
					.println("Config_file should be formatted as follows:\n"
							+ "quick start security username\n"
							+ "quick start security password\n"
							+ "trustStore Path\n"
							+ "trustStore Password\n"
							+ "controller host\n"
							+ "controller port\n"
							+ "controller torrent dir\n"
							+ "clear controller torrent dir before upload (true or false)\n");
			System.out
					.println("hosts should be a list of hostnames separated by newlines\n");
			System.out
					.println("onController indicates whether the package is already on the Controller. Its value is either true or false. If it is true, path_to_package can just be the name of the file.\n");
			System.exit(0);
		}

		// parse config file
		BufferedReader configReader = null;
		String username = null;
		String password = null;
		String truststorePath = null;
		String truststorePass = null;
		String host = null;
		String port = null;
		String contrTorrDir = null;
		Boolean clearCTD = false;
		String str_clearCTD = null;

		try {
			configReader = new BufferedReader(new FileReader(args[0]));
		} catch (FileNotFoundException e) {
			System.out.println("Config file does not exist!");
			System.exit(1);
		}

		try {
			username = configReader.readLine();
			password = configReader.readLine();
			truststorePath = configReader.readLine();
			truststorePass = configReader.readLine();
			host = configReader.readLine();
			port = configReader.readLine();
			contrTorrDir = configReader.readLine();
			str_clearCTD = configReader.readLine();
		} catch (IOException e) {
			System.out.println("improper config");
			System.exit(1);
		}

		if (str_clearCTD.toLowerCase().equals("true")) {
			clearCTD = true;
		} else if (str_clearCTD.toLowerCase().equals("false")) {
			clearCTD = false;
		} else {
			System.out
					.println("invalid option for clearing controller torrent dir");
			System.exit(1);
		}

		// get src and dest
		File srcFile = new File(args[1]);
		String srcName = srcFile.getName();
		String destDir = args[2];

		// create comma separated list of hosts from hosts file
		BufferedReader hostsReader = null;
		String hostnames = "";
		String line;

		try {
			hostsReader = new BufferedReader(new FileReader(args[3]));
			while ((line = hostsReader.readLine()) != null) {
				hostnames += (line + ",");
			}
			if (hostnames.length() > 0) {
				hostnames = hostnames.substring(0, hostnames.length() - 1);
			}
		} catch (FileNotFoundException e) {
			System.out.println("Hosts file does not exist!");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Error in hostnames file");
			System.exit(1);
		}

		Boolean onController = false;
		if (args[4].equals("false")) {
			onController = false;
		} else if (args[4].equals("true")) {
			onController = true;
		} else {
			System.out.println("Invalid onController option");
			System.exit(1);
		}

		// set up HTTPClient for REST API
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(username, password));
		SSLContext sslContext = null;
		try {
			sslContext = SSLContexts
					.custom()
					.loadTrustMaterial(new File(truststorePath),
							truststorePass.toCharArray()).build();
		} catch (KeyManagementException | NoSuchAlgorithmException
				| KeyStoreException | CertificateException | IOException e) {
			System.out.println("invalid truststore information");
			System.exit(1);
		}

		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
				sslContext);
		CloseableHttpClient httpclient = HttpClientBuilder.create()
				.setDefaultCredentialsProvider(credentialsProvider)
				.setSSLSocketFactory(sslsf).setMaxConnPerRoute(100)
				.setMaxConnTotal(100).build();

		String fileTransferURI = "https://" + host + ":" + port
				+ "/IBMJMXConnectorREST/file/";

		// get MBean for torrentController and execute transfer
		System.setProperty("javax.net.ssl.trustStore", truststorePath);
		System.setProperty("javax.net.ssl.trustStorePassword", truststorePass);

		HashMap<String, Object> environment = new HashMap<String, Object>();
		environment.put("jmx.remote.protocol.provider.pkgs",
				"com.ibm.ws.jmx.connector.client");
		environment.put(JMXConnector.CREDENTIALS, new String[] { username,
				password });
		environment.put(ConnectorSettings.READ_TIMEOUT, 0);

		try {
			JMXServiceURL url = new JMXServiceURL("service:jmx:rest://" + host
					+ ":" + port + "/IBMJMXConnectorREST");
			JMXConnector connector = JMXConnectorFactory.newJMXConnector(url,
					environment);
			connector.connect();
			MBeanServerConnection mbs = connector.getMBeanServerConnection();

			ObjectName torrentControllerMBean = new ObjectName(
					"net.wasdev:feature=FastTransferFeature,type=FastTransfer,name=FastTransfer");

			if (mbs.isRegistered(torrentControllerMBean)) {
				if (!onController) {
					if (clearCTD) {
						// clean out torrent directory on controller
						System.out.println("Cleaning torrent directory...");
						mbs.invoke(torrentControllerMBean, "cleanTorrentDir",
								new Object[] { contrTorrDir },
								new String[] { "java.lang.String" });
					}
					// upload package to controller
					long startTime = System.currentTimeMillis();
					System.out.println("Uploading package to Controller...");
					HttpPost post = new HttpPost(fileTransferURI
							+ URLEncoder.encode(contrTorrDir + "/" + srcName,
									"utf-8"));

					post.setEntity(new FileEntity(srcFile));
					try {
						httpclient.execute(post);
					} catch (IOException e) {
						System.out
								.println("invalid host or port, or server not running");
						System.exit(1);
					}
					long endTime = System.currentTimeMillis();
					System.out.println("Package upload finished in "
							+ ((double) (endTime - startTime) / 1000)
							+ " seconds!");
				} else {
					System.out.println("Skipping upload of file...");
				}
				// start torrent process
				System.out.println("Starting fast transfer process...");
				long startTime2 = System.currentTimeMillis();
				int numCompl = (int) mbs.invoke(torrentControllerMBean,
						"transferTorrent", new Object[] { srcName, destDir,
								hostnames, username, password, truststorePass,
								host, port, contrTorrDir }, new String[] {
								"java.lang.String", "java.lang.String",
								"java.lang.String", "java.lang.String",
								"java.lang.String", "java.lang.String",
								"java.lang.String", "java.lang.String",
								"java.lang.String" });
				long endTime2 = System.currentTimeMillis();
				System.out.println("Transfer finished successfully for "
						+ numCompl + " out of " + hostnames.split(",").length
						+ " hosts in "
						+ ((double) (endTime2 - startTime2) / 1000)
						+ " seconds! Cleaning up...");

			} else {
				System.out.println("Torrent transfer feature not up");
				System.exit(1);
			}
		} catch (MalformedObjectNameException | InstanceNotFoundException
				| MBeanException | ReflectionException | IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		HttpDelete deleteTC = new HttpDelete(fileTransferURI
				+ URLEncoder.encode(destDir + "/" + srcName
						+ "TorrentClient.jar", "utf-8"));
		deleteTC.addHeader("com.ibm.websphere.collective.hostNames", hostnames);

		HttpDelete deleteTorr = new HttpDelete(fileTransferURI
				+ URLEncoder.encode(destDir + "/" + srcName + ".torrent",
						"utf-8"));
		deleteTorr.addHeader("com.ibm.websphere.collective.hostNames",
				hostnames);

		try {
			httpclient.execute(deleteTC);
			httpclient.execute(deleteTorr);
			httpclient.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Done!");

	}
}
