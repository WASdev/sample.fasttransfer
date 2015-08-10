package net.wasdev.ftadmin;

public class Config {
	private String username;
	private String password;
	private String truststorePath;
	private String truststorePass;
	private String host;
	private String port;
	private String contrPackageDir;
	private Boolean clearCPD;

	public Config() {
		super();
		this.username = null;
		this.password = null;
		this.truststorePath = null;
		this.truststorePass = null;
		this.host = null;
		this.port = null;
		this.contrPackageDir = null;
		this.clearCPD = null;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getTruststorePath() {
		return truststorePath;
	}

	public void setTruststorePath(String truststorePath) {
		this.truststorePath = truststorePath;
	}

	public String getTruststorePass() {
		return truststorePass;
	}

	public void setTruststorePass(String truststorePass) {
		this.truststorePass = truststorePass;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getContrPackageDir() {
		return contrPackageDir;
	}

	public void setContrPackageDir(String contrPackageDir) {
		this.contrPackageDir = contrPackageDir;
	}

	public Boolean getClearCPD() {
		return clearCPD;
	}

	public void setClearCPD(Boolean clearCPD) {
		this.clearCPD = clearCPD;
	}

}
