
import java.io.*;
import java.net.*;

public class Iperfer{
        public static void main(String[] args){
                if(args[0].equals("-c") && args.length == 7){
                        String serverHostName;
                        int portNumber;
                        double time;
                        if(!args[1].equals("-h") || !args[3].equals("-p") || !args[5].equals("-t")){
                                System.err.println(
                                        "Error: missing or additional arguments");
                                System.exit(1);
                        }
                        else{
                                serverHostName = args[2];
                                portNumber = Integer.parseInt(args[4]);
                                if(portNumber < 1024 || portNumber > 65535){
                                        System.err.println(
                                        "Error: port number must be in the range 1024 to 65536");
                                        System.exit(1);
                                }
                                time = Double.parseDouble(args[6]);
                                iperferClient(serverHostName, portNumber, time);
                        }
                }
                else if(args[0].equals("-s") && args.length == 3){
                        int portNumber;
                        if(!args[1].equals("-p")){
                                System.err.println(
                                        "Error: missing or additional arguments");
                                System.exit(1);
                        }
                        else{
                                portNumber = Integer.parseInt(args[2]);
                                if(portNumber < 1024 || portNumber > 65535){
                                        System.err.println(
                                        "Error: port number must be in the range 1024 to 65536");
                                        System.exit(1);
                                }
                                iperferServer(portNumber);
                        }
                }
                else {
                    System.err.println("Error: missing or additional arguments");
                    System.exit(1);
                }
        }

        public static void iperferClient(String serverHost, int port, double time){
                int sentInKB = 0;
                try{
                        Socket clientSoc = new Socket(serverHost, port);
                        byte[] toSend = new byte[1000];
                        double start = System.nanoTime();
			while((System.nanoTime() - start) / 1000000000.0 < time){
                                clientSoc.getOutputStream().write(toSend);
				sentInKB++;
                        }
                        clientSoc.close();
                }catch(UnknownHostException e){
                        System.err.println("Don't know about host" + serverHost);
                        System.exit(1);
                }catch(IOException e){
                        System.err.println("Couldn't get I/O for the connection to " + 
                                serverHost);
                        System.exit(1);
                }
                double rate = ((double)sentInKB) * 8.0 / 1000.0 / time;
                System.out.format("sent=%d KB rate=%.3f Mbs%n", sentInKB, rate);
        }

        public static void iperferServer(int port){
                int receivedDataInB = 0;
		
                try{
			ServerSocket serverSoc = new ServerSocket(port);
			Socket clientSoc = serverSoc.accept();
			byte[] toReceive = new byte[1000];
			double start = System.nanoTime();
			int read = 0;
			while (read >= 0) {
				receivedDataInB += read;
				read = clientSoc.getInputStream().read(toReceive, 0, 1000);
			}
			double time = (System.nanoTime() - start) / 1000000000.0;
			double rate = ((double)receivedDataInB) * 8.0 / 1000000.0 / time;
			System.out.format("received=%d KB rate=%f Mbs%n", receivedDataInB / 1000, rate);
                }catch(IOException e){
                        System.out.println(e.toString());
                }
        }
}

