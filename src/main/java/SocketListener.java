import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketListener {
    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(5140)) {

            Socket socket = server.accept();

            DataInputStream inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            String line = "";
            while (true) {

                line = inputStream.readUTF();
                System.out.println(line);
            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
