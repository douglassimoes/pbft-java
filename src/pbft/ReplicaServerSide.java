package pbft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReplicaServerSide {

	// This is object is responsible for receiving incoming connections
	private ServerSocket serverSocket;

	private ConcurrentLinkedQueue<String> messages;

	private ConcurrentLinkedQueue<String> Pre;

	private HashMap<Integer,InetSocketAddress> replicas;

	private ConcurrentLinkedQueue<InetSocketAddress> clients;

	private BufferedReader bufferedReader;

	private BufferedWriter bufferedWriter;

	// For now first integer is Message Id and the Second is senderPort
	//TODO Fix that
	//TODO Fix The case of Messages with the Same Id
	private HashMap<Integer,Integer> requestedMessages;

	// For now first Integer is Message Id and the Second is Counter of prepares Received
	//TODO Fix that 
	private HashMap<Integer,Integer> preparedMessages;

	// For now first Integer is Message Id and the Second is Counter of commits Received
	//TODO Fix that 
	private HashMap<Integer,Integer> commitedMessages;

	private int n;
	private int f;

	public ReplicaServerSide(InetSocketAddress serverInetAddress, HashMap<Integer,InetSocketAddress> remoteReplicas) throws IOException {

		ServerSocket serverSocket = new ServerSocket(serverInetAddress.getPort());
		this.serverSocket = serverSocket;

		this.messages = new ConcurrentLinkedQueue<String>();

		this.requestedMessages = new HashMap<Integer,Integer>();
		this.preparedMessages = new HashMap<Integer,Integer>();
		this.commitedMessages = new HashMap<Integer,Integer>();

		this.replicas = remoteReplicas;
		this.n = remoteReplicas.size();
		this.f = (n-1)/3;
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
		case REQUEST:
			handleRequest(senderIp,senderPort,msgId);
		case PREPREPARE: 
			handlePreprepare(senderIp,senderPort,msgId);
			break;
		case PREPARE:
			handlePrepare(senderIp,senderPort,msgId);
			break;
		case COMMIT:
			handleCommit(senderIp,senderPort,msgId);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + msgType);
		}
	}


	private void handleRequest(String senderIp, int senderPort, int msgId) {
		// If Request came from Client( or a server that is not present on replicas HashMap)
		// TODO: this.serverSocket.getLocalPort() checks whether this replica is the primary(for now, later View Object is need)
		if(this.replicas.get(senderPort) == null) {
			this.requestedMessages.put(msgId, senderPort);
			if(this.serverSocket.getLocalPort() == 5000) {
				for(InetSocketAddress replica: this.replicas.values()) {
					String message = this.serverSocket.getInetAddress().getLoopbackAddress().getHostAddress()+":"+this.serverSocket.getLocalPort()+":"+"{msgid="+msgId+";type=1}"+":"+senderIp+":"+replica.getPort();
					this.messages.offer(message);
					System.out.println("Message sent! : "+message);
				}
			}
		}		
	}

	private void handlePreprepare(String senderIp, int senderPort, int msgId) {
		// TODO: this.serverSocket.getLocalPort() checks whether this replica is the primary(for now, later View Object is need)
		// Pre-prepare: The primary replica assigns a sequence number to the request and multicasts a pre-prepare message to all other replicas.
		// Each replica needs to receive one pre-prepare message from the primary replica before moving to the prepare phase.	
		if(this.serverSocket.getLocalPort() != 5000 && this.requestedMessages.get(msgId) != null){
			for(InetSocketAddress replica: this.replicas.values()) {
				String message = this.serverSocket.getInetAddress().getLoopbackAddress().getHostAddress()+":"+this.serverSocket.getLocalPort()+":"+"{msgid="+msgId+";type=2}"+":"+senderIp+":"+replica.getPort();
				this.messages.offer(message);
				System.out.println("Message sent! : "+message);
			}
		}
	}

	private void handlePrepare(String senderIp, int senderPort, int msgId) {
		if(this.preparedMessages.get(msgId) == null) {
			this.preparedMessages.put(msgId,1);
		} else {
			int preparedMsgCount = this.preparedMessages.get(msgId);
			this.preparedMessages.put(msgId,preparedMsgCount+1);
			System.out.print(preparedMsgCount);
			if(preparedMsgCount >= 2*f) {
				// Prepare: Each replica multicasts a prepare message to all other replicas, indicating that it agrees with the sequence number assigned by the primary replica.
				// Each replica needs to receive 2f prepare messages from other replicas (in addition to its own prepare message) before moving to the commit phase.
				// This ensures that at least f+1 correct replicas have agreed on the same sequence number for the request.
				for(InetSocketAddress replica: this.replicas.values()) {
					String message = this.serverSocket.getInetAddress().getLoopbackAddress().getHostAddress()+":"+this.serverSocket.getLocalPort()+":"+"{msgid="+msgId+";type=3}"+":"+senderIp+":"+replica.getPort();
					this.messages.offer(message);
					System.out.println("Message sent! : "+message);
				}
			}
		}
	}

	private void handleCommit(String senderIp, int senderPort, int msgId) {
		if(this.commitedMessages.get(msgId) == null) {
			this.commitedMessages.put(msgId,1);
		} else {
			int commitedMsgCount = this.preparedMessages.get(msgId);
			this.commitedMessages.put(msgId,commitedMsgCount+1);
			if(commitedMsgCount >= 2*f) {
				// Commit: Each replica multicasts a commit message to all other replicas, indicating that it is ready to execute the request.
				// Each replica needs to receive 2f commit messages from other replicas (in addition to its own commit message) before moving to the reply phase. 
				// This ensures that at least f+1 correct replicas have committed to execute the request in the same order across views.
				if(this.requestedMessages.get(msgId) != null) {
					String message = this.serverSocket.getInetAddress().getLoopbackAddress().getHostAddress()+":"+this.serverSocket.getLocalPort()+":"+"{msgid="+msgId+";type=4}"+":"+senderIp+":"+this.requestedMessages.get(msgId);
					this.messages.offer(message);
					System.out.println("Message sent! : "+message);
				}
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
				String message = messages.poll();
				if (message == null) {
					break;
				}
				String[] message_parts = message.split(":");
				String senderIp = message_parts[0];
				int senderPort = Long.valueOf(message_parts[1]).intValue();
				String content = message_parts[2];
				String receiverIp = message_parts[3];
				int receiverPort = Long.valueOf(message_parts[4]).intValue();

				socket = new Socket(receiverIp,receiverPort);
				System.out.println("Socket created!"+"Sending msg "+message);

				while(socket.isConnected()) {
					this.bufferedWriter = new BufferedWriter( new OutputStreamWriter(socket.getOutputStream()));

					this.bufferedWriter.write(message);
					this.bufferedWriter.newLine();
					this.bufferedWriter.flush();

					closeEverything(socket, bufferedReader, bufferedWriter);
				}
			}

		} catch (IOException e) {
			closeEverything(socket, bufferedReader, bufferedWriter);
		}
	}


}
