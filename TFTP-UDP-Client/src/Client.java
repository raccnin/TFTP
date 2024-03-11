import java.io.IOException;
import java.net.*;

public class Client {

    private DatagramSocket socket;
    private InetAddress address;

    public Client(DatagramSocket socket, InetAddress address) {
        this.socket = socket;
        this.address = address;
    }

    public void send(String message) {
        try {
            byte[] buf = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 9999);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws UnknownHostException, SocketException {
        if (args.length < 1) {
            System.out.println("Usage: java Client serverAddress");
            System.exit(1);
        }
        System.out.println(args[0]);
        InetAddress address = InetAddress.getByName(args[0]);
        DatagramSocket socket = new DatagramSocket(9998);
        Client client = new Client(socket, address);
        client.send("blargah");
    }
}
