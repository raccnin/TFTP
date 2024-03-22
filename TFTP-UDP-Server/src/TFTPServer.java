import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class TFTPServer {

    private DatagramSocket socket;
    private static final byte[] octetBytes = "octet".getBytes();
    private static final int bufSize = 516;
    private byte[] buf = new byte[bufSize];

    private static final String fileDirectory = "./../Files";
    private byte opcode;
    private String currentFilename;

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

        byte[] data = packet.getData();
        int filenameSize = 0;
        for (int i = 2; i < data.length-2; i++) {
            filenameSize ++;
            if (data[i+1] == (byte) 0){
                break;
            }
        }
        byte[] filenameBytes = new byte[filenameSize];
        System.arraycopy(data, 2, filenameBytes, 0, filenameSize + 2 - 2);
        String filename = new String( filenameBytes, 0);
        System.out.println((opcode == 1 ? "Read request" : "Write request")+ " received for "+filename);
        currentFilename = filename;
        if (opcode == 1){
            return(getDataPacket(filename, 1));
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
    private byte[] getDataPacket(String filename, int blockNumber) {
        byte[] fileData;

        try {
            fileData = Files.readAllBytes(Paths.get("src/" + filename));
            if (blockNumber == ((fileData.length / 512) + 2)){ // ACK call was acknowledging last packet in file
                return null;
            }
            int to;
            if (blockNumber > fileData.length / 512) {
                to = (512 * (blockNumber - 1)) + (fileData.length % 512);
            } else {
                to = 512 * blockNumber;
            }
            byte[] segment = Arrays.copyOfRange(fileData, (blockNumber-1) * 512, to);
            byte[] dataPacket = new byte[4+segment.length];
            dataPacket[0] = 0;
            dataPacket[1] = 3;
            dataPacket[2] = 0;
            dataPacket[3] = (byte) blockNumber;

            System.arraycopy(segment, 0, dataPacket, 4, segment.length);
            return dataPacket;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

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
    private byte[] handleAck(int blockNumber) {
        return null;
    }
    private byte[] handleError(DatagramPacket packet) {
        return null;
    }
    private int getBlockNumber(byte[] packetData) {
        return (((packetData[2] & 0xff) << 8) | (packetData[3] & 0xff));
    }

    public void receiveAndSend() {
        System.out.println("TFTP server running");
        while (true) {
            try {
                buf = new byte[bufSize];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                byte[] data = packet.getData();
                opcode = data[1];
                if (opcode > 5 || opcode < 1){
                    continue;
                }
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();
                System.out.println("packet received from "+clientAddr+":"+clientPort);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("message received: "+message);

                //System.out.println(Arrays.toString(data));
                //System.out.println(opcode);

                switch (opcode) {
                    case 1: // Read Request
                    case 2: // Write Request
                        buf = handleRequest(packet, opcode);
                        break;
                    case 3: // Data
                    case 4: // Acknowledgement
                        int blockNumber = getBlockNumber(data);
                        System.out.println("blocknumber: "+blockNumber);
                        //buf = handleAck(blockNumber);
                        if (blockNumber > 0) {
                            buf = getDataPacket(currentFilename, blockNumber + 1);
                        }
                    case 5: // Error
                        break;
                    default:
                        System.out.println("incorrect opcode received");
                        break;
                }
                if (buf == null) {
                    continue;
                }
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
