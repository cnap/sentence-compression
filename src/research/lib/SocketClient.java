package research.lib;

import java.io.*;
import java.net.*;

/**
 * used for querying a Google n-gram server, which serves 0 or 1 depending on
 * whether an n-gram appears in the Google n-gram corpus.
 * 
 * adapted from
 * http://java.sun.com/developer/onlineTraining/Programming/BasicJava2
 * /Code/SocketClient.java
 * 
 * @author Courtney Napoles
 * 
 */
//
public class SocketClient {

	Socket socket = null;
	PrintWriter out = null;
	BufferedReader in = null;
	String host;
	int port;

	public SocketClient(String s, int i) throws UnknownHostException, IOException { 
		host = s;
		port = i;
		listenSocket();
	}

	public void close() {
		try { socket.close(); }
		catch(Exception e) { System.err.println("Error closing ngram socket"); }
	}

	public void listenSocket() throws UnknownHostException, IOException {
			socket = new Socket(host, port);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	/**
	 * return 1 if that ngram is present in Google n-grams (up to 5-grams)
	 * 
	 * @param s
	 * @return
	 */
	public int hasNgram(String s) {
		String t = "";
		try { t = in.readLine(); }
		catch (Exception e) {e.printStackTrace(); return 0; }
		return Integer.parseInt(t);
	}

	public String getProbability(String s) {
		String t = "";
		try { t = in.readLine(); }
		catch (Exception e) {e.printStackTrace(); }
		return t;
	}
}
