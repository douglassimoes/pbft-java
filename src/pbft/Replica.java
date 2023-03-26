package pbft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Replica {


	public static void main(String[] args) throws IOException {


		int replicaId = Integer.valueOf(args[0]);
		// Add replicas from the config file
		HashMap<Integer,InetSocketAddress> repIpPorts = readConfigFile();
		
		InetSocketAddress replicaSocket = repIpPorts.remove(replicaId);
		
		ReplicaServerSide server = new ReplicaServerSide(replicaSocket,repIpPorts);
		// Listening from messages from Clients and Replicas
		server.listenForMessage();
		
		while(true) {
			server.sendMessage();
		}
	}

	// Reads the configuration file 
	private static HashMap<Integer,InetSocketAddress> readConfigFile() {
		HashMap<Integer,InetSocketAddress> repIpPorts = new HashMap<Integer,InetSocketAddress> ();
		String baseDirectory;
		try {
			baseDirectory = new File("./").getCanonicalPath();
			//			System.out.println(baseDirectory);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		JSONParser parser = new JSONParser();
		try {
			Object obj = parser.parse(new FileReader(baseDirectory+"/config/config.json"));
			JSONObject jsonObject = (JSONObject) obj;
			for (int i = 0; i < jsonObject.size(); i++) {
				JSONObject innerObj = (JSONObject) jsonObject.get(Integer.toString(i));
				String ip = (String) innerObj.get("ip");
				int port = ((Long) innerObj.get("portServer")).intValue();
				InetSocketAddress replicaSocket = new InetSocketAddress(ip,port);
				repIpPorts.put(i,replicaSocket);
			}
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}

		return repIpPorts;
	}
}
