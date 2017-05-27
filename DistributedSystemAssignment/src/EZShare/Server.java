package EZShare;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.host.Host;
import com.resource.FileResource;
import com.resource.Resource;
import com.resource.ResourceKey;
import com.util.Util;
import com.event.SubscribeEventListener;
import com.event.SubscribeListenerManager;

/**
 * 
 * @author Group NullPointerException Mengnan Shi 802123 Hanwei Zhu 811443
 *         Xuelin Zhao 801736 Hangyu Xia 802971
 *
 */
public class Server {

	private static SubscribeListenerManager listenerManager = new SubscribeListenerManager();
	private static SubscribeListenerManager listenerManager_secure = new SubscribeListenerManager();
	// a map of subscriber id and its listener.

	// the password of key store and trust key store
	// Declare the port number
	private static int port = 33254;
	// Declare the secure port number
	private static int port_secure = 3781;
	// A list of resources
	private static HashMap<ResourceKey, Resource> resources = new HashMap<ResourceKey, Resource>();
	// A list of Server Records
	private static LinkedList<Host> hostList = new LinkedList<Host>();
	// A list of Secure Server Records
	private static LinkedList<Host> hostList_secure = new LinkedList<Host>();
	// Server secret
	private static String secret = "";
	// Server host name
	private static Host host;
	// advertised host name
	private static String advertisedHostName = "";
	// connection interval limit
	private static long connectionIntervalLimit = 1;
	// exchange interval
	private static long exchangeInterval = 10;
	// secure exchange interval
	// store those clients' addresses who just used the server
	private static ArrayList<InetAddress> clientAddresses = new ArrayList<InetAddress>();
	private static ArrayList<InetAddress> clientAddresses_secure = new ArrayList<InetAddress>();

	private static Host itself;
	private static Host itself_secure;

	private static String hostAddress;
	// log4j logger
	private static Logger logger = Logger.getLogger(Server.class);

	public static void main(String[] args) {

		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		itself = new Host(hostAddress, port);
		itself_secure = new Host(hostAddress, port_secure);

		// Enable debugging to view the handshake and communication which
		// happens between the SSLClient and the SSLServer

		logger.info("[INFO] - Starting the EZShare Server");
		Options options = new Options();
		options.addOption("help", "show all the options on server ");
		options.addOption("advertisedhostname", true, "advertised hostname");
		options.addOption("connectionintervallimit", true, "connection interval limit in seconds");
		options.addOption("exchangeinterval", true, "exchange interval in seconds");
		options.addOption("port", true, "server port, an integer");
		options.addOption("sport",true, "secure port number");
		options.addOption("secret", true, "secret");
		options.addOption("debug", "print debug information");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);

			if (cmd.hasOption("help")) {
				printHelpInfo();
				return;
			}

			if (cmd.hasOption("advertisedhostname")) {
				advertisedHostName = cmd.getOptionValue("advertisedhostname");
			} else {
				try {
					advertisedHostName = InetAddress.getLocalHost().getHostAddress().toString();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (cmd.hasOption("connectionintervallimit")) {
				connectionIntervalLimit = Long.parseLong(cmd.getOptionValue("connectionintervallimit"));
			}
			if (cmd.hasOption("exchangeinterval")) {
				exchangeInterval = Long.parseLong(cmd.getOptionValue("exchangeinterval"));
			}
			if (cmd.hasOption("port")) {
				port = Integer.parseInt(cmd.getOptionValue("port"));
			}
			if (cmd.hasOption("sport")) {
				port_secure = Integer.parseInt(cmd.getOptionValue("sport"));
			}
			if (cmd.hasOption("secret")) {
				secret = cmd.getOptionValue("secret");
			} else
				secret = getSecret();
		} catch (org.apache.commons.cli.ParseException e) {
			e.printStackTrace();
		}

		logger.info("[INFO] - using advertised hostname : " + advertisedHostName);
		logger.info("[INFO] - bound to port : " + port);
		logger.info("[INFO] - bound to secure port : " + port_secure);
		logger.info("[INFO] - using secret : " + secret);

		// Specify the keystore details (this can be specified as VM arguments
		// as well)
		// the keystore file contains an application's own certificate and
		// private key
		ServerSocketFactory factory = ServerSocketFactory.getDefault();
		try {
			// Unsecured socket
			ServerSocket server = factory.createServerSocket(port);
			// Secured socket
			System.setProperty("javax.net.ssl.keyStore","serverKeystore/ServerKeyStore");
			System.setProperty("javax.net.ssl.keyStorePassword","123456");
			System.setProperty("javax.net.ssl.trustStore","serverKeystore/ServerKeyStore");
			System.setProperty("javax.net.ssl.trustStorePassword","123456");
			
			SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) SSLServerSocketFactory
					.getDefault();
			SSLServerSocket server_secure = (SSLServerSocket) sslserversocketfactory.createServerSocket(port_secure);
			server_secure.setNeedClientAuth(true);

			logger.info("[INFO] - started");

			// Wait for connections.
			System.out.println("Waiting for client connection..");
			// System.out.println("The secret is:" + secret);
			host = new Host(advertisedHostName, port);
			// System.out.println(host.toString());

			Thread interaction = new Thread(() -> sendHostlist(exchangeInterval * 1000));
			interaction.start();
			Thread interaction_secure = new Thread(() -> sendHostlist_secure(exchangeInterval * 1000));
			interaction_secure.start();

			Thread server_thread = new Thread(() -> server(server));
			server_thread.start();
			Thread server_thread_secure = new Thread(() -> server_secure(server_secure));
			server_thread_secure.start();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void printHelpInfo() {
		System.out.println("usage:Server [-advertisedhostname <arg>][-debug][-connectionintervallimit <arg>]");
		System.out.println("      [-exchangeinterval <arg>][-port <arg>][-secret <arg>]");
		System.out.println("Server for Unimelb COMP90015");
		System.out.println();
		System.out.println("-advertisedhostname <arg>		advertised hostname");
		System.out.println("-connectionintervallimit <arg> 	connection interval limit in seconds");
		System.out.println("-exchangeinterval <arg> 		exchange interval in seconds");
		System.out.println("-port <arg> 					server port, an integer");
		System.out.println("-secret <arg>					secret");
		System.out.println("-debug							print debug information");
		System.out.println("-sport <arg>					secure port number");
	}

	private static void server(ServerSocket server) {
		try {
			while (true) {
				Socket client = server.accept();
				// if the client just used the server before, close it
				InetAddress clientIpAddress = client.getInetAddress();
				if (clientAddresses.contains(clientIpAddress)) {
					// close the socket
					client.close();
					continue;
				}
				logger.info("[INFO] - new connection from " + client.getInetAddress() + ":" + client.getPort());
				// Start a new thread for a connection
				Thread t = new Thread(() -> serveClient(client));
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	private static void server_secure(ServerSocket server) {
		try {
			while (true) {
				SSLSocket client = (SSLSocket) server.accept();
				// if the client just used the server before, close it
				InetAddress clientIpAddress = client.getInetAddress();
				if (clientAddresses_secure.contains(clientIpAddress)) {
					// close the socket
					client.close();
					continue;
				}
				logger.info("[INFO] - new connection from " + client.getInetAddress() + ":" + client.getPort());
				// Start a new thread for a connection
				Thread t = new Thread(() -> serveClient_secure(client));
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * serverClient
	 * 
	 * @param client
	 */
	private static void serveClient(Socket client) {

		addClientAddress(client);

		try (Socket clientSocket = client) {
			// Input stream
			DataInputStream input = new DataInputStream(clientSocket.getInputStream());
			// Output Stream
			DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

			/** get client's command */
			JSONParser parser = new JSONParser();
			JSONObject clientCommand = (JSONObject) parser.parse(input.readUTF());
			logger.info("[INFO] - RECEIVED: " + clientCommand.toJSONString());

			LinkedList<JSONObject> results = dealWithCommand(clientCommand, output, input);
			for (JSONObject result : results) {
				output.writeUTF(result.toJSONString());
				output.flush();
				logger.info("[FINE] - SEND: " + result.toJSONString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			try {
				Thread.sleep(connectionIntervalLimit * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			removeClientAddress(client);
		}
	}

	/**
	 * serverClient_secure
	 * 
	 * @param client
	 */
	private static void serveClient_secure(Socket client) {

		addClientAddress_secure(client);

		try (Socket clientSocket = client) {
			// Input stream
			DataInputStream input = new DataInputStream(clientSocket.getInputStream());
			// Output Stream
			DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

			/** get client's command */
			JSONParser parser = new JSONParser();
			JSONObject clientCommand = (JSONObject) parser.parse(input.readUTF());
			logger.info("[INFO] - RECEIVED: " + clientCommand.toJSONString());

			LinkedList<JSONObject> results = dealWithCommand_secure(clientCommand, output, input);
			if (results != null) {
				for (JSONObject result : results) {
					output.writeUTF(result.toJSONString());
					output.flush();
					logger.info("[FINE] - SEND: " + result.toJSONString());
				}
			}
		} catch (IOException e) {
			logger.info("[INFO] - This connection is refused");
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			try {
				Thread.sleep(connectionIntervalLimit * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			removeClientAddress_secure(client);
		}
	}

	/**
	 * dealWithCommand
	 * 
	 * @param clientCommand
	 * @param output
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static LinkedList<JSONObject> dealWithCommand(JSONObject clientCommand, DataOutputStream output,
			DataInputStream input) {
		LinkedList<JSONObject> results = new LinkedList<JSONObject>();
		JSONObject result = new JSONObject();

		if (clientCommand.containsKey("command")) {
			switch (clientCommand.get("command").toString()) {
			case "PUBLISH":
				result = publish(clientCommand);
				results.add(result);
				break;
			case "REMOVE":
				result = remove(clientCommand);
				results.add(result);
				break;
			case "QUERY":
				results = query(clientCommand);
				break;
			case "FETCH":
				fetch(clientCommand, output);
				break;
			case "SHARE":
				result = share(clientCommand);
				results.add(result);
				break;
			case "EXCHANGE":
				result = exchange(clientCommand);
				results.add(result);
				break;
			case "SUBSCRIBE":
				result = subscribe(clientCommand, output, input);
				results.add(result);
				break;
			default:
				result.put("response", "error");
				results.add(result);
				break;
			}
		} else {
			result.put("response", "error");
			results.add(result);
		}

		return results;
	}

	/**
	 * dealWithCommand
	 * 
	 * @param clientCommand
	 * @param output
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static LinkedList<JSONObject> dealWithCommand_secure(JSONObject clientCommand, DataOutputStream output,
			DataInputStream input) {
		LinkedList<JSONObject> results = new LinkedList<JSONObject>();
		JSONObject result = new JSONObject();

		if (clientCommand.containsKey("command")) {
			switch (clientCommand.get("command").toString()) {
			case "PUBLISH":
				result = publish(clientCommand);
				results.add(result);
				break;
			case "REMOVE":
				result = remove(clientCommand);
				results.add(result);
				break;
			case "QUERY":
				results = query_secure(clientCommand);
				break;
			case "FETCH":
				fetch(clientCommand, output);
				break;
			case "SHARE":
				result = share(clientCommand);
				results.add(result);
				break;
			case "EXCHANGE":
				result = exchange_secure(clientCommand);
				results.add(result);
				break;
			case "SUBSCRIBE":
				result = subscribe_secure(clientCommand, output, input);
				results.add(result);
				break;
			default:
				result.put("response", "error");
				results.add(result);
				break;
			}
		} else {
			result.put("response", "error");
			results.add(result);
		}

		return results;
	}

	/**
	 * subscribe
	 * 
	 * @param clientCommand,
	 *            output
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject subscribe(JSONObject clientCommand, DataOutputStream output, DataInputStream input) {
		JSONObject result;
		result = subscribeCommandValidOrNot(clientCommand); // return null if
															// clientcommand is
															// invalid
		if (result != null)
			return result;
		result = new JSONObject();
		result.put("response", "success");
		result.put("id", clientCommand.get("id").toString());
		try {
			output.writeUTF(result.toJSONString());
			output.flush();
			logger.info("[FINE] - SEND: " + result.toJSONString());
			result.remove("response");
			result.remove("id");
		} catch (IOException e) {
			System.out.println(e.toString());
		}

		// create a listener to listen to new resources published and shared in
		// this server
		SubscribeEventListener listener = new SubscribeEventListener(clientCommand, output);
		listenerManager.addSubscribeEventListener(listener);

		// subscribe to the other severs
		for (Host ht : hostList) {
			listener.subscribeToServer(ht);
		}

		// waiting for the client to unsubscribe
		JSONParser parser = new JSONParser();
		try {
			JSONObject receive = (JSONObject) parser.parse(input.readUTF().toString());
			if ("UNSUBSCRIBE".equals(receive.get("command").toString())) {
				logger.info("[FINE] - RECEIVED: " + receive.toJSONString());
				listenerManager.deleteSubscribeEventListener(listener);
				listener.unsubscribeAllServers();
				result.put("resultSize", listener.resultSize);
			} else {
				result.put("response", "error");
				result.put("errorMessage", "invalid command");
			}
		} catch (ParseException e) {
			System.out.println(e.toString());
		} catch (IOException e) {
			System.out.println(e.toString());
		}

		return result;
	}

	/**
	 * subscribe_secure
	 * 
	 * @param clientCommand,
	 *            output
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject subscribe_secure(JSONObject clientCommand, DataOutputStream output,
			DataInputStream input) {
		JSONObject result;
		result = subscribeCommandValidOrNot(clientCommand); // return null if
															// clientcommand is
															// invalid
		if (result != null)
			return result;

		result = new JSONObject();
		result.put("response", "success");
		result.put("id", clientCommand.get("id").toString());
		try {
			output.writeUTF(result.toJSONString());
			output.flush();
			logger.info("[FINE] - SEND: " + result.toJSONString());
			result.remove("response");
			result.remove("id");
		} catch (IOException e) {
			System.out.println(e.toString());
		}

		// create a listener to listen to new resources published and shared in
		// this server
		SubscribeEventListener listener = new SubscribeEventListener(clientCommand, output);
		listenerManager_secure.addSubscribeEventListener(listener);

		// subscribe to the other severs
		for (Host ht : hostList_secure) {
			listener.subscribeToServer(ht);
		}

		JSONParser parser = new JSONParser();
		try {
			// wait for the client to unsubscribe
			JSONObject receive = (JSONObject) parser.parse(input.readUTF().toString());
			listenerManager.deleteSubscribeEventListener(listener);
			if ("UNSUBSCRIBE".equals(receive.get("command").toString())) {
				listener.unsubscribeAllServers();
				listenerManager_secure.deleteSubscribeEventListener(listener);
				result.put("resultSize", String.valueOf(listener.resultSize));
			} else {
				result.put("response", "error");
				result.put("errorMessage", "invalid command");
			}
		} catch (ParseException e) {
			System.out.println(e.toString());
		} catch (IOException e) {
			System.out.println(e.toString());
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static JSONObject subscribeCommandValidOrNot(JSONObject clientCommand) {
		JSONObject result = new JSONObject();
		if (!clientCommand.containsKey("resourceTemplate")) {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			return result;
		}
		JSONObject resourceTemplate = (JSONObject) clientCommand.get("resourceTemplate");

		if (!clientCommand.containsKey("relay")) {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			return result;
		}
		if (!clientCommand.containsKey("id")) {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			return result;
		}
		if (!clientCommand.containsKey("resourceTemplate")) {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			return result;
		}

		if (resourceTemplate.containsKey("owner")) {
			String owner = resourceTemplate.get("owner").toString();
			owner = Util.clean(owner);
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resourceTemplate");
				return result;
			}
		}
		result = null;
		return result;
	}

	/**
	 * publish
	 * 
	 * @param clientCommand
	 * @return
	 */
	@SuppressWarnings({ "unchecked" })
	private static JSONObject publish(JSONObject clientCommand) {
		String name = "", description = "", channel = "", owner = "";
		LinkedList<String> tags = new LinkedList<String>();
		URI uri = null;
		JSONObject resource;
		JSONObject result = new JSONObject();

		if (clientCommand.containsKey("resource")) {
			resource = (JSONObject) clientCommand.get("resource");
		}
		// if resource field are not given
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resource");
			return result;
		}

		/** get all the fields from command */
		/** get name from command */
		if (resource.containsKey("name")) {
			name = resource.get("name").toString();
			name = Util.clean(name);
		}
		/** get tags from command */
		if (resource.containsKey("tags")) {
			JSONArray tag_array = (JSONArray) resource.get("tags");
			for (int i = 0; i < tag_array.size(); i++) {
				String tag = tag_array.get(i).toString();
				tag = Util.clean(tag);
				tags.add(tag);
			}
		}
		/** get description from command */
		if (resource.containsKey("description")) {
			description = resource.get("description").toString();
			description = Util.clean(description);
		}
		/** get channel from command */
		if (resource.containsKey("channel")) {
			channel = resource.get("channel").toString();
			channel = Util.clean(channel);
		}
		/** get owner from command */
		if (resource.containsKey("owner")) {
			owner = resource.get("owner").toString();
			owner = Util.clean(owner);
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resource");
				return result;
			}
		}
		/** get uri from command */
		try {
			if (resource.containsKey("uri")) {
				uri = new URI(resource.get("uri").toString());
				String scheme = uri.getScheme();
				if ("file".equals(scheme)) {
					result.put("response", "error");
					result.put("errorMessage", "cannot publish resource");
					return result;
				}
			} else {
				result.put("response", "error");
				result.put("errorMessage", "cannot publish resource");
				return result;
			}
		} catch (URISyntaxException e) {
			result.put("response", "error");
			result.put("errorMessage", "cannot publish resource");
			return result;
		}
		/** end of getting all the fields from command */

		result = new JSONObject();
		Resource rs = new Resource(name, description, tags, uri, channel, owner, host);
		ResourceKey key = new ResourceKey(owner, channel, uri);
		/** prepare command and arguments */
		Object[] args = new Object[2];
		args[0] = key;
		args[1] = rs;
		accessResources("ADD", args);
		result.put("response", "success");

		// inform all subscribers of the resource published just now
		listenerManager.informSubscribersOfNewResource(rs);
		listenerManager_secure.informSubscribersOfNewResource(rs);
		return result;
	}

	/**
	 * remove
	 * 
	 * @param clientCommand
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject remove(JSONObject clientCommand) {
		JSONObject result = new JSONObject();
		JSONObject resource = null;
		URI uri = null;
		String channel = "", owner = "", uri_string = "";
		if (clientCommand.containsKey("resource")) {
			resource = (JSONObject) clientCommand.get("resource");
		}
		// if resource field are not given
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resource");
			return result;
		}
		if (resource.containsKey("channel")) {
			channel = resource.get("channel").toString();
			channel = Util.clean(channel);
		}

		if (resource.containsKey("owner")) {
			owner = resource.get("owner").toString();
			owner = Util.clean(owner);
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resource");
				return result;
			}
		}
		if (resource.containsKey("uri")) {
			uri_string = resource.get("uri").toString();
			uri_string = Util.clean(uri_string);
		}

		if ("".equals(uri_string)) {
			result.put("response", "error");
			result.put("errorMessage", "missing resource");
			return result;
		} else {
			try {
				uri = new URI(resource.get("uri").toString());
			} catch (URISyntaxException e) {
				result.put("response", "error");
				result.put("errorMessage", "cannot remove resource");
				return result;
			}
		}
		/** end of getting all the fields */

		ResourceKey key = new ResourceKey(owner, channel, uri);
		// if resource exists
		if (resources.containsKey(key)) {
			Object[] args = new Object[1];
			args[0] = key;
			accessResources("DELETE", args);
			result.put("response", "success");
		}
		// if it doesn't exist
		else {
			result.put("response", "error");
			result.put("errorMessage", "cannot remove resource");
		}
		return result;
	}

	/**
	 * query
	 * 
	 * @param clientCommand
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static LinkedList<JSONObject> query(JSONObject clientCommand) {
		LinkedList<JSONObject> results = new LinkedList<JSONObject>();
		JSONObject result = new JSONObject();
		JSONObject resultSize = new JSONObject();
		LinkedList<String> tags = new LinkedList<String>();
		JSONObject resourceTemplate = null;
		URI uri = null;
		String name = "", description = "", channel = "", owner = "", relay = "";

		// get relay from command
		if (clientCommand.containsKey("relay")) {
			relay = clientCommand.get("relay").toString();
			relay = Util.clean(relay);
		} else {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			results.add(result);
			return results;
		}
		// get resourceTemplate from command
		if (clientCommand.containsKey("resourceTemplate"))
			resourceTemplate = (JSONObject) clientCommand.get("resourceTemplate");
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			results.add(result);
			return results;
		}
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
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resourceTemplate");
				results.add(result);
				return results;
			}
		}
		/** get ezserver from resourceTemplate */
		result.put("response", "success");
		results.add(result);
		// go through the resources list
		Iterator<Entry<ResourceKey, Resource>> iter = resources.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<ResourceKey, Resource> entry = (Entry<ResourceKey, Resource>) iter.next();
			Resource val = (Resource) entry.getValue();
			if (channel.equals(val.getChannel()) && (owner.equals("") || owner.equals(val.getOwner()))
					&& (uri.toString().equals("") || uri.equals(val.getUri()))) {
				// to match the tags
				if (tags.size() != 0) {
					boolean flag = false;
					for (String tag : tags) {
						flag = false;
						for (String val_tag : val.getTags()) {
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
						continue;
					}
				}
				if (name.equals("") && description.equals("") || !name.equals("") && val.getName().indexOf(name) != -1
						|| description.equals("") && val.getDescription().indexOf(description) != -1) {
					JSONObject resource = new JSONObject();
					resource = val.getResourceJSON();
					if (!resource.get("owner").equals("")) {
						resource.put("owner", "*");
					}
					results.add(resource);
				}
			}
		}
		// sent query to every server in the host list
		// clientCommand
		if (relay.equals("true")) {
			for (Host ht : hostList) {
				Socket socket = null;
				JSONParser parser = new JSONParser();
				JSONObject newCommand = new JSONObject();
				try {
					socket = new Socket(ht.getHostname(), ht.getPort());
					newCommand = (JSONObject) parser.parse(clientCommand.toJSONString());
					newCommand.put("relay", "false");
					newCommand.put("owner", "");
					newCommand.put("channel", "");
					results.addAll(sendToServer(newCommand, socket));
					socket.close();
				} catch (IOException | ParseException e) {
					e.printStackTrace();
				}
			}
		}
		resultSize.put("resultSize", "" + (results.size() - 1));
		results.add(resultSize);
		return results;
	}

	/**
	 * query
	 * 
	 * @param clientCommand
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static LinkedList<JSONObject> query_secure(JSONObject clientCommand) {
		LinkedList<JSONObject> results = new LinkedList<JSONObject>();
		JSONObject result = new JSONObject();
		JSONObject resultSize = new JSONObject();
		LinkedList<String> tags = new LinkedList<String>();
		JSONObject resourceTemplate = null;
		URI uri = null;
		String name = "", description = "", channel = "", owner = "", relay = "";

		// get relay from command
		if (clientCommand.containsKey("relay")) {
			relay = clientCommand.get("relay").toString();
			relay = Util.clean(relay);
		} else {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			results.add(result);
			return results;
		}
		// get resourceTemplate from command
		if (clientCommand.containsKey("resourceTemplate"))
			resourceTemplate = (JSONObject) clientCommand.get("resourceTemplate");
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			results.add(result);
			return results;
		}
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
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resourceTemplate");
				results.add(result);
				return results;
			}
		}
		/** get ezserver from resourceTemplate */
		result.put("response", "success");
		results.add(result);
		// go through the resources list
		Iterator<Entry<ResourceKey, Resource>> iter = resources.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<ResourceKey, Resource> entry = (Entry<ResourceKey, Resource>) iter.next();
			Resource val = (Resource) entry.getValue();
			if (channel.equals(val.getChannel()) && (owner.equals("") || owner.equals(val.getOwner()))
					&& (uri.toString().equals("") || uri.equals(val.getUri()))) {
				// to match the tags
				if (tags.size() != 0) {
					boolean flag = false;
					for (String tag : tags) {
						flag = false;
						for (String val_tag : val.getTags()) {
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
						continue;
					}
				}
				if (name.equals("") && description.equals("") || !name.equals("") && val.getName().indexOf(name) != -1
						|| description.equals("") && val.getDescription().indexOf(description) != -1) {
					JSONObject resource = new JSONObject();
					resource = val.getResourceJSON();
					if (!resource.get("owner").equals("")) {
						resource.put("owner", "*");
					}
					results.add(resource);
				}
			}
		}
		// sent query to every server in the host list
		// clientCommand
		// @TODO: secure connections to hosts in hostList_secure
		if (relay.equals("true")) {
			for (Host ht : hostList_secure) {
				Socket socket = null;
				JSONParser parser = new JSONParser();
				JSONObject newCommand = new JSONObject();
				try {
					SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
					socket = (SSLSocket) sslsocketfactory.createSocket(ht.getHostname(), ht.getPort());

					newCommand = (JSONObject) parser.parse(clientCommand.toJSONString());
					newCommand.put("relay", "false");
					newCommand.put("owner", "");
					newCommand.put("channel", "");
					results.addAll(sendToServer(newCommand, socket));
					socket.close();
				} catch (IOException | ParseException e) {
					e.printStackTrace();
				} 
			}
		}
		resultSize.put("resultSize", "" + (results.size() - 1));
		results.add(resultSize);
		return results;
	}

	/**
	 * fetch
	 * 
	 * @param clientCommand
	 * @param output
	 */
	@SuppressWarnings("unchecked")
	private static void fetch(JSONObject clientCommand, DataOutputStream output) {
		JSONObject result = new JSONObject();
		JSONObject resultSize = new JSONObject();
		LinkedList<String> tags = new LinkedList<String>();
		JSONObject resourceTemplate = null;
		URI uri = null;
		String name = "", description = "", channel = "", owner = "";
		int size = 0;

		// get resourceTemplate from command
		if (clientCommand.containsKey("resourceTemplate"))
			resourceTemplate = (JSONObject) clientCommand.get("resourceTemplate");
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			try {
				output.writeUTF(result.toJSONString());
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}

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
		/** get URI from resourceTemplate */
		try {
			if (resourceTemplate.containsKey("uri")) {
				uri = new URI(resourceTemplate.get("uri").toString());
				String scheme = uri.getScheme();
				if (!"file".equals(scheme)) {
					result.put("response", "error");
					result.put("errorMessage", "missing resourceTemplate");
					try {
						output.writeUTF(result.toJSONString());
					} catch (IOException e) {
						e.printStackTrace();
					}
					return;
				}
			}
		} catch (URISyntaxException e) {
			result.put("response", "error");
			result.put("errorMessage", "missing resourceTemplate");
			try {
				output.writeUTF(result.toJSONString());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
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
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resourceTemplate");
				try {
					output.writeUTF(result.toJSONString());
				} catch (IOException e) {
					e.printStackTrace();
				}
				return;
			}
		}
		/** get ezserver from resourceTemplate */
		result.put("response", "success");
		try {
			ResourceKey key = new ResourceKey(owner, channel, uri);
			FileResource resource = null;

			output.writeUTF(result.toJSONString());

			if (resources.containsKey(key)) {
				Object[] args = new Object[1];
				args[0] = key;
				resource = (FileResource) accessResources("GET", args);
				Resource resource_template = new Resource(name, description, tags, uri, channel, owner, null);
				if (resource_template.equals(resource)) {
					size++;
					output.writeUTF(resource.getResourceJSON().toJSONString());
					output.flush();
					byte[] data = resource.getData();

					System.out.println(data.length);

					int sizeRemaining = data.length;
					int chunkSize = 1024 * 1024;
					int start = 0;
					// write data
					while (true) {
						if (chunkSize > sizeRemaining) {
							chunkSize = sizeRemaining;
						}
						output.write(data, start, chunkSize);
						output.flush();
						start += chunkSize;
						sizeRemaining -= chunkSize;
						if (sizeRemaining <= 0) {
							break;
						}
					}

				}
			}
			resultSize.put("resultSize", "" + size);
			output.writeUTF(resultSize.toJSONString());
			output.flush();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * share
	 * 
	 * @param clientCommand
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings({ "unchecked" })
	private static JSONObject share(JSONObject clientCommand) {
		String name = "", description = "", channel = "", owner = "";
		LinkedList<String> tags = new LinkedList<String>();
		URI uri = null;
		byte[] data = null;
		long size = 0;
		JSONObject resource;
		JSONObject result = new JSONObject();

		if (clientCommand.containsKey("resource") && clientCommand.containsKey("secret")) {
			String sc = clientCommand.get("secret").toString();
			sc = Util.clean(sc);
			if (secret.equals(sc)) {
				resource = (JSONObject) clientCommand.get("resource");
			} else {
				result.put("response", "error");
				result.put("errorMessage", "incorrect secret");
				return result;
			}
		}
		// if resource field or secret are not given
		else {
			result.put("response", "error");
			result.put("errorMessage", "missing resource and \\/or secret");
			return result;
		}

		/** get all the fields from command */
		/** get name from command */
		if (resource.containsKey("name")) {
			name = resource.get("name").toString();
			name = Util.clean(name);
		}
		/** get tags from command */
		if (resource.containsKey("tags")) {
			JSONArray tag_array = (JSONArray) resource.get("tags");
			for (int i = 0; i < tag_array.size(); i++) {
				String tag = tag_array.get(i).toString();
				tag = Util.clean(tag);
				tags.add(tag);
			}
		}
		/** get description from command */
		if (resource.containsKey("description")) {
			description = resource.get("description").toString();
			description = Util.clean(description);
		}
		/** get channel from command */
		if (resource.containsKey("channel")) {
			channel = resource.get("channel").toString();
			channel = Util.clean(channel);
		}
		/** get owner from command */
		if (resource.containsKey("owner")) {
			owner = resource.get("owner").toString();
			owner = Util.clean(owner);
			if (owner.equals("*")) {
				result.put("response", "error");
				result.put("errorMessage", "invalid resource");
				return result;
			}
		}
		/** get uri from command */
		try {
			if (resource.containsKey("uri")) {
				uri = new URI(resource.get("uri").toString());
				String scheme = uri.getScheme();
				if (!"file".equals(scheme)) {
					result.put("response", "error");
					result.put("errorMessage", "cannot share resource");
					return result;
				} else {
					/** save data **/
					InputStream in;
					try {
						File f = new File(uri.getPath().toString());
						in = new FileInputStream(f);
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						byte[] buffer = new byte[1024 * 4];
						int n = 0;
						while ((n = in.read(buffer)) != -1) {
							out.write(buffer, 0, n);
						}
						data = out.toByteArray();
						size = data.length;
						in.close();
					} catch (FileNotFoundException e) {
						result.put("response", "error");
						result.put("errorMessage", "cannot share resource");
						return result;
					} catch (IOException e) {
						result.put("response", "error");
						result.put("errorMessage", "cannot share resource");
						return result;
					}
				}
			} else {
				result.put("response", "error");
				result.put("errorMessage", "missing resource and\\/or secret");
				return result;
			}
		} catch (URISyntaxException e) {
			result.put("response", "error");
			result.put("errorMessage", "cannot share resource");
			return result;
		}
		/** end of getting all the fields from command */

		result = new JSONObject();
		FileResource rs = new FileResource(name, description, tags, uri, channel, owner, host, data, size);
		ResourceKey key = new ResourceKey(owner, channel, uri);

		/** prepare command and arguments */
		Object[] args = new Object[2];
		args[0] = key;
		args[1] = rs;
		accessResources("ADD", args);
		result.put("response", "success");

		// inform all subscribers of the resource shared just now
		listenerManager.informSubscribersOfNewResource(rs);
		listenerManager_secure.informSubscribersOfNewResource(rs);

		return result;
	}

	/**
	 * exchange
	 * 
	 * @param commandClient
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject exchange(JSONObject commandClient) {
		JSONObject result = new JSONObject();
		if (commandClient.containsKey("serverList")) {
			JSONArray listArray = (JSONArray) commandClient.get("serverList");
			JSONObject recieveHost;
			for (int i = 0; i < listArray.size(); i++) {
				recieveHost = (JSONObject) listArray.get(i);
				if (recieveHost.containsKey("hostname") && recieveHost.containsKey("port")) {
					String hostname = recieveHost.get("hostname").toString();
					hostname = Util.clean(hostname);
					String port = recieveHost.get("port").toString();
					port = Util.clean(port);
					Host tempHost = new Host(hostname, Integer.parseInt(port));
					if (hostList.isEmpty()) {
						hostList.add(tempHost);
						try{
							if( InetAddress.getLocalHost().getHostAddress()!=tempHost.getHostname())
								listenerManager.informSubscribersOfNewServer(tempHost);
						}catch(UnknownHostException e){
							System.out.println(e.toString());
						}
					}
					// check the same host
					else {
						boolean flag = true;
						for (int j = 0; j < hostList.size(); j++) {

							if ((hostList.get(j).getHostname().equals(tempHost.getHostname()))
									&& (hostList.get(j).getPort() == tempHost.getPort())) {
								flag = false;
								break;
							}
						}
						if (flag) {
							hostList.add(tempHost);
							try{
								if( InetAddress.getLocalHost().getHostAddress()!=tempHost.getHostname())
									listenerManager.informSubscribersOfNewServer(tempHost);
							}catch(UnknownHostException e){
								System.out.println(e.toString());
							}
						}
					}
				} else {
					result.put("response", "error");
					result.put("errorMessage", "invalid server record");
					return result;
				}
			}

		} else {
			result.put("response", "error");
			result.put("errorMessage", "missing or invalid server list");
			return result;
		}
		result.put("response", "success");
		return result;
	}

	/**
	 * exchange
	 * 
	 * @param commandClient
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject exchange_secure(JSONObject commandClient) {
		JSONObject result = new JSONObject();
		if (commandClient.containsKey("serverList")) {
			JSONArray listArray = (JSONArray) commandClient.get("serverList");
			JSONObject recieveHost;
			for (int i = 0; i < listArray.size(); i++) {
				recieveHost = (JSONObject) listArray.get(i);
				if (recieveHost.containsKey("hostname") && recieveHost.containsKey("port")) {
					String hostname = recieveHost.get("hostname").toString();
					hostname = Util.clean(hostname);
					String port = recieveHost.get("port").toString();
					port = Util.clean(port);
					Host tempHost = new Host(hostname, Integer.parseInt(port));
					if (hostList_secure.isEmpty()) {
						hostList_secure.add(tempHost);
						try{
							if( InetAddress.getLocalHost().getHostAddress()!=tempHost.getHostname())
								listenerManager_secure.informSubscribersOfNewServer(tempHost);
						}catch(UnknownHostException e){
							System.out.println(e.toString());
						}
					}
					// check the same host
					else {
						boolean flag = true;
						for (int j = 0; j < hostList_secure.size(); j++) {

							if ((hostList_secure.get(j).getHostname().equals(tempHost.getHostname()))
									&& (hostList_secure.get(j).getPort() == tempHost.getPort())) {
								flag = false;
								break;
							}
						}
						if (flag) {
							hostList_secure.add(tempHost);
							try{
								if( InetAddress.getLocalHost().getHostAddress()!=tempHost.getHostname())
									listenerManager_secure.informSubscribersOfNewServer(tempHost);
							}catch(UnknownHostException e){
								System.out.println(e.toString());
							}
						}
					}
				} else {
					result.put("response", "error");
					result.put("errorMessage", "invalid server record");
					return result;
				}
			}

		} else {
			result.put("response", "error");
			result.put("errorMessage", "missing or invalid server list");
			return result;
		}
		result.put("response", "success");
		return result;
	}

	/**
	 * getSecret
	 * 
	 * @return secret
	 */
	private static String getSecret() {
		String secret = "";
		Random random = new Random();
		for (int i = 0; i < 26; i++) {
			String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num";
			if ("char".equalsIgnoreCase(charOrNum)) {
				int temp = random.nextInt(2) % 2 == 0 ? 65 : 97;
				secret += (char) (random.nextInt(26) + temp);
			} else if ("num".equalsIgnoreCase(charOrNum)) {
				secret += String.valueOf(random.nextInt(10));
			}
		}
		return secret;
	}

	/**
	 * send the request to another server as an client
	 * 
	 * @return LinkedList<JSONObject>
	 */
	private static LinkedList<JSONObject> sendToServer(JSONObject request, Socket sc) {
		LinkedList<JSONObject> results = new LinkedList<JSONObject>();
		JSONObject result = new JSONObject();
		DataInputStream input = null;
		DataOutputStream output = null;
		try {
			input = new DataInputStream(sc.getInputStream());
			output = new DataOutputStream(sc.getOutputStream());
			output.writeUTF(request.toJSONString());
			output.flush();
			JSONParser parser = new JSONParser();
			/** receive JSONObject */
			while (true) {
				try {
					result = (JSONObject) parser.parse(input.readUTF().toString());
					if (result.containsKey("response")) {
						continue;
					}
					if (result.containsKey("resultSize")) {
						continue;
					}
					results.add(result);
				} catch (EOFException e) {
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return results;
	}

	/**
	 * get a random host in the host list
	 * 
	 * @return Host
	 */
	private static Host getRandomHost() {
		Host host = null;
		Random random = new Random();
		int size = hostList.size();
		if (size <= 0) {
			return null;
		}
		host = hostList.get(random.nextInt(size));
		return host;
	}

	/**
	 * get a random host in the host list
	 * 
	 * @return Host
	 */
	private static Host getRandomHost_secure() {
		Host host = null;
		Random random = new Random();
		int size = hostList_secure.size();
		if (size <= 0) {
			return null;
		}
		host = hostList_secure.get(random.nextInt(size));
		return host;
	}

	/**
	 * send exchange command to a random Host
	 * 
	 * @param interval
	 */
	@SuppressWarnings("unchecked")
	private static void sendHostlist_secure(long interval) {

		Host selectedHost = null;
		JSONObject exchangeCommand = new JSONObject();

		while (true) {
			JSONArray serverList = new JSONArray();
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
			}
			synchronized (hostList_secure) {
				showHost_secure();
				// not empty
				if (!hostList_secure.isEmpty()) {
					selectedHost = getRandomHost_secure();

					// not himself
					boolean localHostFlag = false;
					if ((selectedHost.getHostname().equals("localhost"))||(selectedHost.getHostname().equals("127.0.0.1"))) {
						localHostFlag = true;
						selectedHost.setHostname(hostAddress);
					}
					if(selectedHost.equals(itself_secure)){
						continue;
					} else if(localHostFlag){
						removeHost_secure(selectedHost);
					}

					for (Host ht : hostList_secure) {
						serverList.add(ht.getHostJSON());
					}
					exchangeCommand.put("command", "EXCHANGE");
					exchangeCommand.put("serverList", serverList);
					SSLSocket socket;
					try{
						SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
						socket = (SSLSocket) sslsocketfactory.createSocket(selectedHost.getHostname(),
								selectedHost.getPort());
						sendToServer(exchangeCommand, socket);
						socket.close();
					} catch (IOException e) {
						removeHost_secure(selectedHost);
					} 
				}
			}
		}
	}

	/**
	 * send exchange command to a random Host
	 * 
	 * @param interval
	 */
	@SuppressWarnings("unchecked")
	private static void sendHostlist(long interval) {

		Host selectedHost = null;
		JSONObject exchangeCommand = new JSONObject();

		while (true) {
			JSONArray serverList = new JSONArray();
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
			}
			synchronized (hostList) {
				showHost();
				// not empty
				if (!hostList.isEmpty()) {
					selectedHost = getRandomHost();
					
					// not himself
					boolean localHostFlag = false;
					if (selectedHost.getHostname().equals("localhost")) {
						localHostFlag = true;
						selectedHost.setHostname(hostAddress);
					}
					if(selectedHost.equals(itself)){
						continue;
					} else if(localHostFlag){
						removeHost(selectedHost);
					}
					for (Host ht : hostList) {
						serverList.add(ht.getHostJSON());
					}
					exchangeCommand.put("command", "EXCHANGE");
					exchangeCommand.put("serverList", serverList);
					Socket sc;
					try {
						sc = new Socket(selectedHost.getHostname(), selectedHost.getPort());
						sendToServer(exchangeCommand, sc);
						sc.close();
					} catch (IOException e) {
						// remove
						System.out.println("IO Exception");
						removeHost(selectedHost);
					}
				}
			}
		}
	}

	private static synchronized void removeHost(Host selectedHost) {
		if (hostList.contains(selectedHost)) {
			hostList.remove(selectedHost);
		}
	}

	private static synchronized void removeHost_secure(Host selectedHost) {
		if (hostList_secure.contains(selectedHost)) {
			hostList_secure.remove(selectedHost);
		}
	}

	private static synchronized void showHost() {
		System.out.println("----------------------host----------------------------");
		for (Host h : hostList) {
			System.out.println(h.getHostJSON().toString());
		}
	}

	private static synchronized void showHost_secure() {
		System.out.println("------------------secure hosts-----------------------");
		for (Host h : hostList_secure) {
			System.out.println(h.getHostJSON().toString());
		}
	}

	private static synchronized void addClientAddress(Socket socket) {
		clientAddresses.add(socket.getInetAddress());
	}

	private static synchronized void removeClientAddress(Socket socket) {
		InetAddress address = socket.getInetAddress();
		if (clientAddresses.contains(address)) {
			clientAddresses.remove(address);
		}
	}

	private static synchronized void addClientAddress_secure(Socket socket) {
		clientAddresses_secure.add(socket.getInetAddress());
	}

	private static synchronized void removeClientAddress_secure(Socket socket) {
		InetAddress address = socket.getInetAddress();
		if (clientAddresses_secure.contains(address)) {
			clientAddresses_secure.remove(address);
		}
	}

	/**
	 * makes method which accesses resources synchronized
	 * 
	 * @param command
	 * @param args
	 * @return if get return resource else null
	 */
	private static synchronized Resource accessResources(String command, Object[] args) {
		switch (command) {
		case "ADD":
			ResourceKey key_add = (ResourceKey) args[0];
			Resource resource_add = (Resource) args[1];
			resources.put(key_add, resource_add);
			return null;
		case "DELETE":
			ResourceKey key_delete = (ResourceKey) args[0];
			if (resources.containsKey(key_delete)) {
				resources.remove(key_delete);
			}
			return null;
		case "GET":
			ResourceKey key_get = (ResourceKey) args[0];
			if (resources.containsKey(key_get)) {
				return resources.get(key_get);
			}
			return null;
		default:
			return null;
		}
	}

}
