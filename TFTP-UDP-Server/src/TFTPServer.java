import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class TFTPServer {

    private DatagramSocket socket;
    private byte[] buf = new byte[256];

    public TFTPServer(DatagramSocket socket) {
        this.socket = socket;
    }

    public void receive() {
        System.out.println("TFTP server running");
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();
                System.out.println("packet received from "+clientAddr+":"+clientPort);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("message received: "+message);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws SocketException {
        DatagramSocket socket = new DatagramSocket(9999);
        TFTPServer server = new TFTPServer(socket);
        server.receive();
    }
}
