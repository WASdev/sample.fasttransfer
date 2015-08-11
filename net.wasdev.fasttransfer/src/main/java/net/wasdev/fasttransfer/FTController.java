package net.wasdev.fasttransfer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.FileUtils;
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
import org.osgi.service.component.annotations.Component;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;

@Component(name = "FTController", service = { FTControllerMBean.class }, immediate = true, property = { "jmx.objectname="
		+ FTControllerMBean.OBJECT_NAME })
public class FTController implements FTControllerMBean {

	private boolean trackerStarted = false;
	private Tracker tracker = null;
	private int trackerport = 0;

	public int transferPackage(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrPackageDir) {

		Client initialSeed = null;
		Torrent torrent = null;

		try {
			if (!trackerStarted) {
				startTracker();
			}

			// create torrent
			File srcFile = new File(contrPackageDir + "/" + srcName);
			torrent = Torrent.create(srcFile, new URI("http://" + host + ":"
					+ trackerport + "/announce"), "createdByUser");
			File torrentFile = new File(srcFile.getAbsoluteFile() + ".torrent");
			FileOutputStream fos = new FileOutputStream(torrentFile);
			torrent.save(fos);
			fos.close();
			tracker.announce(new TrackedTorrent(torrent));

			// start initial seed
			initialSeed = new Client(InetAddress.getLocalHost(),
					new SharedTorrent(torrent, new File(contrPackageDir), true));
			initialSeed.share();
			while (!initialSeed.getTorrent().isInitialized()) {
				Thread.sleep(1000);
			}

			// zip up FTClient.jar and .torrent into one file
			createZip(srcName, contrPackageDir, torrentFile);

			// push zip to targets and start transfer
			startTransfer(username, password, contrPackageDir, truststorePass,
					host, port, srcName, destDir, hosts);

			waitUntilTransferDone(initialSeed, hosts);
			int numpeers = numDistinctPeers(initialSeed.getPeers(), true);
			return numpeers;

		} catch (Exception e) {
			e.printStackTrace();
			int numpeers = numDistinctPeers(initialSeed.getPeers(), true);
			return numpeers;
		} finally {
			initialSeed.stop();
			// remove torrent after 20 seconds
			tracker.remove(torrent, 20000);
		}
	}

	private synchronized void startTracker() {
		for (trackerport = 6961; trackerport <= 6969; trackerport++) {
			InetSocketAddress tryAddress = new InetSocketAddress(trackerport);
			try {
				// tests if port is in use. If not, an exception
				// will be thrown
				new Socket(tryAddress.getAddress(), tryAddress.getPort())
						.close();
			} catch (IOException ioe) {
				try {
					tracker = new Tracker(tryAddress);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}
		tracker.start();
		trackerStarted = true;

	}

	private CloseableHttpClient setupHttpClient(String username,
			String password, String contrPackageDir, String truststorePass) {
		// setup HTTPClient
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(username, password));
		SSLContext sslContext = null;

		try {
			sslContext = SSLContexts
					.custom()
					.loadTrustMaterial(
							new File(contrPackageDir + "-config/trust.jks"),
							truststorePass.toCharArray()).build();
		} catch (KeyManagementException | NoSuchAlgorithmException
				| KeyStoreException | CertificateException | IOException e) {
			e.printStackTrace();
		}

		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
				sslContext);
		CloseableHttpClient httpclient = HttpClientBuilder.create()
				.setDefaultCredentialsProvider(credentialsProvider)
				.setSSLSocketFactory(sslsf).build();

		return httpclient;
	}

	private void createZip(String srcName, String contrPackageDir,
			File torrentFile) {

		byte[] buffer = new byte[1024];
		String pkgName = srcName + "package";
		String ftcName = srcName + "FTClient.jar";
		try {
			FileOutputStream zipfos = new FileOutputStream(contrPackageDir
					+ "/" + pkgName + ".zip");
			ZipOutputStream zos = new ZipOutputStream(zipfos);
			ZipEntry ze = new ZipEntry(ftcName);
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(contrPackageDir
					+ "-config" + "/FTClient.jar");

			int len;
			while ((len = in.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}

			in.close();
			zos.closeEntry();

			ZipEntry ze2 = new ZipEntry(srcName + ".torrent");
			zos.putNextEntry(ze2);
			FileInputStream in2 = new FileInputStream(torrentFile);

			while ((len = in2.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}

			in2.close();
			zos.closeEntry();
			zos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startTransfer(String username, String password,
			String contrPackageDir, String truststorePass, String host,
			String port, String srcName, String destDir, String hosts) {

		String pkgName = srcName + "package";
		String ftcName = srcName + "FTClient.jar";

		String destZipPath = destDir + "/" + pkgName;

		String fileTransferURI = "https://" + host + ":" + port
				+ "/IBMJMXConnectorREST/file/";

		try (CloseableHttpClient httpclient = setupHttpClient(username,
				password, contrPackageDir, truststorePass)) {
			HttpPost zipPost = new HttpPost(fileTransferURI
					+ URLEncoder.encode(destZipPath, "utf-8")
					+ "?expandOnCompletion=true");

			zipPost.setEntity(new FileEntity(new File(contrPackageDir + "/"
					+ pkgName + ".zip")));
			zipPost.addHeader("com.ibm.websphere.collective.hostNames", hosts);

			String destFTCPath = destDir + "/" + ftcName;
			String destTorrPath = destDir + "/" + srcName + ".torrent";
			String postCommand1 = "mv " + destDir + "/" + pkgName + "/"
					+ ftcName + " " + destDir + "/" + pkgName + "/" + srcName
					+ ".torrent " + destDir;
			String postCommand2 = "java -jar " + destFTCPath + " "
					+ destTorrPath + " " + destDir + " &";
			String postCommand3 = "rmdir " + destDir + "/" + pkgName;
			zipPost.addHeader(
					"com.ibm.websphere.jmx.connector.rest.postTransferAction",
					postCommand1 + "," + postCommand2 + "," + postCommand3);

			System.out.println("Pushing out .torrent files");
			httpclient.execute(zipPost);
			System.out.println("Done pushing .torrent files");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void waitUntilTransferDone(Client initialSeed, String hosts) {
		long startTransferTime = System.currentTimeMillis();

		// wait up to 5 minutes for all clients to connect
		while (numDistinctPeers(initialSeed.getPeers(), false) < hosts
				.split(",").length 
				&& (System.currentTimeMillis() - startTransferTime) <= 300000) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("All Clients Connected!");

		boolean done = false;
		while (!done) {
			// check every second
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			done = true;
			Set<SharingPeer> peers = initialSeed.getPeers();
			for (SharingPeer p : peers) {
				if (p.isConnected() && !p.isSeed()) {
					done = false;
					break;
				}
			}
		}
	}

	private int numDistinctPeers(Set<SharingPeer> peers, Boolean reqDone) {
		int count = 0;
		HashSet<String> hs = new HashSet<String>();
		// //debugging
		// try {
		// FileWriter fw = new FileWriter(new
		// File("/home/ibmadmin/torrent/peerStatus"), true);
		// BufferedWriter bw = new BufferedWriter(fw);
		// for (SharingPeer p : peers) {
		// bw.write(p.getHostIdentifier() + "\t" + p.isConnected() + "\t" +
		// p.isSeed() + "\t" + p.isDownloading() + "\n");
		// }
		// bw.write("\n\n");
		// bw.flush();
		// bw.close();
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		if (reqDone) {
			for (SharingPeer p : peers) {
				if (!hs.contains(p.getIp()) && p.isSeed()) {
					count++;
					hs.add(p.getIp());
				}
			}
			//self will never be included
			return count;
		} else {
			for (SharingPeer p : peers) {
				if (!hs.contains(p.getIp())) {
					count++;
				}
			}
			//subtract one to exclude self
			return count - 1;
		}
	}

	public void cleanPackageDir(String contrPackageDir) {
		try {
			FileUtils.cleanDirectory(new File(contrPackageDir));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
