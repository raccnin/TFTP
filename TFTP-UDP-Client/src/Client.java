import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class Client {

    private DatagramSocket socket;
    private InetAddress address;
    private static final byte[] octetBytes = "octet".getBytes();

    public Client(InetAddress address) throws SocketException {
        Random rand = new Random();
        int port = rand.nextInt(8976)+1025; //number from 1025-9998
        System.out.println("created Client on port: "+Integer.valueOf(port));
        socket = new DatagramSocket(port);
        this.address = address;
    }

    private byte[] handleData(DatagramPacket packet) {
        return null;
    }
    private byte[] handleAck(DatagramPacket packet) {
        System.out.println("Received ACK");
        return null;
    }
    private byte[] handleError(DatagramPacket packet) {
        return null;
    }
    private byte[] getResponseHeader(DatagramPacket packet) {
        byte opcode = packet.getData()[1];
        //System.out.println("Opcode: "+Byte.valueOf(opcode));
        switch (opcode) {
            case 3:
                return handleData(packet);
            case 4:
                return handleAck(packet);
            case 5:
                return handleError(packet);
            default:
                return null;
        }
    }
    public void handleSession(byte[] buf) {
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 9999);
        while (true) {
            try {
                socket.send(packet);

                socket.receive(packet);
                InetAddress serverAddr = packet.getAddress();
                int serverPort = packet.getPort();
                System.out.println("received reply from "+serverAddr+":"+Integer.valueOf(serverPort));
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("message received: "+message);
                //System.out.println(Arrays.toString(packet.getData()));
                buf = getResponseHeader(packet);
                if (buf == null) {
                    break;
                }
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * creating packet for RRQ/WRQ
     * 2 byte opcode (1/2), filename, 0 byte, "octet" in bytes, 0 byte
     */
    private byte[] createRequest(byte opcode, String filename) {
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

    public static void main(String[] args) throws UnknownHostException, SocketException {
        if (args.length < 1) {
            System.out.println("Usage: java Client serverAddress");
            System.exit(1);
        }
        InetAddress address = InetAddress.getByName(args[0]);;
        Client client = new Client(address);

        System.out.println("Welcome to TFTP over UDP");
        Scanner input = new Scanner(System.in);
        System.out.println("Enter Operation Read: 1/Write: 2");
        int opcode = Integer.parseInt(input.nextLine());
        System.out.println("Enter filename: ");
        String filename = input.nextLine();
        System.out.println(
                (opcode == 1 ? "reading ":"writing ")+filename+
                        (opcode == 1 ? " from ":" to ")+address+":9999");

        byte[] request = client.createRequest((byte) opcode, filename);
        client.handleSession(request);

    }

}
