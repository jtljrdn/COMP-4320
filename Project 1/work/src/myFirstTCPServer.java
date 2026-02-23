import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class myFirstTCPServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Parameter(s): <Port>");
        }

        int port = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port + "...");

        Socket socket = serverSocket.accept();
        System.out.println("Client connected: " + socket.getInetAddress());

        DataInputStream in = new DataInputStream(socket.getInputStream());

        int count = in.readInt();
        ArrayList<QuantityCodePair> pairs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            short quantity = in.readShort();
            short code = in.readShort();
            pairs.add(new QuantityCodePair(quantity, code));
        }

        System.out.println("Received " + pairs.size() + " pair(s):");
        for (QuantityCodePair pair : pairs) {
            System.out.println("  " + pair);
        }

        socket.close();
        serverSocket.close();
    }
}
