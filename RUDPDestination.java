import java.io.*;
import java.net.*;

public class RUDPDestination {

    public static void main(String[] args) {
        if (args.length != 2 || !"-p".equals(args[0])) {
            System.out.println("Usage: java RUDPDestination -p <recvPort>");
            return;
        }

        int port = Integer.parseInt(args[1]);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("RUDP Destination listening on port " + port + "...");

            byte[] buffer = new byte[4096];

            FileOutputStream fos = null;
            String outputFileName = null;
            int expectedSeq = 1;
            boolean initialized = false;

            while (true) {
                DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(inPacket);

                RUDPCommon.Packet packet = RUDPCommon.parse(inPacket.getData(), inPacket.getLength());

                InetAddress senderAddress = inPacket.getAddress();
                int senderPort = inPacket.getPort();

                if (!packet.checksumOk) {
                    System.out.println("[PACKET ERROR]: checksum failed");
                    continue;
                }

                if (packet.type == RUDPCommon.TYPE_INIT) {
                    outputFileName = RUDPCommon.makeCopyName(packet.fileName);
                    fos = new FileOutputStream(outputFileName);

                    initialized = true;
                    expectedSeq = 1;

                    sendAck(socket, senderAddress, senderPort, packet.seq);

                    System.out.println("[CONTROL RECEPTION]: INIT | " + packet.fileName);
                }

                else if (packet.type == RUDPCommon.TYPE_DATA) {
                    if (!initialized || fos == null) {
                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | DISCARDED");
                        continue;
                    }

                    if (packet.seq == expectedSeq) {
                        fos.write(packet.payload, 0, packet.length);
                        fos.flush();

                        sendAck(socket, senderAddress, senderPort, packet.seq);

                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | OK");

                        expectedSeq++;
                    } else {
                        sendAck(socket, senderAddress, senderPort, packet.seq);

                        System.out.println("[DATA RECEPTION]: " + packet.start + " | " + packet.length + " | DISCARDED");
                    }
                }

                else if (packet.type == RUDPCommon.TYPE_END) {
                    sendAck(socket, senderAddress, senderPort, packet.seq);

                    if (fos != null) {
                        fos.close();
                    }

                    System.out.println("[CONTROL RECEPTION]: END");
                    System.out.println("[COMPLETE]");

                    break;
                }
            }

        } catch (Exception e) {
            System.out.println("Receiver error: " + e.getMessage());
        }
    }

    private static void sendAck(DatagramSocket socket, InetAddress address, int port, int seq) throws IOException {
        byte[] ack = RUDPCommon.buildAck(seq);
        DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, address, port);
        socket.send(ackPacket);

        System.out.println("[ACK SENT]: seq " + seq);
    }
}