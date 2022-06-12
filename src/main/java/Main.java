

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public Main() {
    }

    public static void main(String[] args) throws IOException {
        Properties property = System.getProperties();
        setDefaults(property);
        loadConfig(property);
        SocketServer socket = new SocketServer(Integer.parseInt(System.getProperty("port")));
        socket.loadFromFile();
        socket.setReuseAddr(true);
        socket.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.warn("Shutdown hook called. Exiting...");

            try {
                socket.exit();
            } catch (InterruptedException | IOException var2) {
                throw new RuntimeException(var2);
            }
        }));
    }

    private static void setDefaults(Properties property) {
        property.setProperty("log4j.configurationFile", "log4j2.xml");
        property.setProperty("port", "16384");
        property.setProperty("userConfig", "userConfig.json");
        property.setProperty("gameHistorySave", "gameHistorySave.json");
    }

    private static void loadConfig(Properties property) {
        System.out.println("Loading config file...");

        try {
            property.load(new FileInputStream("config.properties"));
        } catch (IOException var2) {
            System.out.println("Cannot load config.properties");
        }

    }
}
