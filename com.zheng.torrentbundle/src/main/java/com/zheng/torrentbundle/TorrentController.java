package main.java.com.zheng.torrentbundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
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

@Component(name = "TorrentController", service = { TorrentControllerMBean.class }, immediate = true, property = { "jmx.objectname="
		+ TorrentControllerMBean.OBJECT_NAME })
public class TorrentController implements TorrentControllerMBean {

	public void transferTorrent(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrTorrDir) {

		try {

			// create .torrent file
			Tracker tracker = new Tracker(new InetSocketAddress(6960));
			File srcFile = new File(contrTorrDir + "/" + srcName);
			Torrent torrent = Torrent.create(srcFile, new URI("http://" + host
					+ ":6960/announce"), "createdByUser");
			File torrentFile = new File(srcFile.getAbsoluteFile() + ".torrent");
			FileOutputStream fos = new FileOutputStream(torrentFile);
			torrent.save(fos);
			fos.close();

			// start tracker and initial seed
			tracker.announce(new TrackedTorrent(torrent));
			tracker.start();
			Client initialSeed = new Client(InetAddress.getLocalHost(),
					new SharedTorrent(torrent, new File(contrTorrDir), true));
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
							new File(contrTorrDir + "-config/trust.jks"),
							truststorePass.toCharArray()).build();

			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
					sslContext);
			CloseableHttpClient httpclient = HttpClientBuilder.create()
					.setDefaultCredentialsProvider(credentialsProvider)
					.setSSLSocketFactory(sslsf).build();

			// zip up TorrentClient.jar and .torrent into one file
			byte[] buffer = new byte[1024];

			FileOutputStream zipfos = new FileOutputStream(contrTorrDir
					+ "/package.zip");
			ZipOutputStream zos = new ZipOutputStream(zipfos);
			ZipEntry ze = new ZipEntry("TorrentClient.jar");
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(contrTorrDir + "-config"
					+ "/TorrentClient.jar");

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
			String destZipPath = destDir + "/package";

			String fileTransferURI = "https://" + host + ":" + port
					+ "/IBMJMXConnectorREST/file/";

			HttpPost zipPost = new HttpPost(fileTransferURI
					+ URLEncoder.encode(destZipPath, "utf-8")
					+ "?expandOnCompletion=true");

			zipPost.setEntity(new FileEntity(new File(contrTorrDir
					+ "/package.zip")));
			zipPost.addHeader("com.ibm.websphere.collective.hostNames", hosts);

			String destTCPath = destDir + "/TorrentClient.jar";
			String destTorrPath = destDir + "/" + srcName + ".torrent";
			String postCommand1 = "mv " + destDir
					+ "/package/TorrentClient.jar " + destDir + "/package/"
					+ srcName + ".torrent " + destDir;
			String postCommand2 = "java -jar " + destTCPath + " "
					+ destTorrPath + " " + destDir + " &";
			String postCommand3 = "rmdir " + destDir + "/package";
			zipPost.addHeader(
					"com.ibm.websphere.jmx.connector.rest.postTransferAction",
					postCommand1 + "," + postCommand2 + "," + postCommand3);

			System.out.println("Pushing out .torrent files");
			httpclient.execute(zipPost);
			System.out.println("Done pushing .torrent files");
			httpclient.close();

			long startTorrentTime = System.currentTimeMillis();

			// wait up to 5 minutes for all clients to connect
			while (initialSeed.getPeers().size() < hosts.split(",").length
					&& (System.currentTimeMillis() - startTorrentTime) <= 300000) {
				Thread.sleep(1000);
			}

			// stop when all torrent clients are seeding or disconnected
			boolean done = false;
			while (!done) {
				// check every second
				Thread.sleep(1000);
				done = true;
				Set<SharingPeer> peers = initialSeed.getPeers();
				for (SharingPeer p : peers) {
					if (p.isConnected() && !p.isSeed()) {
						done = false;
						break;
					}
				}
			}

			initialSeed.stop();
			tracker.remove(torrent);
			tracker.stop();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void cleanTorrentDir(String contrTorrDir) {
		try {
			FileUtils.cleanDirectory(new File(contrTorrDir));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
