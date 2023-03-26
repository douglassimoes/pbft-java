package pbft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Client {


	public static void main(String[] args) throws IOException {

		// Add replicas from the config file
		ArrayList<InetSocketAddress> repIpPorts = readConfigFile();
		
		// When Server port is equals 0 the socket it will listen to any free port 
		InetSocketAddress clientSocket = new InetSocketAddress(0);
		ReplicaClientSide client = new ReplicaClientSide(clientSocket,repIpPorts);
		
		// Listening from messages from Clients and Replicas
		client.listenForMessage();
		
		while(true) {
			client.sendMessage();
		}
	}

	// Reads the configuration file 
	private static ArrayList<InetSocketAddress> readConfigFile() {
		ArrayList<InetSocketAddress> repIpPorts = new ArrayList<InetSocketAddress>();
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
				int portServer = ((Long) innerObj.get("portServer")).intValue();
//				int portRemote = ((Long) innerObj.get("portRemote")).intValue();
				InetSocketAddress replicaSocket = new InetSocketAddress(ip,portServer);
				repIpPorts.add(replicaSocket);
			}
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}

		return repIpPorts;
	}
}
