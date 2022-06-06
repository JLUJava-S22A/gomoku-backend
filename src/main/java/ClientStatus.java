/* The object that will serialize into JSON and synchronize with client.
 * Stores user status and game status.
 *
 * */
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;

import java.io.Serializable;

public class ClientStatus implements Serializable {
    private static final Logger logger = LogManager.getLogger(Main.class.getName());
    User user;
    GameObject game;
    int peerId = -1; // peer connection id
    int isFirstMove = -1; // 1: first player (black), 0: second player (white)
    String status; // online, offline, playing, etc. Server will use this to determine the usage of message.
    int sessionId = -1;
    String sessionToken = "";
    transient WebSocket conn;
    int chakuIndex = -1;
    int connectionId = -1;
    int timeToLive;
    // Build from construction function.
    ClientStatus() {
    }
    // build from json
    public static ClientStatus fromJson(String json) {
        return new Gson().fromJson(json, ClientStatus.class);
    }
    // Serialization
    public String serialize() {
        Gson gsonParser = new Gson();
        return gsonParser.toJson(this);
    }
    @Override
    public String toString() {
        return this.serialize();
    }
    public void send() {
        this.timeToLive = 45000; // reset TTL
        String message = this.serialize();
        logger.debug("Sending message: " + message);
        if (this.conn != null) {
            this.conn.send(this.serialize());
        }
    }
}
