import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    public static void main(String[] args) throws IOException {
        System.setProperties(new Properties() {
            {
                setProperty("log4j.configurationFile", "log4j2.xml");
                setProperty("os.name", "Linux");
            }
        });
        SocketServer socket = new SocketServer(16384);
        socket.setReuseAddr(true);
        socket.start();
    }
}