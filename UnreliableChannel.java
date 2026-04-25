import java.net.*;
import java.util.Random;

public class UnreliableChannel {

    public static void main(String[] args) throws Exception {
        
        int port = Integer.parseInt(args[0]);
        double p = Double.parseDouble(args[1]);
        int minD = Integer.parseInt(args[2]);
        int maxD = Integer.parseInt(args[3]);
        String[] user = new String[2];
        InetAddress[] IP = new InetAddress[2];
        int[] userPort = new int[2];
        int[] received = new int[2];
        int[] lost = new int[2];
        int[] delayed = new int[2];
        long[] totalDelay = new long[2];
        boolean[] ends = new boolean[2];
        user[0] = "A";
        user[1] = "B";
        
        if (args.length >= 5) {
            String dest = args[4];
            int c = dest.lastIndexOf(':');
            IP[1] = InetAddress.getByName(dest.substring(0, c));
            userPort[1] = Integer.parseInt(dest.substring(c + 1));
        }

        Random rnd = new Random();
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[4096];

        while (true) {

            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            socket.receive(dp);

            String message = new String(dp.getData(), 0, dp.getLength()).trim();
            InetAddress senderIP = dp.getAddress();
            int senderPort = dp.getPort();

            int index = -1;
            for (int i = 0; i < 2; i++) {
            if (IP[i] != null && IP[i].equals(senderIP) && userPort[i] == senderPort) {
                index = i;
                break;
                }
            }

            if (index == -1) {
                if (IP[0] == null) index = 0;
                else if (IP[1] == null) index = 1;
                else continue;

                IP[index] = senderIP;
                userPort[index] = senderPort;
            }

            if (message.equals("END")) {
                ends[index] = true;
                if (ends[0] && ends[1]) {
                    printStats(user, received, lost, delayed, totalDelay);
                    socket.close();
                    return;
                }
                continue;
            }

            received[index]++;

            if (rnd.nextDouble() <= p) {
                lost[index]++;
            } else {
                int destIndex = 1 - index;
                if (IP[destIndex] == null) {
                    lost[index]++;
                    continue;
                }
                int delay = (maxD > minD) ? minD + rnd.nextInt(maxD - minD + 1) : minD;
                Thread.sleep(delay);


                byte[] outData = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), 0, outData, 0, dp.getLength());
                socket.send(new DatagramPacket(outData, outData.length, IP[destIndex], userPort[destIndex]));
                delayed[index]++;
                totalDelay[index] += delay;
            }
        }
    }

    private static void printStats(String[] user, int[] received, int[] lost, int[] delayed, long[] totalDelay) {
        for (int i = 0; i < 2; i++)
            System.out.println("Packets received from user " + user[i] + ": " + received[i] + " | Lost: " + lost[i] + " | Delayed: " + delayed[i]);

        System.out.printf("Average delay from %s to %s: %.1f ms.%n", user[0], user[1], delayed[0] > 0 ? (double) totalDelay[0] / delayed[0] : 0.0);
        System.out.printf("Average delay from %s to %s: %.1f ms.%n", user[1], user[0], delayed[1] > 0 ? (double) totalDelay[1] / delayed[1] : 0.0);
    }
}
