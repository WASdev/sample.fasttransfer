package net.wasdev.ftclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;

public class FTClient {

	// client for downloading and uploading torrent
	private static Client client = null;
	private static File torrentFile = null;

	public static void main(String[] args) {
		torrentFile = new File(args[0]);
		File destDir = new File(args[1]);
		int numhosts = Integer.parseInt(args[2]);

		try {
			client = new Client(InetAddress.getLocalHost(),
					SharedTorrent.fromFile(torrentFile, destDir));

			String trackerhost = client.getTorrent().getAnnounceList().get(0)
					.get(0).getHost();
			String trackerip = InetAddress.getByName(trackerhost)
					.getHostAddress();
			// start downloading and uploading
			client.share();

			long timeStart = System.currentTimeMillis();

			while (!client.getTorrent().isInitialized()
					|| (numDistinctPeers(client.getPeers()) < numhosts && System
							.currentTimeMillis() - timeStart <= numhosts * 5000)) {
				Thread.sleep(1000);
			}

			// done when client on tracker host is disconnected
			while (true) {
				// check every second
				Thread.sleep(1000);
				Set<SharingPeer> peers = client.getPeers();
				if ((peers.isEmpty() || peers == null)) {
					cleanup();
				}
				boolean initSeedExists = false;
				for (SharingPeer p : peers) {
					if (p.getIp().equals(trackerip)) {
						initSeedExists = true;
						if (!p.isConnected()) {
							cleanup();
						}
					}
				}
				if (!initSeedExists) {
					cleanup();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			cleanup();
		}
	}

	// deletes the torrentFile and this jar
	private static void cleanup() {
		try {
			client.stop();
			torrentFile.delete();
			// write bash file that deletes the jar and then itself
			String jarpath = FTClient.class.getProtectionDomain()
					.getCodeSource().getLocation().getPath();
			String decodedJarPath = URLDecoder.decode(jarpath, "UTF-8");
			FileWriter fw = new FileWriter(new File("rmjar.sh"));
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("#!/bin/bash\nsleep 2\nrm " + decodedJarPath
					+ "\nrm rmjar.sh\n");
			bw.close();
			Runtime.getRuntime().exec("sh rmjar.sh");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	private static int numDistinctPeers(Set<SharingPeer> peers) {
		HashSet<String> hs = new HashSet<String>();
		for (SharingPeer p : peers) {
			hs.add(p.getIp());
		}
		return hs.size();
	}
}
