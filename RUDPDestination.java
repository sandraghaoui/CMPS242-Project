import java.io.*;
import java.net.*;

/*
Receiver side of the Reliable UDP (RUDP) protocol.
Listens for INIT, DATA, and END packets and reconstructs the file.
Uses stop-and-wait logic with sequence numbers and ACKs.
*/

public class RUDPDestination {

    public static void main(String[] args) {

        // Check correct command format: -p <port>
        if (args.length != 2 || !"-p".equals(args[0])) {
            System.out.println("Usage: java RUDPDestination -p <recvPort>");
            return;
        }

        int port = Integer.parseInt(args[1]);

        try (DatagramSocket socket = new DatagramSocket(port)) {

            System.out.println("RUDP Destination listening on port " + port + "...");

            // Buffer for receiving packets
            byte[] buffer = new byte[4096];

            FileOutputStream fos = null; // output file stream
            String outputFileName = null;

            int expectedSeq = 1; // expected sequence number for DATA packets
            boolean initialized = false; // whether INIT has been received

            while (true) {

                // Receive incoming packet
                DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(inPacket);

                // Parse packet using RUDPCommon
                RUDPCommon.Packet packet = RUDPCommon.parse(inPacket.getData(), inPacket.getLength());

                InetAddress senderAddress = inPacket.getAddress();
                int senderPort = inPacket.getPort();

                // Discard packet if checksum is invalid
                if (!packet.checksumOk) {
                    System.out.println("[PACKET ERROR]: checksum failed");
                    continue;
                }

                // Handle INIT packet
                if (packet.type == RUDPCommon.TYPE_INIT) {

                    // Create output file name (copy)
                    outputFileName = RUDPCommon.makeCopyName(packet.fileName);

                    // Open file for writing
                    fos = new FileOutputStream(outputFileName);

                    initialized = true;
                    expectedSeq = 1;

                    // Send ACK for INIT
                    sendAck(socket, senderAddress, senderPort, packet.seq);

                    System.out.println("[CONTROL RECEPTION]: INIT | " + packet.fileName);
                }

                // Handle DATA packet
                else if (packet.type == RUDPCommon.TYPE_DATA) {

                    // If INIT not received yet, discard DATA
                    if (!initialized || fos == null) {
                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | DISCARDED");
                        continue;
                    }

                    // If correct sequence number, accept packet
                    if (packet.seq == expectedSeq) {

                        // Write data to file
                        fos.write(packet.payload, 0, packet.length);
                        fos.flush();

                        // Send ACK
                        sendAck(socket, senderAddress, senderPort, packet.seq);

                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | OK");

                        expectedSeq++;
                    } else {
                        // Out-of-order or duplicate packet, discard but still ACK
                        sendAck(socket, senderAddress, senderPort, packet.seq);

                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | DISCARDED");
                    }
                }

                // Handle END packet
                else if (packet.type == RUDPCommon.TYPE_END) {

                    // Send ACK for END
                    sendAck(socket, senderAddress, senderPort, packet.seq);

                    // Close file if open
                    if (fos != null) {
                        fos.close();
                    }

                    System.out.println("[CONTROL RECEPTION]: END");
                    System.out.println("[COMPLETE]");

                    break; // stop receiving
                }
            }

        } catch (Exception e) {
            System.out.println("Receiver error: " + e.getMessage());
        }
    }

    /*
    Sends an ACK packet with the given sequence number.
    */
    private static void sendAck(DatagramSocket socket, InetAddress address, int port, int seq) throws IOException {

        // Build ACK packet
        byte[] ack = RUDPCommon.buildAck(seq);

        // Send ACK to sender
        DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, address, port);
        socket.send(ackPacket);

        System.out.println("[ACK SENT]: seq " + seq);
    }
}
