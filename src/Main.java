import java.io.IOException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class.getName());
    public static void main(String[] args) throws IOException {
        SocketServer socket = new SocketServer(8080);
        socket.start();
    }
}
