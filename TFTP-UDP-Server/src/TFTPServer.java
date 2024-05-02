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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TFTPServer implements Runnable{

    private DatagramSocket serverSocket;
    private static final int bufSize = 516;
    private final boolean serverRunning;
    private final ExecutorService pool;
    public TFTPServer(int port) {
        try {
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            // ignore
        }
        pool = Executors.newCachedThreadPool();
        serverRunning = true;
    }

    /**
     * main receive and send loop
     *  - handles message based on opcode switch statement
     *  TODO: implement threads for multiple clients
     */
    @Override
    public void run() {
        System.out.println("TFTP server running");
        byte[] buf;
        DatagramPacket packet;
        try {
            while (serverRunning) {
                buf = new byte[516];
                packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                if (packet.getData()[1] == 1 || packet.getData()[1] == 2) { // initial request TFTP packets
                    TFTPServerThread serverThread = new TFTPServerThread(packet);
                    pool.execute(serverThread);
                }
            }
        } catch (IOException e) {
            //TODO: Handle
        }
    }


    class TFTPServerThread implements Runnable{
        private DatagramSocket socket;
        private DatagramPacket packet;
        private boolean threadRunning;
        private final String filename;
        private byte[] fileBuf;
        static final byte OP_RRQ = 1;
        public static final byte OP_WRQ = 2;
        private static final byte OP_DAT = 3;
        private static final byte OP_ACK = 4;
        private static final byte OP_ERR = 5;
        private static final String fileDirectory = "Files/";

        public TFTPServerThread(DatagramPacket request) {
            Random rand = new Random();
            try {
                socket = new DatagramSocket(rand.nextInt(rand.nextInt((50000 - 10000) + 10000)));
                socket.setSoTimeout(20000);
            } catch (SocketException e) {
                threadRunning = false;
            }
            threadRunning = true;
            packet = request;
            filename = getFileName(request);
            fileBuf = new byte[0];
        }

        @Override
        public void run() {
            try {
                byte[] buf;
                buf = getResponseHeader(packet); // initial interaction response
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                if (buf != null) {
                    packet = new DatagramPacket(buf, buf.length, clientAddress, clientPort);
                }
                socket.send(packet);

                while (threadRunning) { // main receive and send loop
                    buf = new byte[516];
                    packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    buf = getResponseHeader(packet);
                    if (buf == null) {
                        shutdown();
                        break;
                    }
                    packet = new DatagramPacket(buf, buf.length, clientAddress, clientPort);
                    socket.send(packet);
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        private byte[] getResponseHeader(DatagramPacket packet) {
            byte opcode = packet.getData()[1];
            switch (opcode) {
                case OP_WRQ:
                case OP_RRQ:
                    return handleRequest(packet);
                case OP_DAT:
                    return handleData(packet);
                case OP_ACK:
                    return handleAck(packet);
                case OP_ERR:
                default:
                    return null;
            }

        }

        /*
         * Handles request packets, by calling their respective method handlers
         *  - read requests get answered by first data packet
         *  - write requests get answered by ACK 0
         */
        private byte[] handleRequest(DatagramPacket packet) {

            byte opcode = packet.getData()[1];
            System.out.println((opcode == 1 ? "Read request" : "Write request")+ " received for "+filename);
            if (opcode == 1){
                return(getDataPacket(1));
            } else {
                return getAck(0);
            }
        }

        /**
         * generates ACK packet buffer from a blocknumber
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
         * Reads data packet into a file buffer
         */
        private byte[] handleData(DatagramPacket packet) {

            byte[] data = Arrays.copyOfRange(packet.getData(), 4, packet.getLength());
            byte[] oldBuf = fileBuf;
            byte[] newBuf = Arrays.copyOf(oldBuf, oldBuf.length + data.length); //makes new list with old file buffer + padding of new data to be entered
            System.arraycopy(data, 0, newBuf, oldBuf.length, data.length); //concatenating old buffer with new data
            fileBuf = newBuf;
            if (packet.getLength() < 516) { //last bit is null
                System.out.println("Final DATA packet");
                readToFile();
            }

            return getAck(getBlockNumber(packet.getData())); //return ACK for retransmission
        }

        /**
         * reads full file buffer into a file on server's directory
         */
        private void readToFile() {
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
            }
        }

        private byte[] handleAck(DatagramPacket packet) {
            int blockNumber = getBlockNumber(packet.getData());
            return getDataPacket(blockNumber + 1);
        }

        /**
         * generates data packet buffer header from a block number
         */
        private byte[] getDataPacket(int blockNumber) {
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
                return getErrorPacket();
            }

        }

        /**
         * generates error packet header
         *  - only handles file not found errors, as per assignment spec
         */
        private byte[] getErrorPacket(){
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
         * reads request packet filename bytes to string
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
            return new String( filenameBytes);
        }

        /**
         * gets block number as an int from a pair of bytes
         */
        private int getBlockNumber(byte[] packetData) {
            return (((packetData[2] & 0xff) << 8) | (packetData[3] & 0xff));
        }

        /**
         * gets blocknumber as a pair of bytes from an int
         */
        private byte[] blockNumbertoBytes(int blockNumber){
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.putShort((short) blockNumber);
            return bb.array();
        }

        public void shutdown() {
            threadRunning = false;
            if (!socket.isClosed()) {
                socket.close();
            }
        }

    }

    public static void main(String[] args) {
        TFTPServer server = new TFTPServer(9999);
        server.run();
    }
}