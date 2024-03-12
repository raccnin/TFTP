import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class TFTPServer {

    private DatagramSocket socket;
    private static final byte[] octetBytes = "octet".getBytes();
    private static final int bufSize = 256;
    private byte[] buf = new byte[bufSize];
    private byte[] fileBuf;
    private static final String fileDirectory = "./../Files";

    public TFTPServer(DatagramSocket socket) {
        this.socket = socket;
    }

    /*
     * Request Packet
     *  2 bytes | String   | 1 byte | String | 1 byte
     * ---------|----------|--------|--------|--------
     *  opcode  | filename | 0      | Mode   | 0
     */
    private byte[] handleRequest(DatagramPacket packet, byte opcode) {
        System.out.println(opcode == 1 ? "RRQ" : "WRQ");
        byte[] data = packet.getData();
        int filenameSize = 0;
        for (int i = 2; i < data.length-2; i++) {
            filenameSize ++;
            if (data[i+1] == (byte) 0){
                break;
            }
        }
        byte[] filenameBytes = new byte[filenameSize];
        for (int i = 2; i < filenameSize + 2; i++) {
            filenameBytes[i-2] = data[i];
        }
        String filename = new String( filenameBytes, 0);
        if (opcode == 1){
            return(getData(filename, 0));
        } else {
            return getAck(packet.getAddress(), packet.getPort(), (byte) 0);
        }
    }

    /*
     * Data Packet
     *  2 bytes | 2 bytes  | n bytes |
     * ---------|----------|---------|
     *  opcode  | BlockNum | Data
     */
    private byte[] getData(String filename, int blockNumber) {
        System.out.println("Reading: "+filename);
        return new byte[bufSize];
    }
    /*
     * ACK Packet
     *  2 bytes | 2 bytes  |
     * ---------|----------|
     *  opcode  | blockNum |
     */
    private byte[] getAck(InetAddress address, int port, byte blockNumber) {
        byte[] ackBuf = new byte[4];
        ackBuf[1] = 4;
        ackBuf[3] = blockNumber;
        return ackBuf;
    }

    private byte[] handleDataPacket(DatagramPacket packet) {
        return null;
    }
    private byte[] handleAck(DatagramPacket packet) {
        return null;
    }
    private byte[] handleError(DatagramPacket packet) {
        return null;
    }
    private byte[] getResponseHeader(DatagramPacket packet) {
        byte opcode = packet.getData()[1];
        switch (opcode) {
            case 1:
            case 2:
                return(handleRequest(packet, opcode));
            case 3:
                return(handleDataPacket(packet));
            case 4:
                return(handleAck(packet));
            case 5:
                return(handleError(packet));
            default:
                System.out.println("incorrect opcode received");
                return null;
        }
    }
    public void receiveAndSend() {
        System.out.println("TFTP server running");
        while (true) {
            try {
                buf = new byte[bufSize];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress clientAddr = packet.getAddress();

                //blocking annoying packets on home network
                // TODO: Remember to remove lol
                if (Objects.equals(clientAddr.toString(), "/192.168.0.57")){
                    continue;
                }

                int clientPort = packet.getPort();
                System.out.println("packet received from "+clientAddr+":"+clientPort);
                //System.out.println(Arrays.toString(data));
                //System.out.println(opcode);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("message received: "+message);

                buf = getResponseHeader(packet);
                packet = new DatagramPacket(buf, buf.length, clientAddr, clientPort);
                socket.send(packet);
                System.out.println("Replied to client");
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws SocketException {
        DatagramSocket socket = new DatagramSocket(9999);
        TFTPServer server = new TFTPServer(socket);
        server.receiveAndSend();
    }
}
