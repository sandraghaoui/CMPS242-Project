import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public final class RUDPCommon {
    public static final byte TYPE_INIT = 1;
    public static final byte TYPE_DATA = 2;
    public static final byte TYPE_END  = 3;
    public static final byte TYPE_ACK  = 4;

    public static final int MAX_PAYLOAD = 1000;

    private RUDPCommon() {}

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

    public static byte[] buildInit(String fileName, long fileSize) throws IOException {
        return buildPacket(TYPE_INIT, 0, 0, 0, fileSize, fileName, null);
    }

    public static byte[] buildData(int seq, int start, byte[] data, int length, long fileSize) throws IOException {
        return buildPacket(TYPE_DATA, seq, start, length, fileSize, null, data);
    }

    public static byte[] buildEnd(int seq, long fileSize) throws IOException {
        return buildPacket(TYPE_END, seq, 0, 0, fileSize, null, null);
    }

    public static byte[] buildAck(int seq) throws IOException {
        return buildPacket(TYPE_ACK, seq, 0, 0, 0L, null, null);
    }

    public static Packet parse(byte[] raw, int len) throws IOException {
        Packet p = new Packet();
        if (len < 1 + 4 + 4 + 4 + 8 + 2 + 4) {
            p.checksumOk = false;
            return p;
        }

        byte[] body = Arrays.copyOf(raw, len - 4);
        int expectedCrc = ByteBuffer.wrap(raw, len - 4, 4).getInt();
        int actualCrc = crc32(body);

        p.checksumOk = (expectedCrc == actualCrc);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(body));
        p.type = dis.readByte();
        p.seq = dis.readInt();
        p.start = dis.readInt();
        p.length = dis.readInt();
        p.fileSize = dis.readLong();

        int nameLen = dis.readUnsignedShort();
        if (nameLen > 0) {
            byte[] nameBytes = new byte[nameLen];
            dis.readFully(nameBytes);
            p.fileName = new String(nameBytes, StandardCharsets.UTF_8);
        } else {
            p.fileName = null;
        }

        if (p.length > 0) {
            p.payload = new byte[p.length];
            dis.readFully(p.payload);
        } else {
            p.payload = new byte[0];
        }

        return p;
    }

    public static String makeCopyName(String original) {
        String base = new File(original).getName();
        int dot = base.lastIndexOf('.');
        if (dot == -1) return base + "-copy";
        return base.substring(0, dot) + "-copy" + base.substring(dot);
    }

    private static byte[] buildPacket(byte type, int seq, int start, int length,
                                      long fileSize, String fileName, byte[] payload) throws IOException {
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bodyOut);

        dos.writeByte(type);
        dos.writeInt(seq);
        dos.writeInt(start);
        dos.writeInt(length);
        dos.writeLong(fileSize);

        byte[] nameBytes = (fileName == null) ? new byte[0] : fileName.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(nameBytes.length);
        if (nameBytes.length > 0) dos.write(nameBytes);

        if (payload != null && length > 0) {
            dos.write(payload, 0, length);
        }

        dos.flush();
        byte[] body = bodyOut.toByteArray();
        int crc = crc32(body);

        ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
        packetOut.write(body);
        DataOutputStream finalOut = new DataOutputStream(packetOut);
        finalOut.writeInt(crc);
        finalOut.flush();

        return packetOut.toByteArray();
    }

    private static int crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }
}