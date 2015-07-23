package main.java.com.zheng.torrentclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Set;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer;

public class TorrentClient {

	public static void main(String[] args) {
		File torrentFile = new File(args[0]);
		File destDir = new File(args[1]);

		try {
			Client client = new Client(InetAddress.getLocalHost(),
					SharedTorrent.fromFile(torrentFile, destDir));
			
			String trackerhost = client.getTorrent().getAnnounceList().get(0).get(0).getHost();
			String trackerip = InetAddress.getByName(trackerhost).getHostAddress();
			client.share();

			// done when seed on tracker host is disconnected
			while(!client.getTorrent().isInitialized()){
				Thread.sleep(1000);
			}
			Thread.sleep(5000);
			
			while (true) {
				// check every second
				Thread.sleep(1000);
				Set<SharingPeer> peers = client.getPeers();
				if(peers.isEmpty() || peers == null){
					client.stop();
					System.exit(0);
				}
				for(SharingPeer p : peers){
					if(p.getIp().equals(trackerip) && !p.isConnected()){
						client.stop();
						System.exit(0);
					}
				}
			}

		} catch (Exception e) {
			try {
				File error = new File("error");
				FileWriter fw = new FileWriter(error);
				BufferedWriter bw = new BufferedWriter(fw);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				bw.write(sw.toString());
				bw.close();
				pw.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.println("HEY HI!");
			System.exit(0);
		}
	}
}
