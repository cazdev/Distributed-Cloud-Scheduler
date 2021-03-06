import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


import java.io.*;

public class Client {
	// initialize socket and input output streams
	private static Socket socket = null;
	private BufferedReader input = null;
	private DataOutputStream out = null;
	private BufferedReader in = null;
	

	// constructor to put ip address and port
	public Client(String address, int port) throws IOException {

		// connect to server
		connect(address, port);

		// takes input from keyboard
		input = new BufferedReader(new InputStreamReader(System.in));

		// sends output to the server
		out = new DataOutputStream(socket.getOutputStream());

		// gets input from server
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	private void start () {
	
		// Initial handshake
		sendMessage("HELO");
		readMessage();

		// Send user details
		sendMessage("AUTH " + System.getProperty("user.name"));
		readMessage();

		// read ds-system xml file and populate Server ArrayList
		ArrayList<Server> t = new ArrayList<Server>();
		t = readXML("ds-system.xml");

		// find index of largest server type (most cores)
		int largestServer = findLargest(t);

		// handshake completed
		boolean connected = true;
		
		// Tells client it is ready to recieve commands
		sendMessage("REDY");

		// temp string to hold readMessage data
		// we check the contents of this string, rather than call readMessage() 
		String msg = readMessage();
		
		// SCHEDULES JOB TO LARGEST SERVER
		while (connected){

			if (msg.contains("JCPL")){
				sendMessage("REDY");
				msg = readMessage();
			} else if (msg.contains("NONE")){
				connected = false;
				sendMessage("QUIT");
			}else {
				sendMessage(toLargest(msg, t.get(largestServer)));
				msg = readMessage();

				sendMessage("REDY");
				msg = readMessage();
			} 
		}
		
		// close the connection
		try {

			// QUIT hand-shake, must receive confirmation from server for quit
			if (readMessage().contains("QUIT")){
				input.close();
				out.close();
				socket.close();
			}
				
		} catch (IOException i) {
			System.out.println(i);
		}

		// Exit the program
		System.exit(1);
	}

	// receives string input and the largest server as input
	// schedules job to largest server
	private String toLargest(String job, Server s){
		String[] splitStr = job.split("\\s+");
		return "SCHD " + splitStr[2] + " " + s.getType() + " " + (s.getLimit()-s.getLimit());
		
		// the index [2] represents the job ID from the String "job"
		//		This string is sent by ds-server to the client 
		
	}

	// find index of largest server, relative to number of cores
	private int findLargest(ArrayList<Server> s){
		int lrgNum = 0;
		int lrgIndex = 0;

		for (int i = 0; i < s.size(); i++){

			if (s.get(i).getCores() > lrgNum){
				lrgIndex = i;
				lrgNum = s.get(i).getCores();
			} 
		}

		return lrgIndex;
	}


	/* 
		We have chosen to comment out the console printing portions of
		sendMessage() and readMessage() as it significantly reduces the
		speed of the test files/scripts. 
	*/

	private void sendMessage (String outStr) {
		// send message to server
		byte[] byteMsg = outStr.getBytes();
		try {
			out.write(byteMsg);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Display outgoing message from client
		//System.out.println("OUT: " + outStr);		
	}

	private String readMessage () {
		// read string sent from server
		String inStr = "";
		char[] cbuf = new char[65535];
		try {
			in.read(cbuf);
		} catch (IOException e) {
			e.printStackTrace();
		}
		inStr = new String(cbuf, 0, cbuf.length);

		// Display incoming message from server
		//System.out.println("INC: " + inStr);
		return inStr;
	}

	private static void connect(String address, int port) {
		// try establish a connection
		double secondsToWait = 1;
		int tryNum = 1;
		while (true) {
			try {
				System.out.println("Connecting to server at: " + address + ":" + port);
				socket = new Socket(address, port);
				System.out.println("Connected");
				break; // we connected, so exit the loop
			} catch (IOException e) {
				// reconnect failed, wait.
				secondsToWait = Math.min(30, Math.pow(2, tryNum));
				tryNum++;
				System.out.println("Connection timed out, retrying in  " + (int) secondsToWait + " seconds ...");
				try {
					TimeUnit.SECONDS.sleep((long) secondsToWait);
				} catch (InterruptedException ie) {
					// interrupted
				}
			}
		}
	}

	public static void main(String args[]) throws IOException {
		// Specify Server IP address and Port
		Client client = new Client("127.0.0.1", 50000);
		client.start();
	}

	
	public static ArrayList<Server> readXML(String fileName){
        ArrayList<Server> serverList = new ArrayList<Server>();
		
		try {
			// XML file to read
			File systemXML = new File(fileName);

			// Setup XML document parser
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(systemXML);

			// String converting to normalized form
			doc.getDocumentElement().normalize();
			NodeList servers = doc.getElementsByTagName("server");
			for (int i = 0; i < servers.getLength(); i++) {
				Element server = (Element) servers.item(i);

				// Parse all XML attributes to new Server object
				String type = server.getAttribute("type");
				int limit = Integer.parseInt(server.getAttribute("limit"));
				int bootupTime = Integer.parseInt(server.getAttribute("bootupTime"));
				float hourlyRate = Float.parseFloat(server.getAttribute("hourlyRate"));
				int coreCount = Integer.parseInt(server.getAttribute("coreCount"));
				int memory = Integer.parseInt(server.getAttribute("memory"));
				int disk = Integer.parseInt(server.getAttribute("disk"));
				
				Server s = new Server(type,limit,bootupTime,hourlyRate,coreCount,memory,disk);
				serverList.add(s);
			}

			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return serverList;
    }

	
}