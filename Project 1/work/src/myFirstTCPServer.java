import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class myFirstTCPServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Parameter(s): <Port>");
        }

        // Establish the listen port
        int port = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port + "..." + serverSocket.getLocalSocketAddress());

        Socket socket = serverSocket.accept();
        System.out.println("Client connected: " + socket.getInetAddress());

        DataInputStream in = new DataInputStream(socket.getInputStream());

        // Read the 4-byte header first to get TML
        byte[] header = new byte[4];
        in.readFully(header);
        short requestNumber = (short) (((header[0] & 0xFF) << 8) | (header[1] & 0xFF));
        short tml = (short) (((header[2] & 0xFF) << 8) | (header[3] & 0xFF));

        // Read the rest of the message based on TML
        byte[] rest = new byte[tml - 4];
        in.readFully(rest);

        // Combine into one buffer for parsing
        byte[] bytes = new byte[tml];
        System.arraycopy(header, 0, bytes, 0, 4);
        System.arraycopy(rest, 0, bytes, 4, rest.length);

        // Decode byte array received from client
        // Request # (2 bytes) ; TML (2 bytes) ; Q1 (2 bytes) ; C1 (2 bytes) ; Q2 ; C2 ; ...
        ArrayList<QuantityCodePair> pairs = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        buffer.getShort(); // skip request number (already read)
        buffer.getShort(); // skip TML (already read)

        // Handle tml != byte count case
        if (tml != bytes.length) {
            // Respond with an error message
            // Request # ; -1
            byte[] errorResponse = new byte[4];
            errorResponse[0] = (byte) (requestNumber >> 8); // Request # high byte
            errorResponse[1] = (byte) (requestNumber & 0xFF); // Request # low byte
            errorResponse[2] = (byte) (0xFF); // TML high byte (indicating error)
            errorResponse[3] = (byte) (0xFF); // TML low byte (indicating error)

            socket.getOutputStream().write(errorResponse);
            System.out.println("Error: TML does not match byte count. Sent error response.");
            socket.close();
            serverSocket.close();
            return;
        }

        short quantity, code;
        while (buffer.remaining() >= 2) {
            quantity = buffer.getShort();
            if (quantity == -1) break; // 0xFFFF terminator
            if (buffer.remaining() < 2) break;
            code = buffer.getShort();
            System.out.println(code + " " + quantity);
            pairs.add(new QuantityCodePair(quantity, code));
        }

        for (byte b : bytes) {
            System.out.printf("0x%02X ", b);
        }
        System.out.println();

        System.out.println("Received " + pairs.size() + " pair(s):");
        for (QuantityCodePair pair : pairs) {
            System.out.println("  " + pair);
        }

        socket.close();
        serverSocket.close();
    }
}
