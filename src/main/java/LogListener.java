import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class LogListener {
    public static void main(String[] args) {
        int port = 5140; // Change this to your desired port

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening for logs on port " + port + "...");

            // Accept incoming connections and handle them
            while (true) {
                // Wait for a client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connected to client");

                // Get the input stream from the client and read the logs
                try (BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String logLine;
                    // Read and print log lines from the client
                    while ((logLine = input.readLine()) != null) {
                        System.out.println("Log: " + logLine);
                    }
                } catch (Exception e) {
                    System.err.println("Error reading from client: " + e.getMessage());
                } finally {
                    clientSocket.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Error with server socket: " + e.getMessage());
        }
    }
}

