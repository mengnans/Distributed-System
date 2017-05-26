package com.host;

import org.json.simple.JSONObject;

/**
 * 
 * @author Group NullPointerException
 * 		Mengnan Shi		802123
 * 		Hanwei Zhu		811443
 * 		Xuelin Zhao		801736
 * 		Hangyu Xia		802971
 *
 */
public class Host {
	String hostname;
	int port;
	
	public Host(String hostname,int port){
		setPort(port);
		setHostname(hostname);
	}
	
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public String getHostname() {
		return hostname;
	}
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject getHostJSON() {
		JSONObject hostJson = new JSONObject();
		hostJson.put("hostname", getHostname());
		hostJson.put("port", getPort() + "");
		return hostJson;
	}
	
	public boolean equals(Host host){
		if(this.hostname.equals(host.getHostname())&&(this.getPort()== host.getPort())){
			return true;
		}else{
			return false;
		}
		
	}
	
	@Override
	public String toString() {
		return hostname + ":" + port;
	}
}
