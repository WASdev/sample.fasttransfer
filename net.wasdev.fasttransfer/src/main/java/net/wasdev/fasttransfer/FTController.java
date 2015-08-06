package net.wasdev.fasttransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
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

	private Object lock = new Object();
	private boolean trackerStarted = false;
	private Tracker tracker = null;
	private int trackerport = 0;

	public int transferPackage(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrPackageDir) {

		Client initialSeed = null;
		Torrent torrent = null;

		try {
			synchronized (lock) {
				if (!trackerStarted) {
					for (trackerport = 6961; trackerport <= 6969; trackerport++) {
						InetSocketAddress tryAddress = new InetSocketAddress(
								trackerport);
						try {
							// tests if port is in use. If not, an exception
							// will be thrown
							new Socket(tryAddress.getAddress(),
									tryAddress.getPort()).close();
						} catch (IOException ioe) {
							tracker = new Tracker(tryAddress);
							break;
						}
					}
					tracker.start();
					trackerStarted = true;
				}
			}

			File srcFile = new File(contrPackageDir + "/" + srcName);
			torrent = Torrent.create(srcFile, new URI("http://" + host + ":"
					+ trackerport + "/announce"), "createdByUser");
			File torrentFile = new File(srcFile.getAbsoluteFile() + ".torrent");
			FileOutputStream fos = new FileOutputStream(torrentFile);
			torrent.save(fos);
			fos.close();
			tracker.announce(new TrackedTorrent(torrent));

			initialSeed = new Client(InetAddress.getLocalHost(),
					new SharedTorrent(torrent, new File(contrPackageDir), true));
			initialSeed.share();
			while (!initialSeed.getTorrent().isInitialized()) {
				Thread.sleep(1000);
			}

			// setup HTTPClient
			CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY,
					new UsernamePasswordCredentials(username, password));
			SSLContext sslContext = null;

			sslContext = SSLContexts
					.custom()
					.loadTrustMaterial(
							new File(contrPackageDir + "-config/trust.jks"),
							truststorePass.toCharArray()).build();

			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
					sslContext);
			CloseableHttpClient httpclient = HttpClientBuilder.create()
					.setDefaultCredentialsProvider(credentialsProvider)
					.setSSLSocketFactory(sslsf).build();

			// zip up FTClient.jar and .torrent into one file
			byte[] buffer = new byte[1024];
			String pkgName = srcName + "package";
			String tcName = srcName + "FTClient.jar";
			FileOutputStream zipfos = new FileOutputStream(contrPackageDir + "/"
					+ pkgName + ".zip");
			ZipOutputStream zos = new ZipOutputStream(zipfos);
			ZipEntry ze = new ZipEntry(tcName);
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(contrPackageDir + "-config"
					+ "/FTClient.jar");

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

			// push zip to targets
			String destZipPath = destDir + "/" + pkgName;

			String fileTransferURI = "https://" + host + ":" + port
					+ "/IBMJMXConnectorREST/file/";

			HttpPost zipPost = new HttpPost(fileTransferURI
					+ URLEncoder.encode(destZipPath, "utf-8")
					+ "?expandOnCompletion=true");

			zipPost.setEntity(new FileEntity(new File(contrPackageDir + "/"
					+ pkgName + ".zip")));
			zipPost.addHeader("com.ibm.websphere.collective.hostNames", hosts);

			String destTCPath = destDir + "/" + tcName;
			String destTorrPath = destDir + "/" + srcName + ".torrent";
			String postCommand1 = "mv " + destDir + "/" + pkgName + "/"
					+ tcName + " " + destDir + "/" + pkgName + "/" + srcName
					+ ".torrent " + destDir;
			String postCommand2 = "java -jar " + destTCPath + " "
					+ destTorrPath + " " + destDir + " &";
			String postCommand3 = "rmdir " + destDir + "/" + pkgName;
			zipPost.addHeader(
					"com.ibm.websphere.jmx.connector.rest.postTransferAction",
					postCommand1 + "," + postCommand2 + "," + postCommand3);

			System.out.println("Pushing out .torrent files");
			httpclient.execute(zipPost);
			System.out.println("Done pushing .torrent files");
			httpclient.close();

			long startTorrentTime = System.currentTimeMillis();

			// wait up to 5 minutes for all clients to connect
			while (numDistinctPeers(initialSeed.getPeers()) < hosts.split(",").length + 1
					&& (System.currentTimeMillis() - startTorrentTime) <= 300000) {
				Thread.sleep(1000);
			}
			System.out.println("All Clients Connected!");

			boolean done = false;
			while (!done) {
				// check every second
				Thread.sleep(1000);
				done = true;
				Set<SharingPeer> peers = initialSeed.getPeers();
				numDistinctPeers(peers);
				for (SharingPeer p : peers) {
					if (p.isConnected() && !p.isSeed()) {
						done = false;
						break;
					}
				}
			}
			int numpeers = numDistinctPeers(initialSeed.getPeers()) - 1;
			return numpeers;

		} catch (Exception e) {
			e.printStackTrace();
			int numpeers = numDistinctPeers(initialSeed.getPeers()) - 1;
			return numpeers;
		} finally {
			initialSeed.stop();
			// remove torrent after 20 seconds
			tracker.remove(torrent, 20000);
		}
	}

	private int numDistinctPeers(Set<SharingPeer> peers) {
		HashSet<String> hs = new HashSet<String>();
		for (SharingPeer p : peers) {
			hs.add(p.getIp());
		}
		return hs.size();
	}

	public void cleanPackageDir(String contrPackageDir) {
		try {
			FileUtils.cleanDirectory(new File(contrPackageDir));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
