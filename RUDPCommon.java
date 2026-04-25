import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public final class RUDPCommon {

    // Packet type values used by the protocol
    public static final byte TYPE_INIT = 1;
    public static final byte TYPE_DATA = 2;
    public static final byte TYPE_END  = 3;
    public static final byte TYPE_ACK  = 4;

    // Maximum number of file bytes inside one DATA packet
    public static final int MAX_PAYLOAD = 1000;

    // Prevent creating objects from this utility class
    private RUDPCommon() {}

    // Stores all fields extracted from a received packet
    public static class Packet {
        public byte type;
        public int seq;
        public int start;
        public int length;
        public long fileSize;
        public String fileName;
        public byte[] payload;
        public boolean checksumOk;
    }

    // Build INIT packet containing file name and file size
    public static byte[] buildInit(String fileName, long fileSize) throws IOException {
        return buildPacket(TYPE_INIT, 0, 0, 0, fileSize, fileName, null);
    }

    // Build DATA packet containing one chunk of file data
    public static byte[] buildData(int seq, int start, byte[] data, int length, long fileSize) throws IOException {
        return buildPacket(TYPE_DATA, seq, start, length, fileSize, null, data);
    }

    // Build END packet to mark the end of transmission
    public static byte[] buildEnd(int seq, long fileSize) throws IOException {
        return buildPacket(TYPE_END, seq, 0, 0, fileSize, null, null);
    }

    // Build ACK packet for a received sequence number
    public static byte[] buildAck(int seq) throws IOException {
        return buildPacket(TYPE_ACK, seq, 0, 0, 0L, null, null);
    }

    // Convert raw received bytes into a Packet object
    public static Packet parse(byte[] raw, int len) throws IOException {
        Packet p = new Packet();

        // Minimum valid packet size
        if (len < 1 + 4 + 4 + 4 + 8 + 2 + 4) {
            p.checksumOk = false;
            return p;
        }

        // Separate packet body from checksum
        byte[] body = Arrays.copyOf(raw, len - 4);
        int expectedCrc = ByteBuffer.wrap(raw, len - 4, 4).getInt();
        int actualCrc = crc32(body);

        // Check whether packet data was corrupted
        p.checksumOk = (expectedCrc == actualCrc);

        // Read packet fields from the body
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(body));
        p.type = dis.readByte();
        p.seq = dis.readInt();
        p.start = dis.readInt();
        p.length = dis.readInt();
        p.fileSize = dis.readLong();

        // Read file name if present
        int nameLen = dis.readUnsignedShort();
        if (nameLen > 0) {
            byte[] nameBytes = new byte[nameLen];
            dis.readFully(nameBytes);
            p.fileName = new String(nameBytes, StandardCharsets.UTF_8);
        } else {
            p.fileName = null;
        }

        // Read payload if present
        if (p.length > 0) {
            p.payload = new byte[p.length];
            dis.readFully(p.payload);
        } else {
            p.payload = new byte[0];
        }

        return p;
    }

    // Create output file name by adding "-copy" before the extension
    public static String makeCopyName(String original) {
        String base = new File(original).getName();
        int dot = base.lastIndexOf('.');
        if (dot == -1) return base + "-copy";
        return base.substring(0, dot) + "-copy" + base.substring(dot);
    }

    // Build the full packet body and append CRC checksum
    private static byte[] buildPacket(byte type, int seq, int start, int length,
                                      long fileSize, String fileName, byte[] payload) throws IOException {
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bodyOut);

        // Write fixed header fields
        dos.writeByte(type);
        dos.writeInt(seq);
        dos.writeInt(start);
        dos.writeInt(length);
        dos.writeLong(fileSize);

        // Write file name length and file name bytes
        byte[] nameBytes = (fileName == null) ? new byte[0] : fileName.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(nameBytes.length);
        if (nameBytes.length > 0) dos.write(nameBytes);

        // Write payload bytes for DATA packets
        if (payload != null && length > 0) {
            dos.write(payload, 0, length);
        }

        dos.flush();

        // Calculate checksum over packet body
        byte[] body = bodyOut.toByteArray();
        int crc = crc32(body);

        // Append checksum to the end of packet
        ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
        packetOut.write(body);
        DataOutputStream finalOut = new DataOutputStream(packetOut);
        finalOut.writeInt(crc);
        finalOut.flush();

        return packetOut.toByteArray();
    }

    // Calculate CRC32 checksum
    private static int crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }
}
