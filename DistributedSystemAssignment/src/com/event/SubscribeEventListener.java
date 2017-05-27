package com.event;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.host.Host;
import com.interfaces.ISubscribeEventListener;
import com.resource.Resource;
import com.resource.ResourceKey;
import com.util.Util;

//listen to SubscribeEvent. Return matched resource which is contained in event to the client when an event happends 
public class SubscribeEventListener implements ISubscribeEventListener{
	private JSONObject clientCommand = null;
	private DataOutputStream output = null;
	public int resultSize = 0;
	List<Thread> serverConnThreads = new ArrayList<Thread>();
	
	public SubscribeEventListener(JSONObject clientCommand,DataOutputStream out){
		this.clientCommand = clientCommand;
		this.output = out;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void mactchTemplate(Resource res){
		JSONObject resourceTemplate = (JSONObject)clientCommand.get("resourceTemplate");
		LinkedList<String> tags = new LinkedList<String>();
		URI uri = null;
		String name = "", description = "", channel = "", owner = "";
		
		// get name from resourceTemplate
		if (resourceTemplate.containsKey("name")) {
			name = resourceTemplate.get("name").toString();
			name = Util.clean(name);
		}
		// get tags from resourceTemplate
		if (resourceTemplate.containsKey("tags")) {
			JSONArray tag_array = (JSONArray) resourceTemplate.get("tags");
			for (int i = 0; i < tag_array.size(); i++) {
				String tag = tag_array.get(i).toString();
				tag = Util.clean(tag);
				tags.add(tag);
			}
		}
		// get description from resourceTemplate
		if (resourceTemplate.containsKey("description")) {
			description = resourceTemplate.get("description").toString();
			description = Util.clean(description);
		}
		/** get uri from resourceTemplate */
		try {
			if (resourceTemplate.containsKey("uri"))
				uri = new URI(resourceTemplate.get("uri").toString());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		/** get channel from resourceTemplate */
		if (resourceTemplate.containsKey("channel")) {
			channel = resourceTemplate.get("channel").toString();
			channel = Util.clean(channel);
		}
		/** get owner from resourceTemplate */
		if (resourceTemplate.containsKey("owner")) {
			owner = resourceTemplate.get("owner").toString();
			owner = Util.clean(owner);
		}
		
		if (channel.equals(res.getChannel()) && (owner.equals("") || owner.equals(res.getOwner()))
				&& (uri.toString().equals("") || uri.equals(res.getUri()))) {
			// to match the tags
			if (tags.size() != 0) {
				boolean flag = false;
				for (String tag : tags) {
					flag = false;
					for (String val_tag : res.getTags()) {
						if (tag.equals(val_tag)) {
							flag = true;
							break;
						}
					}
					if (flag == true) {
						continue;
					} else {
						break;
					}
				}
				if (!flag) {
					return;
				}
			}
			if (name.equals("") && description.equals("") || !name.equals("") && res.getName().indexOf(name) != -1
					|| description.equals("") && res.getDescription().indexOf(description) != -1) {
				JSONObject resource = new JSONObject();
				resource = res.getResourceJSON();
				if (!resource.get("owner").equals("")) {
					resource.put("owner", "*");
				}
				sendToClient(resource.toJSONString());
			}
		}
	}
	
	@Override
	public void subscribeToServer(Host ht){
		SubscriberToServer serverConnTread = new SubscriberToServer(ht,false);
		serverConnThreads.add(serverConnTread);
		serverConnTread.start();
	}
	
	public void unsubscribeAllServers(){
		for(Thread serverConnThread : serverConnThreads){
			serverConnThread.interrupt();
		}
	}
	
	private synchronized void sendToClient(String result){
		try{
			output.writeUTF(result);
			output.flush();
			resultSize++;
		}catch(IOException e){
			System.out.println(e.toString());
		}
	}
	
	private class SubscriberToServer extends Thread{
		
		Host ht;
		boolean secure = false;
		
		public SubscriberToServer(Host ht, boolean secure){
			this.ht = ht;
			this.secure = secure;
		}
		
		//subsctibe to server ht and return hitting resource back to client
		@SuppressWarnings("unchecked")
		@Override
		public void run(){
			Socket socket = null;
			try{
				if(secure == true){
					System.setProperty("javax.net.ssl.keyStore","serverKeystore/ServerKeyStore");
					System.setProperty("javax.net.ssl.keyStorePassword","123456");
					System.setProperty("javax.net.ssl.trustStore","serverKeystore/ServerKeyStore");
					System.setProperty("javax.net.ssl.trustStorePassword","123456");
					
					SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
					socket = (SSLSocket) sslsocketfactory.createSocket(ht.getHostname(), ht.getPort());
		            
		        }else{
					socket = new Socket(ht.getHostname(),ht.getPort());
				}
				
				DataOutputStream serverOutput = new DataOutputStream(socket.getOutputStream());
				DataInputStream serverInput = new DataInputStream(socket.getInputStream());

				clientCommand.put("relay", "false");
				serverOutput.writeUTF(clientCommand.toJSONString());
				serverOutput.flush();
				
				JSONParser parser = new JSONParser();
				JSONObject reply =  (JSONObject) parser.parse(serverInput.readUTF().toString());
				if(reply.containsKey("response")){
					if("success".equals( reply.get("response").toString())){
						String result ;
						while(!Thread.currentThread().isInterrupted()){
							result = serverInput.readUTF().toString();
							sendToClient(result);
						}
						String unsubscribe = "{\"command\":\"UNSUBSCRIBE\",\"id\":\"" + (String)clientCommand.get("id") + "\"}";
						serverOutput.writeUTF(unsubscribe);
						serverOutput.flush();
					}
					else if("error".equals(reply.get("response").toString())){
						System.out.println(ht.getHostname()+":"+ht.getPort()+" "+reply.get("errorMessage").toString());
					}
				}
				
				if(serverOutput!=null)
					serverOutput.close();
				if(serverInput!=null)
					serverInput.close();
			}catch(IOException e){
				System.out.println(e.toString());
			}catch (ParseException e) {
				System.out.println(e.toString());
			}finally{
				try {
					if(socket!=null)
						socket.close();
				} catch (IOException e) {
					System.out.println(e.toString());
				}
			}
		}
	}
}
