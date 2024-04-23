import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TFTPServer {

    private final DatagramSocket socket;
    private static final byte[] octetBytes = "octet".getBytes();
    private static final int bufSize = 516;
    private static final String fileDirectory = "Files/";
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DAT = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERR = 5;

    private String filename;
    private byte[] fileBuf;

    public TFTPServer(DatagramSocket socket) {
        fileBuf = new byte[0];
        this.socket = socket;
    }

    /**
     * main receive and send loop
     *  - handles message based on opcode switch statement
     *  TODO: implement threads for multiple clients
     */
    public void receiveAndSend() {
        System.out.println("TFTP server running");
        byte opcode;
        byte[] buf;
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
                //System.out.println("message received: "+message);

                //System.out.println(Arrays.toString(data));
                //System.out.println(opcode);

                switch (opcode) {
                    case OP_RRQ: // Read Request
                    case OP_WRQ: // Write Request
                        filename = getFileName(packet);
                        buf = handleRequest(packet, opcode, filename);
                        break;
                    case OP_DAT: // Data
                        System.out.println("DATA blocknumber: "+getBlockNumber(data));
                        buf = handleDataPacket(packet, filename);
                        break;
                    case OP_ACK: // Acknowledgement

                        System.out.println("ACK blocknumber: "+getBlockNumber(data));
                        //buf = handleAck(blockNumber);
                        if (getBlockNumber(data) > 0) {
                            buf = getDataPacket(filename, getBlockNumber(data) + 1);
                        }
                        break;
                    case OP_ERR: // Error
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

    /*
     * Handles request packets, by calling their respective method handlers
     *  - read requests get answered by first data packet
     *  - write requests get answered by ACK 0
     */
    private byte[] handleRequest(DatagramPacket packet, byte opcode, String filename) {

        System.out.println((opcode == 1 ? "Read request" : "Write request")+ " received for "+filename);
        if (opcode == 1){
            return(getDataPacket(filename, 1));
        } else {
            return getAck(0);
        }
    }

    /**
     * reads request packet filename bytes to string
     * @param packet
     * @return filename
     */
    private String getFileName(DatagramPacket packet){
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
        return filename;
    }

    /**
     * Reads data packet into a file buffer
     * @param packet
     * @param filename
     * @return ACK packet buffer
     */
    private byte[] handleDataPacket(DatagramPacket packet, String filename) {

        byte[] data = getData(packet);
        byte[] oldBuf = fileBuf;
        byte[] newBuf = Arrays.copyOf(oldBuf, oldBuf.length + data.length); //makes new list with old file buffer + padding of new data to be entered
        System.arraycopy(data, 0, newBuf, oldBuf.length, data.length); //concatenating old buffer with new data
        fileBuf = newBuf;
        if (packet.getData()[515] == 0){ //last bit is null
            System.out.println("Final DATA packet");
            readToFile(fileBuf, filename);
        }


        return getAck(getBlockNumber(packet.getData())); //return ACK for retransmission
    }

    /**
     * reads data portion of a data packet
     * @param packet
     * @return data buf
     */
    private static byte[] getData(DatagramPacket packet) {
        int to = packet.getData().length;
        //getting new data buffer data[4:end]
        // TODO: make pruning functional, caused by making fresh buf each loop
        /*
        if (packet.getData()[515] == 0){ //smaller than 516 bytes
             //need to prune before adding line of nulls
            for(int i = 4; i < packet.getData().length-4; i++){ //get index of first nullbyte
                if(packet.getData()[i] == 0){
                    to = i;
                    break;
                }
            }
        }
        */
        byte[] data = Arrays.copyOfRange(packet.getData(), 4, to);
        return data;
    }

    /**
     * reads full file buffer into a file on server's directory
     * @param fileBuf
     * @param filename
     */
    private void readToFile(byte[] fileBuf, String filename) {
        // create new file if not existing
        try {
            File file = new File(fileDirectory + filename);
            if (file.createNewFile()){
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println(file.getName() + " already exists, overwiting.");
            }
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(fileBuf);
            outputStream.close();
            System.out.println("Wrote to " + file.getName());
        } catch (IOException e) {
            System.out.println("Error occurred in readToFile");
            e.printStackTrace();
        }
    }

    /**
     * generates ACK packet buffer from a blocknumber
     * @param blockNumber
     * @return
     */
    private byte[] getAck(int blockNumber) {

        byte[] ackBuf = new byte[4];
        byte[] blockBuf = blockNumbertoBytes(blockNumber);
        ackBuf[1] = 4;
        ackBuf[2] = blockBuf[0];
        ackBuf[3] = blockBuf[1];

        return ackBuf;
    }

    /**
     * generates data packet buffer header from a filename and block number
     * @param filename
     * @param blockNumber
     * @return
     */
    private byte[] getDataPacket(String filename, int blockNumber) {
        byte[] fileData;
        byte[] blockBuf = blockNumbertoBytes(blockNumber);

        try {
            fileData = Files.readAllBytes(Paths.get(fileDirectory + filename));
            if (blockNumber == ((fileData.length / 512) + 2)){ // ACK call was acknowledging last packet in file
                System.out.println("Last packet in file");
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
            dataPacket[2] = blockBuf[0];
            dataPacket[3] = blockBuf[1];

            System.arraycopy(segment, 0, dataPacket, 4, segment.length);
            return dataPacket;
        } catch (IOException e) {
            e.printStackTrace();
            return getErrorPacket(filename);
        }

    }

    /**
     * generates error packet header
     *  - only handles file not found errors, as per assignment spec
     * @param filename
     * @return
     */
    private byte[] getErrorPacket(String filename){
        byte[] errorMsg = ("No such file on server: "+ filename).getBytes();
        byte[] errorBuf = new byte[5 + errorMsg.length];
        errorBuf[0] = 0;
        errorBuf[1] = 5;
        errorBuf[2] = 0;
        errorBuf[3] = 1;
        System.arraycopy(errorMsg, 0, errorBuf, 4, errorMsg.length);
        errorBuf[errorBuf.length-1] = 0;
        return errorBuf;
    }

    /**
     * gets block number as an int from a pair of bytes
     * @param packetData
     * @return
     */
    private int getBlockNumber(byte[] packetData) {
        return (((packetData[2] & 0xff) << 8) | (packetData[3] & 0xff));
    }

    /**
     * gets blocknumber as a pair of bytes from an int
     * @param blockNumber
     * @return
     */
    private byte[] blockNumbertoBytes(int blockNumber){
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort((short) blockNumber);
        return bb.array();
    }


    public static void main(String[] args) throws SocketException {

        DatagramSocket socket = new DatagramSocket(9998);
        TFTPServer server = new TFTPServer(socket);
        server.receiveAndSend();
    }
}