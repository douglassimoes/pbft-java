package pbft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReplicaClientSide {

	// This is object is responsible for receiving incoming connections
	private ServerSocket serverSocket;

	private ConcurrentLinkedQueue<String> messages;
	
	private ArrayList<InetSocketAddress>  replicas;
	
	private BufferedReader bufferedReader;

	private BufferedWriter bufferedWriter;
	
	// For now first integer is Message Id and the Second is the counter of how many replies were received
	//TODO Fix that
	private ConcurrentHashMap<Integer,Integer> requestedMessages;
	
	private int n;
	private int f;

	public ReplicaClientSide(InetSocketAddress serverInetAddress, ArrayList<InetSocketAddress> remoteReplicas) throws IOException {

		ServerSocket serverSocket = new ServerSocket(serverInetAddress.getPort());
		this.serverSocket = serverSocket;

		this.messages = new ConcurrentLinkedQueue<String>();
		
		this.replicas = remoteReplicas;
		this.n = remoteReplicas.size();
		this.f = (n-1)/3;
		
		this.requestedMessages = new ConcurrentHashMap<Integer,Integer>();
		
		for(InetSocketAddress remoteReplica : remoteReplicas) {
			String messageFormula = "127.0.0.1"+":"+serverSocket.getLocalPort()+":"+"{msgid=1;type=0}"+":"+"127.0.0.1"+":"+remoteReplica.getPort();	
			this.messages.offer(messageFormula);
		}
	}

	// Method responsible for keeping the server listening
	public void listenForMessage() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				String msgFromReplicas;

				System.out.println("Starting server "+serverSocket.getLocalPort());
				// Keep listening until the server socket is closed

				while(!serverSocket.isClosed()) {
					Socket socket = null;
					try {
						// This is a blocking method, the Server will be halted here until a client connects 
						// However when a client Connects the socket object is returned which can be used to communicate with the client
						socket = serverSocket.accept();
						System.out.println("A new replica has connected!");

						// In Java there a two types of Streams a Character Stream and a Byte Stream,
						// in this case we want a character string because we are sending text messages.
						// In java CharacterStreams end with "Writer" and ByteStreams end with Stream.
						bufferedReader = new BufferedReader( new InputStreamReader(socket.getInputStream()));

						msgFromReplicas = bufferedReader.readLine();
						System.out.println("Message Received! : "+msgFromReplicas);

						handleMessage(msgFromReplicas);
					} catch (IOException e) {
						closeEverything(socket, bufferedReader, bufferedWriter);
					}
				}
			}
		}).start();
	}
	
	private void handleMessage(String msgReceived) {
		String[] message_parts = msgReceived.split(":");
		String senderIp = message_parts[0];
		int senderPort = Long.valueOf(message_parts[1]).intValue();
		String content = message_parts[2];
		String[] content_parts = content.split(";");
		int msgId = Integer.valueOf(content_parts[0].split("=")[1]); 
		String msgtypeText = content_parts[1].split("=")[1].replace("}","");
		String receiverIp = message_parts[3];
		int receiverPort = Long.valueOf(message_parts[4]).intValue();
		
		PBFTMessageType msgType = PBFTMessageType.fromValue(msgtypeText);
		
		switch (msgType) {
		case REPLY: 
			handleReply(senderIp, senderPort, msgId);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + msgType);
		}
	}

	private void handleReply(String senderIp, int senderPort, int msgId) {
		if(this.requestedMessages.get(msgId) == null) {
			System.out.println("This message was already correctly executed by f+1 nodes!");
		} else {
			int repplyMsgCount = this.requestedMessages.get(msgId);
			this.requestedMessages.put(msgId,repplyMsgCount+1);
			if(repplyMsgCount >= f+1) {
				// The client has to receive f+1 identical replies from different replicas before accepting the result.
				// This is because there can be at most f faulty replicas that may refuse to reply or reply with incorrect messages
				System.out.println("REQUEST "+msgId+" FINISHED!");
				this.requestedMessages.remove(msgId);
			}
		}
	}

	private void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
		try {
			if(bufferedReader != null) {
				bufferedReader.close();
			}
			if(bufferedWriter != null) {
				bufferedWriter.close();
			}
			if(socket != null) {
				socket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendMessage() {
		Socket socket = null;
		try {
			while(true) {
				String message = this.messages.poll();
				if (message == null) {
					break;
				}
				String[] message_parts = message.split(":");
				String senderIp = message_parts[0];
				int senderPort = Long.valueOf(message_parts[1]).intValue();
				String content = message_parts[2];
				String[] content_parts = content.split(";");
				int msgId = Integer.valueOf(content_parts[0].split("=")[1]); 
				String msgtypeText = content_parts[1].split("=")[1].replace("}","");
				String receiverIp = message_parts[3];
				int receiverPort = Long.valueOf(message_parts[4]).intValue();

				socket = new Socket(receiverIp,receiverPort);
				System.out.println("Socket created!"+"Sending msg "+message);

				while(socket.isConnected()) {
					this.bufferedWriter = new BufferedWriter( new OutputStreamWriter(socket.getOutputStream()));

					this.bufferedWriter.write(message);
					this.bufferedWriter.newLine();
					this.bufferedWriter.flush();
					
					this.requestedMessages.put(msgId,0);
					closeEverything(socket, bufferedReader, bufferedWriter);
				}
			}

	} catch (IOException e) {
		closeEverything(socket, bufferedReader, bufferedWriter);
	}
}

}
