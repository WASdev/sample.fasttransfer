package net.wasdev.ftadmin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

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
		// for logging
		System.setProperty("org.apache.commons.logging.Log",
				"org.apache.commons.logging.impl.Jdk14Logger");
		if (args.length != 6) {
			printHelp();
			System.exit(0);
		}
		Config config = readConfig(new File(args[0]));
		// get the package to transfer and where to transfer to
		File packageFile = new File(args[1]);
		String destDir = args[2];
		String destHosts = readHosts(new File(args[3]));
		// is the package already on the controller?
		Boolean onController = readOnController(args[4]);
		// logs directory
		String logsDir = args[5];
		HashSet<String> complHosts = executeTransfer(config, packageFile,
				destDir, destHosts, onController);
		complHostsReport(complHosts, packageFile.getName(), logsDir);
	}

	private static void printHelp() {
		System.out
				.println("Takes five arguments: config_file path_to_package dest_dir hosts onController logs_dir\n");
		System.out.println("dest_dir and logs_dir should not end with a slash\n");
		System.out
				.println("Config_file should be formatted as follows:\n"
						+ "quick start security username\n"
						+ "quick start security password\n"
						+ "trustStore Path\n"
						+ "trustStore Password\n"
						+ "controller host\n"
						+ "controller port\n"
						+ "controller packakge directory path\n"
						+ "clear controller package directory before upload (true or false)\n");
		System.out
				.println("hosts should be a list of hostnames separated by newlines\n");
		System.out
				.println("onController indicates whether the package is already on the Controller. Its value is either true or false. If it is true, path_to_package can just be the name of the file.\n");
		System.out.println("logs_dir is the directory where information about the transfer will be stored");
	}

	private static Config readConfig(File configFile) {

		Config config = new Config();
		String str_clearCPD = null;

		try (BufferedReader configReader = new BufferedReader(new FileReader(
				configFile))) {
			config.setUsername(configReader.readLine());
			config.setPassword(configReader.readLine());
			config.setTruststorePath(configReader.readLine());
			config.setTruststorePass(configReader.readLine());
			config.setHost(configReader.readLine());
			config.setPort(configReader.readLine());
			config.setContrPackageDir(configReader.readLine());
			str_clearCPD = configReader.readLine();
		} catch (FileNotFoundException e) {
			System.out.println("Config file does not exist!");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("improper config");
			System.exit(1);
		}

		if (str_clearCPD.toLowerCase().equals("true")) {
			config.setClearCPD(true);
		} else if (str_clearCPD.toLowerCase().equals("false")) {
			config.setClearCPD(false);
		} else {
			System.out
					.println("invalid option for clearing controller package directory");
			System.exit(1);
		}

		return config;
	}

	private static String readHosts(File hostsFile) {
		String hosts = "";
		String line;

		try (BufferedReader hostsReader = new BufferedReader(new FileReader(
				hostsFile));) {
			while ((line = hostsReader.readLine()) != null) {
				hosts += (line + ",");
			}
			if (hosts.length() > 0) {
				hosts = hosts.substring(0, hosts.length() - 1);
			}
		} catch (FileNotFoundException e) {
			System.out.println("Hosts file does not exist!");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Error in hosts file");
			System.exit(1);
		}

		return hosts;
	}

	private static Boolean readOnController(String onContrStr) {
		Boolean onController = false;
		if (onContrStr.equals("false")) {
			onController = false;
		} else if (onContrStr.equals("true")) {
			onController = true;
		} else {
			System.out.println("Invalid onController option");
			System.exit(1);
		}

		return onController;
	}

	// sets up httpclient to call server's REST API via SSL
	private static CloseableHttpClient setupHttpClient(Config config) {
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(
				AuthScope.ANY,
				new UsernamePasswordCredentials(config.getUsername(), config
						.getPassword()));
		SSLContext sslContext = null;
		try {
			sslContext = SSLContexts
					.custom()
					.loadTrustMaterial(new File(config.getTruststorePath()),
							config.getTruststorePass().toCharArray()).build();
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

		return httpclient;
	}

	// JMX connector needed to call server MBean
	private static JMXConnector setupJMXConnector(Config config) {
		System.setProperty("javax.net.ssl.trustStore",
				config.getTruststorePath());
		System.setProperty("javax.net.ssl.trustStorePassword",
				config.getTruststorePass());

		HashMap<String, Object> environment = new HashMap<String, Object>();
		environment.put("jmx.remote.protocol.provider.pkgs",
				"com.ibm.ws.jmx.connector.client");
		environment.put(JMXConnector.CREDENTIALS,
				new String[] { config.getUsername(), config.getPassword() });
		environment.put(ConnectorSettings.READ_TIMEOUT, 0);

		JMXConnector connector = null;
		try {
			JMXServiceURL url = new JMXServiceURL("service:jmx:rest://"
					+ config.getHost() + ":" + config.getPort()
					+ "/IBMJMXConnectorREST");
			connector = JMXConnectorFactory.newJMXConnector(url, environment);
			connector.connect();
		} catch (IOException e) {
			System.out.println("JMX Connector broken");
			System.exit(1);
		}

		return connector;
	}

	private static void uploadPackage(Config config, File packageFile) {
		String fileTransferURI = "https://" + config.getHost() + ":"
				+ config.getPort() + "/IBMJMXConnectorREST/file/";
		long startTime = System.currentTimeMillis();
		System.out.println("Uploading package to Controller...");

		try (CloseableHttpClient httpclient = setupHttpClient(config)) {
			HttpPost post = new HttpPost(fileTransferURI
					+ URLEncoder.encode(config.getContrPackageDir() + "/"
							+ packageFile.getName(), "utf-8"));
			post.setEntity(new FileEntity(packageFile));
			httpclient.execute(post);
		} catch (IOException e) {
			System.out.println("invalid host or port, or server not running");
			System.exit(1);
		}
		long endTime = System.currentTimeMillis();
		System.out.println("Package upload finished in "
				+ ((double) (endTime - startTime) / 1000) + " seconds!");
	}

	private static HashSet<String> executeTransfer(Config config,
			File packageFile, String destDir, String destHosts,
			Boolean onController) {

		try (JMXConnector connector = setupJMXConnector(config)) {
			MBeanServerConnection mbs = connector.getMBeanServerConnection();
			ObjectName FTControllerMBean = new ObjectName(
					"net.wasdev:feature=FastTransferFeature,type=FastTransfer,name=FastTransfer");

			if (mbs.isRegistered(FTControllerMBean)) {
				if (!onController) {
					if (config.getClearCPD()) {
						System.out.println("Cleaning package directory...");
						mbs.invoke(FTControllerMBean, "cleanPackageDir",
								new Object[] { config.getContrPackageDir() },
								new String[] { "java.lang.String" });
					}
					uploadPackage(config, packageFile);
				} else {
					System.out.println("Skipping upload of file...");
				}

				System.out.println("Starting fast transfer process...");
				long startTime = System.currentTimeMillis();
				// returns the ips of the host that successfully received the
				// file
				@SuppressWarnings("unchecked")
				HashSet<String> complHosts = (HashSet<String>) mbs
						.invoke(FTControllerMBean,
								"transferPackage",
								new Object[] { packageFile.getName(), destDir,
										destHosts, config.getUsername(),
										config.getPassword(),
										config.getTruststorePass(),
										config.getHost(), config.getPort(),
										config.getContrPackageDir() },
								new String[] { "java.lang.String",
										"java.lang.String", "java.lang.String",
										"java.lang.String", "java.lang.String",
										"java.lang.String", "java.lang.String",
										"java.lang.String", "java.lang.String" });
				long endTime = System.currentTimeMillis();
				System.out
						.println("Transfer finished successfully for "
								+ complHosts.size() + " out of "
								+ destHosts.split(",").length + " hosts in "
								+ ((double) (endTime - startTime) / 1000)
								+ " seconds!");
				return complHosts;

			} else {
				System.out.println("FastTransfer feature not up");
				System.exit(1);
				return null;
			}
		} catch (MalformedObjectNameException | InstanceNotFoundException
				| MBeanException | ReflectionException | IOException e) {
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	private static void complHostsReport(HashSet<String> complHosts,
			String pkgname, String logDir) {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		Date date = new Date();
		File outfile = new File(logDir + "/" + pkgname + "_CompletedHosts_"
				+ dateFormat.format(date));
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outfile))) {
			for (String ip : complHosts) {
				bw.write(ip + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
