/**
 * myFirstTCPClient.java
 * @author Jordan Lee
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
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

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        System.out.println("Client connected to server at " + addr + ":" + port);

        Random rand = new Random();
        Scanner sc = new Scanner(System.in);
        int requestNumber = rand.nextInt(1000, 2000); // Random starting value between 1000 and 1999

        while (true) {
            ArrayList<QuantityCodePair> pairs = new ArrayList<>();

            System.out.println("\nEnter quantity/code pairs. Enter -1 for quantity to submit or quit.");
            while (true) {
                System.out.print("Enter quantity: ");
                short quantity = sc.nextShort();
                if (quantity == -1) break;
                System.out.print("Enter code: ");
                short code = sc.nextShort();
                pairs.add(new QuantityCodePair(quantity, code));
            }

            // If no pairs were entered, treat as quit
            if (pairs.isEmpty()) {
                System.out.println("No pairs entered. Disconnecting.");
                break;
            }

            // Build byte array to send
            // Request # (2 bytes) ; TML (2 bytes) ; Q1 (2 bytes) ; C1 (2 bytes) ; Q2 ; C2 ; ... ; 0xFFFF
            byte[] requestBytes = new byte[2 + 2 + pairs.size() * 4 + 2];
            requestBytes[0] = (byte) (requestNumber >> 8); // Request # high byte
            requestBytes[1] = (byte) (requestNumber & 0xFF); // Request # low byte
            requestBytes[2] = (byte) (requestBytes.length >> 8); // TML high byte
            requestBytes[3] = (byte) (requestBytes.length & 0xFF); // TML low byte
            for (int i = 0; i < pairs.size(); i++) {
                QuantityCodePair pair = pairs.get(i);
                int offset = 4 + i * 4;
                requestBytes[offset]     = (byte) (pair.getQuantity() >> 8);
                requestBytes[offset + 1] = (byte) (pair.getQuantity() & 0xFF);
                requestBytes[offset + 2] = (byte) (pair.getCode() >> 8);
                requestBytes[offset + 3] = (byte) (pair.getCode() & 0xFF);
            }
            requestBytes[requestBytes.length - 2] = (byte) 0xFF;
            requestBytes[requestBytes.length - 1] = (byte) 0xFF;

            // Log the request being sent
            System.out.println("Sending request #" + requestNumber + " with " + pairs.size() + " pair(s) to server:");
            System.out.print("Request bytes: ");
            for (byte b : requestBytes) {
                System.out.printf("0x%02X ", b);
            }
            System.out.println();

            // Send pairs over TCP
            out.write(requestBytes);
            out.flush();
            System.out.println("Sent " + pairs.size() + (pairs.size() > 1 ? " pairs " : " pair ") + "to server.");

            // Wait for server response:
            // Request # (2 bytes) ; TML (2 bytes) ; TC (4 bytes) ; L1 (2 bytes) ; D1 (L1 bytes)
            // CS1 (2 bytes) ; Q1 (2 bytes) ; ... ; 0xFFFF (2 bytes)
            short responseNumber = in.readShort();
            short responseTml = in.readShort();
            int TC = in.readInt();

            if (responseTml == -1) {
                System.out.println("ERROR: Server responded with TML = -1, indicating a mismatch between TML and byte count.");
                break;
            }

            byte[] remaining = new byte[responseTml - 8];
            in.readFully(remaining);

            ByteBuffer respBuffer = ByteBuffer.wrap(remaining).order(java.nio.ByteOrder.BIG_ENDIAN);

            // Print entire response in hex
            System.out.print("Received response: ");
            System.out.printf("0x%02X 0x%02X 0x%02X 0x%02X ", (responseNumber >> 8) & 0xFF, responseNumber & 0xFF,
                    (responseTml >> 8) & 0xFF, responseTml & 0xFF);
            System.out.printf("0x%02X 0x%02X 0x%02X 0x%02X ", (TC >> 24) & 0xFF, (TC >> 16) & 0xFF, (TC >> 8) & 0xFF, TC & 0xFF);
            for (byte b : remaining) {
                System.out.printf("0x%02X ", b);
            }
            System.out.println();

            int computedTC = 0;
            ArrayList<String[]> rows = new ArrayList<>();
            int itemNum = 1;
            while (respBuffer.remaining() >= 2) {
                short L1 = respBuffer.getShort();
                if (L1 == -1) break; // 0xFFFF terminator

                byte[] nameBytes = new byte[L1];
                respBuffer.get(nameBytes);
                String itemName = new String(nameBytes);

                short CS1 = respBuffer.getShort(); // unit price
                short Q1 = respBuffer.getShort();  // quantity

                int lineCost = CS1 * Q1;
                computedTC += lineCost;

                rows.add(new String[]{String.valueOf(itemNum++), itemName, "$" + CS1, String.valueOf(Q1), "$" + lineCost});
            }

            // Print table
            String fmt = "| %-6s | %-20s | %-9s | %-8s | %-13s |%n";
            String divider = "+--------+----------------------+-----------+----------+---------------+";
            System.out.println(divider);
            System.out.printf(fmt, "Item #", "Description", "Unit Cost", "Quantity", "Cost Per Item");
            System.out.println(divider);
            for (String[] row : rows) {
                System.out.printf(fmt, row[0], row[1], row[2], row[3], row[4]);
                System.out.println(divider);
            }
            System.out.printf(fmt, "", "", "", "Total", "$" + computedTC);
            System.out.println(divider);

            if (computedTC != TC) {
                System.out.println("ERROR: Total Cost mismatch! Computed TC = $" + computedTC + " but server sent TC = $" + TC);
            }

            requestNumber++;
        }

        socket.close();
    }
}
