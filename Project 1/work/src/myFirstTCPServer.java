/**
 * myFirstTCPServer.java
 * @author Jordan Lee
 */

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class myFirstTCPServer {
    private static Map<Short, Item> loadCSV() throws IOException {
        Map<Short, Item> catalog = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("work/src/data.csv"))) {
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

        // Load catalog before accepting connections
        Map<Short, Item> catalog = loadCSV();
        System.out.println("Loaded " + catalog.size() + " items from catalog.");

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

        // Handle invalid TML (must be at least 4 bytes: req# + TML fields, plus 2 for terminator)
        if (tml < 6) {
            byte[] errorResponse = new byte[4];
            errorResponse[0] = (byte) (requestNumber >> 8);
            errorResponse[1] = (byte) (requestNumber & 0xFF);
            errorResponse[2] = (byte) (0xFF);
            errorResponse[3] = (byte) (0xFF);
            socket.getOutputStream().write(errorResponse);
            System.out.println("ERROR: TML does not match byte count. Sent error response.");
            socket.close();
            serverSocket.close();
            return;
        }

        // Decode pairs: Request # (2) | TML (2) | Q1 (2) | C1 (2) | ... | 0xFFFF (2)
        ArrayList<QuantityCodePair> pairs = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        buffer.getShort(); // skip request number (already read)
        buffer.getShort(); // skip TML (already read)

        while (buffer.remaining() >= 2) {
            short quantity = buffer.getShort();
            if (quantity == -1) break; // 0xFFFF terminator
            if (buffer.remaining() < 2) break;
            short code = buffer.getShort();
            pairs.add(new QuantityCodePair(quantity, code));
        }

        System.out.println("Received " + pairs.size() + " pair(s):");

        // look up each code, compute line cost = quantity * price
        ArrayList<Short> costs = new ArrayList<>();
        for (QuantityCodePair pair : pairs) {
            Item item = catalog.get(pair.getCode());
            if (item != null) {
                short cost = (short) (pair.getQuantity() * item.getPrice());
                costs.add(cost);
                System.out.printf("  %-25s x%d @ $%d = $%d%n",
                        item.getName(), pair.getQuantity(), item.getPrice(), cost);
            } else {
                costs.add((short) -1); // unknown code -> -1
                System.out.println("  Code " + pair.getCode() + " not found in catalog. Returning -1.");
            }
        }

        // Respond to client
        // Request # (2 bytes) ; TML (2 bytes) ; TC (4 bytes) ; L1 (length of D1 2 bytes) ; D1 (Item Name, L1 bytes)
        // CS1 (Cost, 2 bytes) ; Q1 (Quantity, 2 bytes) ; ... ; 0xFFFF (2 bytes)
        int responseTml = 2 + 2 + 4; // requestNumber + TML + TC
        for (QuantityCodePair quantityCodePair : pairs) {
            Item item = catalog.get(quantityCodePair.getCode());
            if (item != null) {
                responseTml += 2 + item.getName().getBytes().length + 2 + 2; // L1 + D1 + CS1 + Q1
            }
        }
        responseTml += 2; // 0xFFFF terminator
        ByteBuffer response = ByteBuffer.allocate(responseTml).order(ByteOrder.BIG_ENDIAN);
        response.putShort(requestNumber);
        response.putShort((short) responseTml);
        int TC = costs.stream().filter(c -> c > 0).mapToInt(c -> c).sum();
        response.putInt(TC);
        for (int i = 0; i < costs.size(); i++) {
            QuantityCodePair pair = pairs.get(i);
            Item item = catalog.get(pair.getCode());
            response.putShort((short) item.getName().length()); // L1
            response.put(item.getName().getBytes()); // D1
            response.putShort(item.getPrice()); // CS1
            response.putShort(pair.getQuantity()); // Q1
        }
        response.putShort((short) -1); // 0xFFFF terminator

        socket.getOutputStream().write(response.array());
        socket.getOutputStream().flush();
        System.out.println("Sent response with " + costs.size() + " cost(s). TML=" + responseTml);

        socket.close();
        serverSocket.close();
    }
}
