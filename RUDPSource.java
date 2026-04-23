import java.io.*;
import java.net.*;

public class RUDPSource {

    private static final int TIMEOUT_MS = 600;
    private static final int MAX_RETRIES = 200;

    public static void main(String[] args) {
        if (args.length != 4 || !"-r".equals(args[0]) || !"-f".equals(args[2])) {
            System.out.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            return;
        }

        String hostPart = args[1];
        String fileName = args[3];

        int colon = hostPart.lastIndexOf(':');
        if (colon == -1) {
            System.out.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            return;
        }

        String recvHost = hostPart.substring(0, colon);
        int recvPort = Integer.parseInt(hostPart.substring(colon + 1));

        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            System.out.println("File not found: " + fileName);
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(recvHost);
            long fileSize = file.length();

            sendAndWaitForAck(socket, address, recvPort,
                    RUDPCommon.buildInit(file.getName(), fileSize), 0,
                    "[CONTROL TRANSMISSION]: INIT | " + file.getName());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[RUDPCommon.MAX_PAYLOAD];
                int bytesRead;
                int seq = 1;
                int start = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] dataPacket = RUDPCommon.buildData(seq, start, buffer, bytesRead, fileSize);

                    sendAndWaitForAck(socket, address, recvPort, dataPacket, seq,
                            "[DATA TRANSMISSION]: " + start + " | " + bytesRead);

                    start += bytesRead;
                    seq++;
                }

                sendAndWaitForAck(socket, address, recvPort,
                        RUDPCommon.buildEnd(seq, fileSize), seq,
                        "[CONTROL TRANSMISSION]: END");
            }

            System.out.println("[COMPLETE]");
        } catch (Exception e) {
            System.out.println("Sender error: " + e.getMessage());
        }
    }

    private static void sendAndWaitForAck(DatagramSocket socket, InetAddress address, int port,
                                          byte[] packet, int expectedAckSeq, String logMsg) throws IOException {

        DatagramPacket out = new DatagramPacket(packet, packet.length, address, port);
        byte[] ackBuffer = new byte[2048];
        int attempts = 0;

        while (true) {
            socket.send(out);
            System.out.println(logMsg);

            attempts++;
            if (attempts > MAX_RETRIES) {
                throw new IOException("Too many retransmissions for seq " + expectedAckSeq);
            }

            try {
                DatagramPacket in = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(in);

                RUDPCommon.Packet ack = RUDPCommon.parse(in.getData(), in.getLength());
                if (ack.checksumOk && ack.type == RUDPCommon.TYPE_ACK && ack.seq == expectedAckSeq) {
                    return;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[TIMEOUT]: seq " + expectedAckSeq + " -> retransmitting");
            }
        }
    }
}