import java.io.*;
import java.net.*;
 
public class Client {
    public static void main(String[] args) throws IOException {
         
        if (args.length != 2) {
            System.err.println(
                "Usage: java Client <host name> <port number>");
            System.exit(1);
        }
 
        String host = args[0];
        int portNumber = Integer.parseInt(args[1]);

        /* 1. Create a socket that connects to the server (identified by the host name and port number) */
        Socket clientSoc = new Socket(host, portNumber);

        /* 2. Get handles to the input and output stream of the socket */
        PrintWriter out = new PrintWriter(clientSoc.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSoc.getInputStream()));
        
        /* 3. Get a handle to the standart input stream to get the user's input (that needs to be sent over to the server) */
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String stdInput;
        /* 4a. Block until the user enters data to the standard input stream */
        /* 4b. Write the users input the input stream of the socket (sends data to the server) */
        /* 4c. Read the output stream of the socket (reads data sent by the server) */
        while ((stdInput = stdIn.readLine()) != null) {
            out.println(stdInput);
            System.out.println("Text received --> " + in.readLine());
        }
    }
}
