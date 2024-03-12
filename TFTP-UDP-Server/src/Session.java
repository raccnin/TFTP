import java.net.InetAddress;

public class Session {
    private String filename;
    private int port;
    private InetAddress address;

    public Session(String filename, int port, InetAddress address) {
        this.filename = filename;
        this.port = port;
        this.address = address;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }
}
