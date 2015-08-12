package net.wasdev.fasttransfer;

import java.util.HashSet;

public interface FTControllerMBean {
	static final java.lang.String OBJECT_NAME = "net.wasdev:feature=FastTransferFeature,type=FastTransfer,name=FastTransfer";

	public HashSet<String> transferPackage(String srcName, String destDir, String hosts,
			String username, String password, String truststorePass,
			String host, String port, String contrPackageDir);

	public void cleanPackageDir(String contrPackageDir);

}
