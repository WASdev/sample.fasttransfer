package net.wasdev.fasttransfer;

public interface FTControllerMBean {
	static final java.lang.String OBJECT_NAME = "net.wasdev:feature=FastTransferFeature,type=FastTransfer,name=FastTransfer";

	public int transferTorrent(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrTorrDir);

	public void cleanTorrentDir(String contrTorrDir);

}
