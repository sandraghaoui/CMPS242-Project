import java.io.*;
import java.net.*;

/*
 * Sender side of a simple Reliable UDP (RUDP) protocol.
 * Handles sending INIT, DATA, and END packets with stop-and-wait reliability.
 */

public class RUDPSource {

    // Time to wait for ACK before retransmitting (in milliseconds)
    private static final int TIMEOUT_MS = 600;

    // Maximum number of retransmission attempts
    private static final int MAX_RETRIES = 200;

    public static void main(String[] args) {

        // Check correct command format: -r <host:port> -f <filename>
        if (args.length != 4 || !"-r".equals(args[0]) || !"-f".equals(args[2])) {
            System.out.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            return;
        }

        String hostPart = args[1];   // receiver host and port
        String fileName = args[3];   // file to send

        // Split host and port
        int colon = hostPart.lastIndexOf(':');
        if (colon == -1) {
            System.out.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            return;
        }

        String recvHost = hostPart.substring(0, colon);
        int recvPort = Integer.parseInt(hostPart.substring(colon + 1));

        // Check if file exists
        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            System.out.println("File not found: " + fileName);
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {

            // Set timeout for receiving ACKs
            socket.setSoTimeout(TIMEOUT_MS);

            InetAddress address = InetAddress.getByName(recvHost);
            long fileSize = file.length();

            // Send INIT packet with file name and size
            sendAndWaitForAck(
                    socket,
                    address,
                    recvPort,
                    RUDPCommon.buildInit(file.getName(), fileSize),
                    0,
                    "[CONTROL TRANSMISSION]: INIT | " + file.getName()
            );

            try (FileInputStream fis = new FileInputStream(file)) {

                // Buffer for reading file chunks
                byte[] buffer = new byte[RUDPCommon.MAX_PAYLOAD];

                int bytesRead;
                int seq = 1;   // sequence number starts from 1 for data
                int start = 0; // byte offset in file

                // Read file chunk by chunk
                while ((bytesRead = fis.read(buffer)) != -1) {

                    // Build DATA packet
                    byte[] dataPacket = RUDPCommon.buildData(
                            seq,
                            start,
                            buffer,
                            bytesRead,
                            fileSize
                    );

                    // Send DATA packet and wait for ACK
                    sendAndWaitForAck(
                            socket,
                            address,
                            recvPort,
                            dataPacket,
                            seq,
                            "[DATA TRANSMISSION]: " + start + " | " + bytesRead
                    );

                    // Update offset and sequence number
                    start += bytesRead;
                    seq++;
                }

                // Send END packet after all data is sent
                sendAndWaitForAck(
                        socket,
                        address,
                        recvPort,
                        RUDPCommon.buildEnd(seq, fileSize),
                        seq,
                        "[CONTROL TRANSMISSION]: END"
                );
            }

            System.out.println("[COMPLETE]");

        } catch (Exception e) {
            System.out.println("Sender error: " + e.getMessage());
        }
    }

    /*
     * Sends a packet and waits for the correct ACK.
     * Retransmits if timeout occurs.
     */
    private static void sendAndWaitForAck(
            DatagramSocket socket,
            InetAddress address,
            int port,
            byte[] packet,
            int expectedAckSeq,
            String logMsg
    ) throws IOException {

        // Create UDP packet to send
        DatagramPacket out = new DatagramPacket(packet, packet.length, address, port);

        // Buffer to receive ACK
        byte[] ackBuffer = new byte[2048];

        int attempts = 0;

        while (true) {

            // Send packet
            socket.send(out);
            System.out.println(logMsg);

            attempts++;

            // Stop if too many retries
            if (attempts > MAX_RETRIES) {
                throw new IOException("Too many retransmissions for seq " + expectedAckSeq);
            }

            try {
                // Wait for ACK
                DatagramPacket in = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(in);

                // Parse received packet
                RUDPCommon.Packet ack = RUDPCommon.parse(in.getData(), in.getLength());

                // Check if ACK is valid and matches expected sequence number
                if (ack.checksumOk &&
                    ack.type == RUDPCommon.TYPE_ACK &&
                    ack.seq == expectedAckSeq) {

                    System.out.println("[ACK RECEIVED]: seq " + expectedAckSeq);
                    return;
                }

            } catch (SocketTimeoutException e) {
                // Timeout occurred, resend packet
                System.out.println("[TIMEOUT]: seq " + expectedAckSeq + " -> retransmitting");
            }
        }
    }
}
