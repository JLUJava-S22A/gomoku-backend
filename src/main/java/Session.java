/*
* Object that holds the session data. Including socket connection, user info, and game info.
* Handle the session of 2 users.
* */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;

public class Session {
    String sessionToken = "";
    //    private static Logger logger = LogManager.getLogger(Main.class.getName());
    ClientStatus client1;
    ClientStatus client2;
    int sessionId = -1;
}
