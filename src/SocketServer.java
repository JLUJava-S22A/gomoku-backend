import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.util.*;
import java.net.InetSocketAddress;

import com.google.gson.Gson;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class SocketServer extends WebSocketServer {

    public static Queue<ClientStatus> waitingPool;
    public static List<Session> activeSessions; // all active sessions
    public static List<ClientStatus> activeClients; // all clients status
    public static List<User> allUsers; // user list
    public static List<Session> allGamesHistory; // all games history
    private static final Logger logger = LogManager.getLogger(Main.class.getName());

    // Construction functions.
    public SocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    public SocketServer(InetSocketAddress addr) {
        super(addr);
    }

    public SocketServer(int port, Draft_6455 draft) {
        super(new InetSocketAddress(port), Collections.singletonList(draft));
    }

    /*  The server opens a new websocket connection and send server hello.
     *   Generally do nothing important, just wait for message to income.
     * */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info(String.format("Received connection from. %s", conn.getRemoteSocketAddress()));
        ClientStatus clientStatus = new ClientStatus();
        // assign new connectionId to clientStatus and append it to activeClients
        clientStatus.conn = conn;
        clientStatus.connectionId = activeClients.size();
        clientStatus.status = "online";
        addActiveClient(clientStatus);
        // send sessionId to client
        clientStatus.send();
        logger.debug(String.format("Sent connection id to client. %s", clientStatus.connectionId));
    }

    /*  Do cleaning when socket closes.
     * */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conn.close();
        logger.info(String.format("Connection closed. Reason: %s", reason));
    }

    /*  CORE METHOD
     *   Make response to client messages.
     * */
    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug(String.format("Received message from %s: %s", conn.getRemoteSocketAddress(), message));
        ClientStatus clientStatus = ClientStatus.fromJson(message);
        logger.debug(String.format("Built object %s", clientStatus));
        logger.debug(String.format("Client %d status: %s", clientStatus.connectionId, clientStatus.status));
        clientStatus.conn = conn;
        // TODO: Handle message from client.
        // case 1: client wants to register.
        if (clientStatus.status.equals("register")) {
            String userName = clientStatus.user.name;
            String userPassword = clientStatus.user.token;
            logger.debug(String.format("Client %d wants to register with name %s and password %s.", clientStatus.connectionId, userName, userPassword));
            User newUser = this.register(userName, userPassword);
            logger.debug(String.format("Registered userId %d", newUser.id));
            //update client status
            clientStatus.user = newUser;
            clientStatus.status = "registered"; // set next step
            updateClientStatus(clientStatus);
            // send user id to client
            clientStatus.send();
            logger.debug(String.format("Sent user id to client %s.", clientStatus.user.id));
            return;
        }
        // case 2:client wants to log in.
        if (clientStatus.status.equals("login")) {
            logger.debug(String.format("Client %d trying to login with name %s userId %s and password %s.", clientStatus.connectionId, clientStatus.user.name, clientStatus.user.id, clientStatus.user.token));
            // check username and password
            if (!checkUser(clientStatus.user)) {
                logger.debug(String.format("Client %d login failed.", clientStatus.connectionId));
                clientStatus.status = "loginFailed";
                updateClientStatus(clientStatus);
                clientStatus.send(); // failed, send back to client
            }//passed
            // check waiting pool
            if (!waitingCheck()) {
                // no peer found, assign user to waiting pool
                // then stop do nothing further
                addWaiting(clientStatus);
                logger.debug(String.format("Client %d is waiting for peer.", clientStatus.connectionId));
                // client wait for server to inform
                return;
            } // else found peer, assign a new session
            ClientStatus peerStatus = waitingPoll();
            clientStatus.status = "loggedIn";
            peerStatus.status = "loggedIn";
            // create new session
            Session session = assignSession(peerStatus,clientStatus);
            logger.debug(String.format("Client %d and %d are assigned to session %d with sessionToken %s.", clientStatus.connectionId, peerStatus.connectionId, session.sessionId, session.sessionToken));
            // update client status
            updateClientStatus(clientStatus);
            updateClientStatus(peerStatus);
            // update session status
            addSession(session);
            // then inform both clients
            clientStatus.send();
            logger.debug(String.format("Sent game start signal to client %s.", clientStatus.connectionId));
            peerStatus.send();
            logger.debug(String.format("Sent game start signal to client %s.", peerStatus.connectionId));
            // then clients should start game and keep alive
        }
        // case 3: client syncs with server.
        if (clientStatus.status.equals("sync")) {
            logger.debug(String.format("Client %d syncs with server.", clientStatus.connectionId));
            // check session token
            if (!checkSession(clientStatus)) {
                logger.debug(String.format("Client %d syncs failed.", clientStatus.connectionId));
                clientStatus.status = "syncFailed";
                updateClientStatus(clientStatus);
                clientStatus.send(); // failed, send back to client
            }//passed
            ClientStatus peerStatus = getPeer(clientStatus);
            peerStatus.game = clientStatus.game; // update peer game
            peerStatus.status = "sync"; // update peer status
            // update client status
            updateClientStatus(clientStatus);
            updateClientStatus(peerStatus);
            // then inform peer client
            peerStatus.send();
            logger.debug(String.format("Sent sync package to client %s.", peerStatus.connectionId));
        }
        // case 4: client keepalive. (now auto reset TTL)
        // case 5: client wants to end a game.
        if (clientStatus.status.equals("term")) {
            logger.debug(String.format("Client %d wants to end a game.", clientStatus.connectionId));
            // check session token
            if (!checkSession(clientStatus)) {
                logger.debug(String.format("Client %d terminate game failed.", clientStatus.connectionId));
                // do not reply
            }//passed
            clientStatus.status = "terminated";
            ClientStatus peerStatus = getPeer(clientStatus);
            peerStatus.status = "terminated";
            // update client status
            clientStatus.send();
            logger.debug(String.format("Sent terminate signal to client %s.", clientStatus.connectionId));
            peerStatus.send();
            logger.debug(String.format("Sent terminate signal to client %s.", peerStatus.connectionId));
            // clean up
            addGamesHistory(clientStatus.sessionId);
            removeSession(clientStatus.sessionId);
            removeClient(clientStatus);
            removeClient(peerStatus);
            logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
            peerStatus.conn.close();
            clientStatus.conn.close();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error(String.format("Socket from %s occurred an error: %s", conn.getRemoteSocketAddress(), ex.getMessage()));
    }

    @Override
    public void onStart() {
        logger.info("Server started.");
    }

    User register(String userName, String userPassword) {
        User newUser = new User();
        newUser.name = userName;
        newUser.token = userPassword;
        // assign user id
        newUser.id = allUsers.size();
        // add to users list
        allUsers.add(newUser);
        return newUser;
    }

    Session assignSession(ClientStatus client1, ClientStatus client2) {
        Session newSession = new Session();
        newSession.client1 = client1;
        newSession.client2 = client2;
        newSession.sessionId = activeSessions.size();
        newSession.sessionToken = UUID.randomUUID().toString();
        client1.peerId = client2.connectionId; // assign peer connection id (not session id or user id)
        client2.peerId = client1.connectionId;
        client1.sessionId = newSession.sessionId;
        client2.sessionId = newSession.sessionId;
        client1.sessionToken = newSession.sessionToken;
        client2.sessionToken = newSession.sessionToken;
        activeSessions.add(newSession);
        return newSession;
    }
    boolean checkUser(User user) {
        if (allUsers.size() <= user.id) {
            return false;
        }
        return allUsers.get(user.id).token.equals(user.token);
    }

    void addActiveClient(ClientStatus client) {
        activeClients.add(client);
    }
    void updateClientStatus(ClientStatus clientStatus) {
        activeClients.set(clientStatus.connectionId, clientStatus);
    }

    ClientStatus getPeer(ClientStatus clientStatus) {
        return activeClients.get(clientStatus.peerId);
    }

    boolean waitingCheck() {
        return waitingPool.size() > 0;
    }
    void addWaiting(ClientStatus clientStatus) {
        waitingPool.add(clientStatus);
    }
    ClientStatus waitingPoll() {
        return waitingPool.poll();
    }
    void addSession(Session session) {
        activeSessions.add(session);
    }
    void updateSession(Session session) {
        activeSessions.set(session.sessionId, session);
    }
    void removeSession(int sessionId) {
        activeSessions.remove(sessionId);
    }
    boolean checkSession(ClientStatus clientStatus) {
        if (activeSessions.size() <= clientStatus.sessionId) {
            return false;
        }
        return activeSessions.get(clientStatus.sessionId).sessionToken.equals(clientStatus.sessionToken);
    }
    void removeClient(ClientStatus clientStatus) {
        activeClients.remove(clientStatus.connectionId);
    }
    void addGamesHistory(int sessionId) {
        allGamesHistory.add(activeSessions.get(sessionId));
    }
}
