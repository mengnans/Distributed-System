package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.log4j.Logger;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * 
 * @author Group NullPointerException
 * 		Mengnan Shi		802123
 * 		Hanwei Zhu		811443
 * 		Xuelin Zhao		801736
 * 		Hangyu Xia		802971
 *
 */
public class Client {
	// IP and port
	private static String ip = "localhost";
	private static int port = 33254;
	
	//log4j Logger
	private static Logger logger = Logger.getLogger(Client.class);
	private static boolean debug= false;
	private static boolean secure =false;
	
	private static boolean isUriEmpty=false;
	
	private static DataInputStream input = null;
	private static DataOutputStream output = null;
	private static Socket socket = null;
	
	public static void main(String[] args) {
		try {
			/** Get JSONObject and set IP and port*/
			//JSONObject request = getRandomRequest();
			JSONObject request = getRequestFromCommandLine(args);
			
			
			if(debug)
				logger.info("[INFO] - setting debug on");
			if(request.containsKey("command")){
				String command = (String)request.get("command");
				if(debug){
					logger.info("[FINE] - " + command.toLowerCase() + " " + ip + ":" + port);
					logger.info("[FINE] - " + command.toUpperCase() + ":" + request.toJSONString());
				}
			}
			if(secure){
				System.setProperty("javax.net.ssl.keyStore","clientKeystore/ClientKeyStore");
				System.setProperty("javax.net.ssl.keyStorePassword","123456");
				System.setProperty("javax.net.ssl.trustStore","clientKeystore/ClientKeyStore");
				System.setProperty("javax.net.ssl.trustStorePassword","123456");
				
				SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
				socket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			}else{
				socket = new Socket(ip, port);
			}
			// Output and Input Stream
			input = new DataInputStream(socket.getInputStream());
			output = new DataOutputStream(socket.getOutputStream());
			output.writeUTF(request.toJSONString());
			output.flush();
			
			String command = (String) request.get("command");

			/** if command is fetch, it will jump to different logic part*/
			if ("FETCH".equals(command)) {
				getFetchReponse(socket);
				return;
			}

			JSONParser parser = new JSONParser();
			//Subscribe to the server until Enter is pressed.
			if("SUBSCRIBE".equals( request.get("command").toString())){
				JSONObject result = (JSONObject) parser.parse(input.readUTF().toString());
				if(debug)
					logger.info("[FINE] - RECEIVED: " + result.toJSONString());
				
				if (result.containsKey("response")) {
					if("success".equals(result.get("response").toString())){
						System.out.println("subscribe success!");
						Thread persistentConn = new Thread( () -> waitForEnter(output, (String)request.get("id")));
						persistentConn.start();
						
						while(true){
							JSONObject resource = (JSONObject) parser.parse(input.readUTF().toString());
							if(debug)
								logger.info("[FINE] - RECEIVED: " + resource.toJSONString());
							System.out.println(resource);
							
							if(resource.containsKey("resultSize"))
								return;
						}
					}
				}
				return;
			}
			
			//other commands
			while (true) {
				/** receive JSONObject */
				try{
					JSONObject result = (JSONObject) parser.parse(input.readUTF().toString());
					
					if (result.containsKey("response")) {
						if(debug)
							logger.info("[FINE] - RECEIVED: " + result.toJSONString());

						String response = (String) result.get("response");
						if("success".equals(response)){
							System.out.println( (String)request.get("command")+" succeed");
						}
						else if ("error".equals(response)) {
							System.out.println( (String)request.get("command")+" failed");
							String errorMessage = (String) result.get("errorMessage");
							System.out.println(errorMessage);
						}
					}
					else
						System.out.println(result.toString());
				}
				catch (EOFException e) {
					break;
				}
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("connection refused!");
		}catch (ParseException e) {
			e.printStackTrace();
		}finally {
			try {
				if (input != null)
					input.close();
				if (output != null)
					output.close();
				if (socket != null) {
					socket.close();
				}
			} catch (IOException e) {
			}
		}
	}
	
	private static void waitForEnter(DataOutputStream output, String id){
		Scanner scanner = new Scanner(System.in);
		scanner.nextLine();
		scanner.close();
		
		String unsubscribe = "{\"command\":\"UNSUBSCRIBE\",\"id\":\"" + id + "\"}";
		
		try{
			output.writeUTF(unsubscribe);
			output.flush();
			logger.info("[FINE] - SEND: " + unsubscribe);
		}catch(IOException e){
			System.out.println(e.toString());
		}
	}
	
	private static void getFetchReponse(Socket socket) {
		DataInputStream input = null;
		DataOutputStream output = null;
		try {
			input = new DataInputStream(socket.getInputStream());
			output = new DataOutputStream(socket.getOutputStream());
			long fileSizeRemaining = 0;
			JSONParser parser = new JSONParser();
			URI uri = null;
			for (int i = 0; i < 2; i++) {
				/** receive JSONObject */
				String readLine = input.readUTF().toString();
				JSONObject result = (JSONObject) parser.parse(readLine);
				if(debug)
					logger.info("[FINE] - RECEIVED: " + result.toJSONString());
				System.out.println(readLine);
				if(result.containsKey("uri")){
					uri = new URI(result.get("uri").toString());
				}
				if (result.containsKey("resourceSize")) {
					fileSizeRemaining = (Long) result.get("resourceSize");
				}
				
				if(result.containsKey("resultSize")){
					break;
				}
			}

			String path = uri.getPath();
			String fileName = path.substring(path.lastIndexOf("/") + 1, path.length());

			// Create a RandomAccessFile to read and write the output file.
			RandomAccessFile downloadingFile = new RandomAccessFile(fileName, "rw");

			while (true) {
				int chunkSize = setChunkSize(fileSizeRemaining);

				// Represents the receiving buffer
				byte[] receiveBuffer = new byte[chunkSize];
				// Variable used to read if there are remaining size left to
				// read.
				int num;

				System.out.println("Downloading " + fileName + " of size " + fileSizeRemaining);
				while ((num = input.read(receiveBuffer)) > 0) {
					// Write the received bytes into the RandomAccessFile
					downloadingFile.write(Arrays.copyOf(receiveBuffer, num));

					// Reduce the file size left to read..
					fileSizeRemaining -= num;

					// Set the chunkSize again
					chunkSize = setChunkSize(fileSizeRemaining);
					receiveBuffer = new byte[chunkSize];

					// If you're done then break
					if (fileSizeRemaining == 0) {
						break;
					}
				}
				if (fileSizeRemaining == 0) {
					downloadingFile.close();
					break;
				}
			}

			String readLine = input.readUTF().toString();
			if(debug)
				logger.info("[FINE] - RECEIVED: " + readLine);
			System.out.println(readLine);

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("connection refused!");
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if (input != null)
					input.close();
				if (output != null)
					output.close();
				if (socket != null) {
					socket.close();
				}
				System.out.println("File received!");
			} catch (IOException e) {
			}
		}
	}

	private static int setChunkSize(long fileSizeRemaining) {
		// Determine the chunkSize
		int chunkSize = 1024 * 1024;

		// If the file size remaining is less than the chunk size
		// then set the chunk size to be equal to the file size.
		if (fileSizeRemaining < chunkSize) {
			chunkSize = (int) fileSizeRemaining;
		}

		return chunkSize;
	}
	
	public static void printHelpInfo(){
		System.out.println("usage:Client [-channel <arg>][-debug][-description <arg>]");
		System.out.println("      [-exchange][-fetch][-host <arg>][-name <arg>][-owner <arg>]");
		System.out.println("      [-port <arg>][-publish][-query][-remove][-secret <arg>]");
		System.out.println("      [-server <arg>][-share][-tags <arg>][-name <arg>][-uri <arg>]");
		System.out.println("Client for Unimelb COMP90015");
		System.out.println();
		System.out.println("-channel <arg>			channel");
		System.out.println("-debug					print debug information");
		System.out.println("-description <arg>		resource description");
		System.out.println("-exchange				exchange server list with server");
		System.out.println("-fetch					fetch resources from server");
		System.out.println("-host <arg>				server host, a domain name or IP address");
		System.out.println("-name <arg>				resource name");
		System.out.println("-owner <arg>			owner");
		System.out.println("-port <arg>				server port, an integer");
		System.out.println("-publish				publish resource on server");
		System.out.println("-query					query for resources from server");
		System.out.println("-remove					remove resource from server");
		System.out.println("-secret <arg>			secret");
		System.out.println("-servers <arg>			server list, host1:port1,host2:port2,...");
		System.out.println("-share					share resource on server");
		System.out.println("-tags <arg>				resource tags, tag1,tag2,tag3,...");
		System.out.println("-uri <arg>				resource URI");
		System.out.println("-subcribe 				subscribe to the server");
	}
	
	public static JSONObject getRequestFromCommandLine(String[] args){
		Options options = new Options();
		/**request options*/
		options.addOption("help", "show all the options on client");
		options.addOption("publish", "publish resource on server");
		options.addOption("remove", "remove resource from server");
		options.addOption("query", "query for resources from server");
		options.addOption("share", "share resource on server");
		options.addOption("fetch", "fetch resources from server");
		options.addOption("exchange", "exchange server list with server");
		options.addOption("debug", "print debug information");
		options.addOption("secure", "whether secure or not");
		options.addOption("subscribe", "subscribe to the server");
		/**parameters for options*/
		options.addOption("name", true, "resource name");
		options.addOption("description", true, "resource description");
		options.addOption("tags", true, "resource tags, tag1,tag2,tag3,...");
		options.addOption("uri", true, "resource URI");
		options.addOption("channel", true, "channel");
		options.addOption("owner", true, "owner");
		options.addOption("host", true, "server host, a domain name or IP address");
		options.addOption("port", true, "server port, and Integer");
		options.addOption("secret", true, "secret");
		options.addOption("servers", true, "server list, host1:port1,host2:port2,...");
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try{
			cmd = parser.parse(options,args);
			
			/**print help information*/
			if(cmd.hasOption("help")){
				printHelpInfo();
				System.exit(-1);
			}
			
			/**check debug mode*/
			if(cmd.hasOption("debug")){
				debug=true;
			}
			
			/**enter secure mode*/
			if(cmd.hasOption("secure")){
				secure=true;
				port = 3781;
			}
			
			/**get server host and port*/
			if(cmd.hasOption("host")){
				ip = cmd.getOptionValue("host");
			}
			if(cmd.hasOption("port")){
				port = Integer.parseInt( cmd.getOptionValue("port"));
			}
		}
		catch (org.apache.commons.cli.ParseException e){
			e.printStackTrace();
		}
		
		/**construct a request JSONObject by parsing command line parameters*/
		JSONObject requestJO=getRequestJO(cmd);
		return requestJO;
	}
	
	@SuppressWarnings("unchecked")
	public static JSONObject getRequestJO(CommandLine cmd){
		/**get a resource JSONObject*/
		JSONObject resource=getResourceJO(cmd);
		
		/**construct a request JSONObject for different options*/
		JSONObject request = new JSONObject();
		if(cmd.hasOption("publish")){
			request.put("command", "PUBLISH");
			if(isUriEmpty)
				resource.remove("uri");
			request.put("resource", resource);
		}
		else if(cmd.hasOption("remove")){
			request.put("command", "REMOVE");
			request.put("resource", resource);
		}
		else if(cmd.hasOption("query")){
			request.put("command", "QUERY");
			request.put("relay", "true");
			request.put("resourceTemplate", resource);
		}
		else if(cmd.hasOption("fetch")){
			request.put("command", "FETCH");
			request.put("resourceTemplate", resource);
		}
		else if(cmd.hasOption("share")){
			request.put("command", "SHARE");
			String secret;
			if(cmd.hasOption("secret"))
				secret=cmd.getOptionValue("secret");
			else
				secret="";
			request.put("secret", secret);
			
			if(isUriEmpty)
				resource.remove("uri");
			request.put("resource", resource);
		}
		else if(cmd.hasOption("exchange")){
			request.put("command", "EXCHANGE");
			//parse servers information(host:port set) to a JASONArray
			JSONArray serverListJA = getServerListJA(cmd);
			request.put("serverList", serverListJA);
		}
		else if(cmd.hasOption("subscribe")){
			request.put("command", "SUBSCRIBE");
			request.put("relay", "true");
			request.put("id", getRandomIdString());
			request.put("resourceTemplate", resource);
		}
		else if(cmd.hasOption("unsubscribe")){
			request.put("command", "UNSUBSCRIBE");
		}
		else{
			System.out.println("Error: Request mush have a command:[-help][-publish|-remove|-query|-fetch|-share|"
					+ "-exchange|-debug]");
			System.exit(-1);
		}
		
		return request;
	}
	
	public static String getRandomIdString(){
		String id = "";
		Random random = new Random();
		for (int i = 0; i < 16; i++) {
			String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num";
			if ("char".equalsIgnoreCase(charOrNum)) {
				int temp = random.nextInt(2) % 2 == 0 ? 65 : 97;
				id += (char) (random.nextInt(26) + temp);
			} else if ("num".equalsIgnoreCase(charOrNum)) {
				id += String.valueOf(random.nextInt(10));
			}
		}
		return id;
	}

	@SuppressWarnings("unchecked")
	public static JSONObject getResourceJO(CommandLine cmd){
		/**get resource information from command line*/
		String name,description,tags,uri,channel,owner,ezserver;
		if(cmd.hasOption("name"))
			name=cmd.getOptionValue("name");
		else
			name="";
		if(cmd.hasOption("description"))
			description=cmd.getOptionValue("description");
		else
			description="";
		if(cmd.hasOption("uri")){
			uri=cmd.getOptionValue("uri");
		}
		else{
			uri="";
			isUriEmpty=true;
		}
		if(cmd.hasOption("tags"))
			tags=cmd.getOptionValue("tags");
		else
			tags="";
		if(cmd.hasOption("channel"))
			channel=cmd.getOptionValue("channel");
		else
			channel="";
		if(cmd.hasOption("owner"))
			owner=cmd.getOptionValue("owner");
		else
			owner="";
		ezserver="null";
		
		/**build a resource JSONObject*/
		JSONObject resource = new JSONObject();
		resource.put("name", name);
		resource.put("description", description);
		JSONArray jsonTags = new JSONArray();
		if(tags!=""){
			String[] tagArr = tags.split(",");
			for(int i=0;i<tagArr.length;i++){
				jsonTags.add(tagArr[i]);
			}
		}
		resource.put("tags", jsonTags);
		
		resource.put("uri", uri);
		resource.put("channel", channel);
		resource.put("owner", owner);
		resource.put("ezserver", ezserver);
		
		return resource;
	}
	
	@SuppressWarnings("unchecked")
	/**parse servers information(host:port set) to a JASONArray*/
	public static JSONArray getServerListJA(CommandLine cmd){
		JSONArray serverListJA = new JSONArray();
		
		String servers="";
		if(cmd.hasOption("servers"))
			servers=cmd.getOptionValue("servers");
		else{
			System.out.println("Error: servers must be given [-servers].");
			System.exit(-1);
		}
		
		String[] serverList=servers.split(",");
		if(serverList.length==0){
			System.out.println("Error: server list is empty.");
			System.exit(-1);
		}
		for(String server : serverList){
			String hostname,port;
			hostname = server.split(":")[0];
			port = server.split(":")[1];
			
			JSONObject serverJO=new JSONObject();
			serverJO.put("hostname", hostname);
			serverJO.put("port", port);
			serverListJA.add(serverJO);
		}
		return serverListJA;
	}
}
