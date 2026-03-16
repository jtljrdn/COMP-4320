/**
 * myFirstUDPClient.java
 * @author Jordan Lee
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class myFirstUDPClient {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Parameter(s): <Destination> <Port>");
        }

        InetAddress addr = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000); // 5 second timeout for lost packets

        System.out.println("UDP Client ready to send to " + addr + ":" + port);

        Random rand = new Random();
        Scanner sc = new Scanner(System.in);
        int requestNumber = rand.nextInt(1000) + 1000; // Random starting value between 1000 and 1999

        while (true) {
            ArrayList<QuantityCodePair> pairs = new ArrayList<>();

            System.out.println("\nEnter quantity/code pairs. Enter -1 for quantity to submit or quit.");
            while (true) {
                System.out.print("Enter quantity: ");
                short quantity = sc.nextShort();
                if (quantity == -1) break;
                if (quantity <= 0) {
                    System.out.println("ERROR: Quantity must be between 0-32767. Please re-enter.");
                    continue;
                }
                System.out.print("Enter code: ");
                short code = sc.nextShort();
                if (code < 0) {
                    System.out.println("ERROR: Code must be between 0-32767. Please re-enter.");
                    continue;
                }
                pairs.add(new QuantityCodePair(quantity, code));
            }

            // If no pairs were entered, treat as quit
            if (pairs.isEmpty()) {
                System.out.println("No pairs entered. Exiting.");
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

            // Send request via UDP
            DatagramPacket sendPacket = new DatagramPacket(requestBytes, requestBytes.length, addr, port);
            socket.send(sendPacket);
            System.out.println("Sent " + pairs.size() + (pairs.size() > 1 ? " pairs " : " pair ") + "to server.");

            // Wait for server response
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                socket.receive(receivePacket);
            } catch (SocketTimeoutException e) {
                System.out.println("ERROR: No response from server (timed out after 5 seconds).");
                requestNumber++;
                continue;
            }

            // Print entire response in hex
            System.out.print("Received response: ");
            for (int i = 0; i < receivePacket.getLength(); i++) {
                System.out.printf("0x%02X ", receivePacket.getData()[i]);
            }
            System.out.println();

            // Parse response from received datagram
            ByteBuffer respBuffer = ByteBuffer.wrap(receivePacket.getData(), 0, receivePacket.getLength())
                    .order(ByteOrder.BIG_ENDIAN);

            short responseNumber = respBuffer.getShort();
            short responseTml = respBuffer.getShort();

            // Check for error response: Request # | -1 (only 4 bytes)
            if (responseTml == -1) {
                System.out.println("Error: the total cost in the response does not match the total computed by the client.");
                requestNumber++;
                continue;
            }

            int TC = respBuffer.getInt();

            int computedTC = 0;
            ArrayList<String[]> rows = new ArrayList<>();
            int itemNum = 1;
            while (respBuffer.remaining() >= 1) {
                int L1 = respBuffer.get() & 0xFF; // L_i is 1 byte (unsigned)
                if (L1 == 0xFF) break; // hit 0xFFFF terminator

                byte[] nameBytes = new byte[L1];
                respBuffer.get(nameBytes);
                String itemName = new String(nameBytes);

                short CS1 = respBuffer.getShort(); // unit price
                short Q1 = respBuffer.getShort();  // quantity

                int lineCost = CS1 * Q1;
                computedTC += lineCost;

                rows.add(new String[]{String.valueOf(itemNum++), itemName, "$" + CS1, String.valueOf(Q1), "$" + lineCost});
            }

            if (computedTC != TC) {
                System.out.println("Error: the total cost in the response does not match the total computed by the client.");
            } else {
                // Print table only if TC check is successful
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
            }

            requestNumber++;
        }

        socket.close();
    }
}
