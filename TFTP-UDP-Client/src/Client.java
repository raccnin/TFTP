import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {

    private DatagramSocket socket;
    private InetAddress address;

    public Client(DatagramSocket socket, InetAddress address) {
        this.socket = socket;
        this.address = address;
    }


    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Client serverAddress");
            System.exit(1);
        }
        System.out.println(args[0]);
    }
}
