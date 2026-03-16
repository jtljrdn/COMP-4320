/**
 * myFirstUDPServer.java
 * @author Jordan Lee
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class myFirstUDPServer {
    private static Map<Short, Item> loadCSV() throws IOException {
        Map<Short, Item> catalog = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("src/data.csv"))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    // Strip UTF-8 BOM if present
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    firstLine = false;
                }
                String[] parts = line.split(",", 3);
                if (parts.length < 3) continue;
                short id = Short.parseShort(parts[0].trim());
                String name = parts[1].trim();
                short price = Short.parseShort(parts[2].trim());
                catalog.put(id, new Item(id, name, price));
            }
        }
        return catalog;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Parameter(s): <Port>");
        }

        // Load catalog before receiving packets
        Map<Short, Item> catalog = loadCSV();

        int port = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("UDP Server listening on " + port + "...");

        byte[] receiveBuffer = new byte[1024];

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            byte[] bytes = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, bytes, 0, receivePacket.getLength());

            System.out.println("Received packet from " + receivePacket.getAddress() + ":" + receivePacket.getPort());

            // Parse request header
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            short requestNumber = buffer.getShort();
            short tml = buffer.getShort();

            // Error: if received bytes != TML, send Request # | -1
            if (receivePacket.getLength() != tml) {
                byte[] errorResponse = new byte[4];
                errorResponse[0] = (byte) (requestNumber >> 8);
                errorResponse[1] = (byte) (requestNumber & 0xFF);
                errorResponse[2] = (byte) 0xFF;
                errorResponse[3] = (byte) 0xFF;
                DatagramPacket errorPacket = new DatagramPacket(errorResponse, errorResponse.length,
                        receivePacket.getAddress(), receivePacket.getPort());
                socket.send(errorPacket);
                System.out.println("ERROR: TML (" + tml + ") does not match received byte count (" + receivePacket.getLength() + "). Sent error response.");
                continue;
            }

            // Log request byte-by-byte
            System.out.print("Request bytes: ");
            for (byte b : bytes) {
                System.out.printf("0x%02X ", b);
            }
            System.out.println();

            // Decode pairs: Request # (2) | TML (2) | Q1 (2) | C1 (2) | ... | 0xFFFF (2)
            ArrayList<QuantityCodePair> pairs = new ArrayList<>();
            while (buffer.remaining() >= 2) {
                short quantity = buffer.getShort();
                if (quantity == -1) break; // 0xFFFF terminator
                if (buffer.remaining() < 2) break;
                short code = buffer.getShort();
                pairs.add(new QuantityCodePair(quantity, code));
            }

            System.out.println("Received " + pairs.size() + " pair(s).");

            // Build response:
            // Request # (2) | TML (2) | TC (4) | L1 (1) | D1 (L1) | CS1 (2) | Q1 (2) | ... | 0xFFFF (2)
            String notAvailable = "Article Not Available";

            // Calculate response TML
            int responseTml = 2 + 2 + 4; // Request# + TML + TC
            for (QuantityCodePair pair : pairs) {
                Item item = catalog.get(pair.getCode());
                String desc = (item != null) ? item.getName() : notAvailable;
                responseTml += 1 + desc.getBytes().length + 2 + 2; // L_i (1 byte) + D_i + CS_i + Q_i
            }
            responseTml += 2; // 0xFFFF terminator

            // Compute TC
            int TC = 0;
            for (QuantityCodePair pair : pairs) {
                Item item = catalog.get(pair.getCode());
                if (item != null) {
                    TC += pair.getQuantity() * item.getPrice();
                }
            }

            // Build response buffer
            ByteBuffer response = ByteBuffer.allocate(responseTml).order(ByteOrder.BIG_ENDIAN);
            response.putShort(requestNumber);
            response.putShort((short) responseTml);
            response.putInt(TC);
            for (QuantityCodePair pair : pairs) {
                Item item = catalog.get(pair.getCode());
                String desc = (item != null) ? item.getName() : notAvailable;
                short unitCost = (item != null) ? item.getPrice() : 0;
                response.put((byte) desc.getBytes().length); // L_i (1 byte)
                response.put(desc.getBytes());                // D_i
                response.putShort(unitCost);                  // CS_i
                response.putShort(pair.getQuantity());        // Q_i
            }
            response.putShort((short) -1); // 0xFFFF terminator

            // Send response
            byte[] responseBytes = response.array();
            DatagramPacket sendPacket = new DatagramPacket(responseBytes, responseBytes.length,
                    receivePacket.getAddress(), receivePacket.getPort());
            socket.send(sendPacket);

            // Log response byte-by-byte
            System.out.print("Response bytes: ");
            for (byte b : responseBytes) {
                System.out.printf("0x%02X ", b);
            }
            System.out.println();
        }
    }
}
