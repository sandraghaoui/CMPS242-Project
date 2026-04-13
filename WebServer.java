import java.io.*;
import java.net.*;

public class WebServer {

    public static void main(String[] args) throws Exception {

        int port = 6789;

        // Creating a listening ServerSocket 
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server started on port " + port + "...");

        // Infinite loop so the server keeps running
        while (true) {

            // Wait for a client to connect
            Socket connectionSocket = serverSocket.accept();

            // Creating a request handler for this connection
            HttpRequest request = new HttpRequest(connectionSocket);

            // Creating a new thread to handle the request
            Thread thread = new Thread(request);

            // Start the thread 
            thread.start();
        }
    }
}


class HttpRequest implements Runnable {

    
    final String CRLF = "\r\n";

    Socket socket;

    
    public HttpRequest(Socket socket) {
        this.socket = socket;
    }

   
    public void run() {
        try {
            processRequest(); 
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    
      //This method processes the HTTP request
     
    private void processRequest() throws Exception {   

        // Get input and output streams from the socket
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());

        
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        // Read the HTTP request line
        String requestLine = br.readLine();

        // Print it in console
        System.out.println();
        System.out.println("Request Line:");
        System.out.println(requestLine);

        // Read and print all header lines
        String headerLine;

        while ((headerLine = br.readLine()).length() != 0) {
            System.out.println(headerLine);
        }

      
        os.close();
        br.close();
        socket.close();
    }
}