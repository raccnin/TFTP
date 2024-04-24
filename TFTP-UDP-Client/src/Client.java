import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class Client {

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private static final byte[] octetBytes = "octet".getBytes();
    private static final byte OP_DAT = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERR = 5;
    private static final int bufSize = 516;
    private byte[] fileBuf;
    private String filename;
    private static final String fileDirectory = "Files/";

    public Client(InetAddress serverAddress, int serverPort) throws SocketException {
        Random rand = new Random();
        int port = rand.nextInt((50000-10000))+10000; //number from 10000-50000
        System.out.println("Created Client on port: "+Integer.valueOf(port));
        socket = new DatagramSocket(port);
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        fileBuf = new byte[0];
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void sendAndReceive(byte[] buf) {
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, serverPort);
                socket.send(packet);
                System.out.println("Packet sent to "+serverAddress+":"+serverPort);
                packet = new DatagramPacket(new byte[bufSize], bufSize, serverAddress, serverPort);
                socket.receive(packet);
                serverAddress = packet.getAddress();
                serverPort = packet.getPort();
                System.out.println("Received reply from "+serverAddress+":"+Integer.valueOf(serverPort));
                //String message = new String(packet.getData(), 0, packet.getLength());
                //System.out.println("message received: "+message);
                //System.out.println(Arrays.toString(packet.getData()));
                buf = getResponseHeader(packet);
                if (buf == null) {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /*
     * creating packet for RRQ/WRQ
     * 2 byte opcode (1/2), filename, 0 byte, "octet" in bytes, 0 byte
     */
    private byte[] createRequest(byte opcode) {
        byte[] filenameBytes = filename.getBytes();
        int requestSize = 2 + filename.getBytes().length + 1 + octetBytes.length + 1;
        byte[] request = new byte[requestSize];
        int index = 0;
        request[index] = 0;
        index++;
        request[index] = opcode;
        index++;
        for(int i = 0; i < filenameBytes.length; i++){
            request[index] = filenameBytes[i];
            index++;
        }
        request[index] = 0;
        index++;
        for(int i = 0; i < octetBytes.length; i++){
            request[index] = octetBytes[i];
            index++;
        }
        request[index] = 0;
        return(request);
    }

    private byte[] handleData(DatagramPacket packet) {
        int blockNumber = getBlockNumberInt(packet);
        System.out.println("Received DAT packet #: "+blockNumber);
        appendToBuf(packet);
        if (packet.getLength() < 516){
            System.out.println("last DAT packet received, writing...");
            readBufToFile();
            return null;
        }


        return getAckPacket(blockNumber);
    }
    private void appendToBuf(DatagramPacket packet){
        byte[] data = Arrays.copyOfRange(packet.getData(), 4, packet.getLength());
        byte[] newBuf = Arrays.copyOf(fileBuf, fileBuf.length + data.length);
        System.arraycopy(data, 0, newBuf, fileBuf.length, data.length);
        fileBuf = newBuf;
    }

    private void readBufToFile() {
        try {
            File file = new File(fileDirectory+filename);
            if (file.createNewFile()) {
                System.out.println("File created: "+file.getName());
            } else {
                System.out.println("File already exists, overwiting...");
            }
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(fileBuf);
            outputStream.close();
            System.out.println("Wrote file data to: "+file.getName());
        } catch (IOException e) {
            System.out.println("Error occurred in reading data to file");
            e.printStackTrace();
        }
    }

    private byte[] getAckPacket(int blockNumber) {

        byte[] ackBuf = new byte[4];
        byte[] blockBuf = getBlockNumberBytes(blockNumber);
        ackBuf[0] = 0;
        ackBuf[1] = (byte) 4;
        ackBuf[2] = blockBuf[0];
        ackBuf[3] = blockBuf[1];

        return ackBuf;
    }
    private byte[] handleAck(DatagramPacket packet) {
        int blockNumber = getBlockNumberInt(packet);
        System.out.println("Received ACK packet #: "+blockNumber);

        //send data block of blocknumber + 1

        return getDataPacket(blockNumber+1);
    }

    private byte[] getDataPacket(int blockNumber) {

        byte[] fileData;
        byte[] blockBuf = getBlockNumberBytes(blockNumber);

        try {
            fileData = Files.readAllBytes(Paths.get(fileDirectory+filename));
            if (blockNumber == ((fileData.length / 512) + 2)){
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
            byte[] dataPacket = new byte[4 + segment.length];
            dataPacket[0] = 0;
            dataPacket[1] = 3;
            dataPacket[2] = blockBuf[0];
            dataPacket[3] = blockBuf[1];
            System.arraycopy(segment, 0, dataPacket, 4, segment.length);

            return dataPacket;
        } catch (IOException e) {
            System.out.println("File does not exist on client. Exiting TFTP client");
            e.printStackTrace();
            return null;
        }
    }
    private byte[] handleError(DatagramPacket packet) {

        int errMsgSize = 0;
        for(int i = 4; i < packet.getData().length; i++){
            errMsgSize ++;
            if (packet.getData()[i+1] == (byte) 0){
                break;
            }
        }
        byte[] errMsgBytes = new byte[errMsgSize];
        System.arraycopy(packet.getData(), 4, errMsgBytes, 0, errMsgSize);
        String errMsg = new String(errMsgBytes, 0);
        System.out.println("Error received: "+errMsg+", exiting TFTP client.");
        return null;
    }
    private byte[] getResponseHeader(DatagramPacket packet) {
        byte opcode = packet.getData()[1];
        //System.out.println("Opcode: "+Byte.valueOf(opcode));
        switch (opcode) {
            case OP_DAT:
                return handleData(packet);
            case OP_ACK:
                return handleAck(packet);
            case OP_ERR:
                return handleError(packet);
            default:
                return null;
        }
    }

    private int getBlockNumberInt(DatagramPacket packet){
        byte[] packetData = packet.getData();
        return (((packetData[2] & 0xff) << 8) | (packetData[3] & 0xff));
    }
    private byte[] getBlockNumberBytes(int blockNumber){
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort((short) blockNumber);
        return bb.array();
    }
    public static void main(String[] args) throws UnknownHostException, SocketException {
        if (args.length < 2) {
            System.out.println("Usage: java Client serverAddress, serverPort");
            System.exit(1);
        }
        System.out.println("Welcome to TFTP over UDP");

        InetAddress serverAddress = InetAddress.getByName(args[0]);
        int serverPort = Integer.parseInt(args[1]);
        Client client = new Client(serverAddress, serverPort);

        Scanner input = new Scanner(System.in);
        System.out.println("Enter Operation Read: 1/Write: 2");
        int opcode = Integer.parseInt(input.nextLine());
        System.out.println("Enter filename: ");
        client.setFilename(input.nextLine());
        System.out.println(
                (opcode == 1 ? "Reading ":"Writing ")+client.getFilename()+
                        (opcode == 1 ? " from ":" to ")+serverAddress+":"+serverPort);

        byte[] request = client.createRequest((byte) opcode);
        client.sendAndReceive(request);

    }

}
