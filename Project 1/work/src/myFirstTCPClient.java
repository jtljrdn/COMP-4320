import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class myFirstTCPClient {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Parameter(s): <Destination> <Port>");
        }

        InetAddress addr = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        Socket socket = new Socket(addr, port);

        ArrayList<QuantityCodePair> pairs = new ArrayList<>();
        Random rand = new Random();
        int requestNumber = rand.nextInt(1000, 2000); // Random # between 1000 and 1999
        Scanner sc = new Scanner(System.in);

        System.out.println("Enter quantity/code pairs. Enter -1 for quantity to quit.");
        while (true) {
            System.out.print("Enter quantity: ");
            short quantity = sc.nextShort();
            if (quantity == -1) break;
            System.out.print("Enter code: ");
            short code = sc.nextShort();
            pairs.add(new QuantityCodePair(quantity, code));
        }

        // Build byte array to send
        // Request # (2 bytes) ; TML (2 bytes) ; Q1 (2 bytes) ; C1 (2 bytes) ; Q2 ; C2 ; ...
        byte[] requestBytes = new byte[2 + 2 + pairs.size() * 4]; // 2 bytes for request #, 2 for TML, 4 for each pair
        requestBytes[0] = (byte) (requestNumber >> 8); // Request # high byte
        requestBytes[1] = (byte) (requestNumber & 0xFF); // Request # low byte
        requestBytes[2] = (byte) (requestBytes.length >> 8); // TML high byte
        requestBytes[3] = (byte) (requestBytes.length & 0xFF); // TML low byte
        for (int i = 0; i < pairs.size(); i++) {
            QuantityCodePair pair = pairs.get(i);
            int offset = 4 + i * 4;
            requestBytes[offset] = (byte) (pair.getQuantity() >> 8); // Quantity high byte
            requestBytes[offset + 1] = (byte) (pair.getQuantity() & 0xFF); // Quantity low byte
            requestBytes[offset + 2] = (byte) (pair.getCode() >> 8); // Code high byte
            requestBytes[offset + 3] = (byte) (pair.getCode() & 0xFF); // Code low byte
        }

        // Log the request being sent
        System.out.println("Sending request #" + requestNumber + " with " + pairs.size() + " pair(s) to server:");
        // Log bytes
        System.out.print("Request bytes: ");
        for (byte b : requestBytes) {
            System.out.printf("%02X ", b);
        }
        System.out.println();


        // Send pairs over TCP
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(requestBytes);
        out.flush();

        System.out.println("Sent " + pairs.size() + " pair(s) to server.");
        socket.close();
    }
}
