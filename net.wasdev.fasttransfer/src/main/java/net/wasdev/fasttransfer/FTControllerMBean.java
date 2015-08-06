package net.wasdev.fasttransfer;

public interface FTControllerMBean {
	static final java.lang.String OBJECT_NAME = "net.wasdev:feature=FastTransferFeature,type=FastTransfer,name=FastTransfer";

	public int transferPackage(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrPackageDir);

	public void cleanPackageDir(String contrPackageDir);

}
