/* The object that will serialize into JSON and synchronize with client.
 * Stores user status and game status.
 *
 * */
import com.google.gson.Gson;
import org.java_websocket.WebSocket;

import java.io.Serializable;

public class ClientStatus implements Serializable {
    User user;
    GameObject game;
    int peerId; // peer connection id
    String status; // online, offline, playing, etc. Server will use this to determine the usage of message.
    int sessionId;
    String sessionToken;
    transient WebSocket conn;
    int connectionId;
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
        if (this.conn != null) {
            this.conn.send(this.serialize());
        }
    }
}
