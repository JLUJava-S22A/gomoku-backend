

import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class SocketServer extends WebSocketServer {
    public static Queue<ClientStatus> waitingPool = new LinkedList();
    public static List<Session> activeSessions = new ArrayList();
    public static List<ClientStatus> activeClients = new ArrayList();
    public static List<User> allUsers = new ArrayList();
    public static List<Session> allGamesHistory = new ArrayList();
    private static final Logger logger = LogManager.getLogger(Main.class.getName());

    public SocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    public SocketServer(InetSocketAddress addr) {
        super(addr);
    }

    public SocketServer(int port, Draft_6455 draft) {
        super(new InetSocketAddress(port), Collections.singletonList(draft));
    }

    public String toString() {
        return (new Gson()).toJson(this);
    }

    public void saveToFile() throws IOException {
        String userConfigPath = System.getProperty("userConfig");
        String gameHistorySavePath = System.getProperty("gameHistorySave");
        String usersConfig = (new Gson()).toJson(allUsers);
        String gamesHistorySave = (new Gson()).toJson(allGamesHistory);
        logger.debug("Saving to file: " + userConfigPath);
        logger.debug("Saving to file: " + gameHistorySavePath);
        File usersConfigFile = new File(userConfigPath);
        File gameHistorySaveFile = new File(gameHistorySavePath);

        FileOutputStream s;
        try {
            s = new FileOutputStream(usersConfigFile);
        } catch (FileNotFoundException var12) {
            logger.error("File not found: " + userConfigPath);
            throw var12;
        }

        try {
            s.write(usersConfig.getBytes());
        } catch (IOException var11) {
            logger.error("IOException: " + var11.getMessage());
            throw var11;
        }

        s.close();

        try {
            s = new FileOutputStream(gameHistorySaveFile);
        } catch (FileNotFoundException var10) {
            logger.error("File not found: " + gameHistorySavePath);
            throw var10;
        }

        try {
            s.write(gamesHistorySave.getBytes());
        } catch (IOException var9) {
            logger.error("IOException: " + var9.getMessage());
            throw var9;
        }

        s.close();
    }

    public void loadFromFile() {
        String userConfigPath = System.getProperty("userConfig");
        String gameHistorySavePath = System.getProperty("gameHistorySave");
        String usersConfig = (new Gson()).toJson(allUsers);
        String gamesHistorySave = (new Gson()).toJson(allGamesHistory);
        logger.debug("Loading from file: " + userConfigPath);
        logger.debug("Loading from file: " + gameHistorySavePath);
        File usersConfigFile = new File(userConfigPath);
        File gameHistorySaveFile = new File(gameHistorySavePath);

        FileInputStream s;
        try {
            s = new FileInputStream(usersConfigFile);
        } catch (FileNotFoundException var12) {
            logger.error("File not found: " + userConfigPath);
            throw new RuntimeException(var12);
        }

        try {
            allUsers = (List)(new Gson()).fromJson(new String(s.readAllBytes()), allUsers.getClass());
        } catch (IOException var11) {
            logger.error("IOException: " + var11.getMessage());
            throw new RuntimeException(var11);
        }

        try {
            s = new FileInputStream(gameHistorySaveFile);
        } catch (FileNotFoundException var10) {
            logger.error("File not found: " + gameHistorySavePath);
            throw new RuntimeException(var10);
        }

        try {
            allGamesHistory = (List)(new Gson()).fromJson(new String(s.readAllBytes()), allGamesHistory.getClass());
        } catch (IOException var9) {
            logger.error("IOException: " + var9.getMessage());
            throw new RuntimeException(var9);
        }
    }

    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info(String.format("Received connection from. %s", conn.getRemoteSocketAddress()));
        ClientStatus clientStatus = new ClientStatus();
        clientStatus.conn = conn;
        clientStatus.connectionId = activeClients.size();
        clientStatus.status = "online";
        this.addActiveClient(clientStatus);
        clientStatus.send();
        logger.debug(String.format("Sent connection id to client. %s", clientStatus.connectionId));
    }

    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conn.close();
        logger.info(String.format("Connection closed. Reason: %s", reason));
    }

    public void onMessage(WebSocket conn, String message) {
        logger.debug(String.format("Received message from %s: %s", conn.getRemoteSocketAddress(), message));
        ClientStatus clientStatus = ClientStatus.fromJson(message);
        logger.debug(String.format("Built object %s", clientStatus));
        logger.debug(String.format("Client %d status: %s", clientStatus.connectionId, clientStatus.status));
        clientStatus.conn = conn;
        if (clientStatus.status.equals("register")) {
            this.clientRegister(clientStatus);
        }

        if (clientStatus.status.equals("login")) {
            this.clientLogin(clientStatus);
        }

        if (clientStatus.status.equals("sync")) {
            this.sync(clientStatus);
        }

        if (clientStatus.status.equals("term")) {
            this.terminateGame(clientStatus, conn);
        }

    }

    public void onError(WebSocket conn, Exception ex) {
        logger.error(String.format("Socket from %s occurred an error: %s", conn.getRemoteSocketAddress(), ex.getMessage()));
    }

    public void onStart() {
        logger.info("Server started.");
    }

    User register(String userName, String userPassword) {
        User newUser = new User();
        newUser.name = userName;
        newUser.token = userPassword;
        newUser.id = allUsers.size();
        allUsers.add(newUser);
        return newUser;
    }

    Session assignSession(ClientStatus client1, ClientStatus client2) {
        Session newSession = new Session();
        newSession.client1 = client1;
        newSession.client2 = client2;
        newSession.sessionId = activeSessions.size();
        newSession.sessionToken = UUID.randomUUID().toString();
        client1.isFirstMove = 1;
        client2.isFirstMove = 0;
        client1.peerId = client2.connectionId;
        client2.peerId = client1.connectionId;
        client1.sessionId = newSession.sessionId;
        client2.sessionId = newSession.sessionId;
        client1.sessionToken = newSession.sessionToken;
        client2.sessionToken = newSession.sessionToken;
        activeSessions.add(newSession);
        return newSession;
    }

    private boolean checkUser(User user) {
        return allUsers.size() <= user.id ? false : ((User)allUsers.get(user.id)).token.equals(user.token);
    }

    private void updateUser(User user) {
        allUsers.set(user.id, user);
    }

    private void addActiveClient(ClientStatus client) {
        activeClients.add(client);
    }

    private void updateClientStatus(ClientStatus clientStatus) {
        activeClients.set(clientStatus.connectionId, clientStatus);
    }

    private ClientStatus getPeer(ClientStatus clientStatus) {
        return (ClientStatus)activeClients.get(clientStatus.peerId);
    }

    private boolean waitingCheck() {
        return waitingPool.size() > 0;
    }

    private void addWaiting(ClientStatus clientStatus) {
        waitingPool.add(clientStatus);
    }

    private ClientStatus waitingPoll() {
        return (ClientStatus)waitingPool.poll();
    }

    private void addSession(Session session) {
        activeSessions.add(session);
    }

    private void updateSession(Session session) {
        activeSessions.set(session.sessionId, session);
    }

    private void updateSessionClient(ClientStatus clientStatus) {
        Session session = (Session)activeSessions.get(clientStatus.sessionId);
        if (clientStatus.isFirstMove == 1) {
            session.client1 = clientStatus;
        } else {
            session.client2 = clientStatus;
        }

    }

    private void removeSession(int sessionId) {
        activeSessions.remove(sessionId);
    }

    private boolean checkSession(ClientStatus clientStatus) {
        return activeSessions.size() <= clientStatus.sessionId ? false : ((Session)activeSessions.get(clientStatus.sessionId)).sessionToken.equals(clientStatus.sessionToken);
    }

    private void removeClient(ClientStatus clientStatus) {
        activeClients.remove(clientStatus.connectionId);
    }

    private void addGamesHistory(int sessionId) {
        allGamesHistory.add((Session)activeSessions.get(sessionId));
    }

    private int[] get2DCoord(int index) {
        int[] coord = new int[]{index / 15, index % 15};
        return coord;
    }

    private int get1DCoord(int dim0, int dim1) {
        int coord = dim0 * 15 + dim1;
        return coord;
    }

    int checkGameEnd(GameObject game, int chaku) {
        int[] coord = this.get2DCoord(chaku);
        int color = game.chessboard[chaku];
        int[] steps = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        int[] currCoord = new int[]{coord[0], coord[1]};

        int var10002;
        int step;
        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] + step;
            if (currCoord[1] >= 15 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[0]++;
        }

        currCoord[1] = coord[1];

        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] - step;
            if (currCoord[1] < 0 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[4]++;
        }

        currCoord[1] = coord[1];

        for(step = 0; step < 5; ++step) {
            currCoord[0] = coord[0] - step;
            if (currCoord[0] < 0 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[2]++;
        }

        currCoord[0] = coord[0];

        for(step = 0; step < 5; ++step) {
            currCoord[0] = coord[0] + step;
            if (currCoord[0] >= 15 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[6]++;
        }

        currCoord[0] = coord[0];

        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] + step;
            currCoord[0] = coord[0] - step;
            if (currCoord[0] < 0 || currCoord[1] >= 15 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[1]++;
        }

        currCoord[0] = coord[0];
        currCoord[1] = coord[1];

        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] - step;
            currCoord[0] = coord[0] + step;
            if (currCoord[0] >= 15 || currCoord[1] < 0 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[5]++;
        }

        currCoord[0] = coord[0];
        currCoord[1] = coord[1];

        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] - step;
            currCoord[0] = coord[0] - step;
            if (currCoord[0] < 0 || currCoord[1] < 0 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[3]++;
        }

        currCoord[0] = coord[0];
        currCoord[1] = coord[1];

        for(step = 0; step < 5; ++step) {
            currCoord[1] = coord[1] + step;
            currCoord[0] = coord[0] + step;
            if (currCoord[0] >= 15 || currCoord[1] >= 15 || game.chessboard[this.get1DCoord(currCoord[0], currCoord[1])] != color) {
                break;
            }

            var10002 = steps[7]++;
        }

        logger.debug("steps: " + Arrays.toString(steps));
        if (steps[0] + steps[4] >= 6) {
            return color;
        } else if (steps[2] + steps[6] >= 6) {
            return color;
        } else if (steps[1] + steps[5] >= 6) {
            return color;
        } else {
            return steps[3] + steps[7] >= 6 ? color : -1;
        }
    }

    private void sync(ClientStatus clientStatus) {
        logger.debug(String.format("Client %d syncs with server.", clientStatus.connectionId));
        if (!this.checkSession(clientStatus)) {
            logger.debug(String.format("Client %d syncs failed.", clientStatus.connectionId));
            clientStatus.status = "syncFailed";
            this.updateClientStatus(clientStatus);
            clientStatus.send();
        }

        ClientStatus peerStatus = this.getPeer(clientStatus);
        peerStatus.game = clientStatus.game;
        int win = this.checkGameEnd(clientStatus.game, clientStatus.chakuIndex);
        ClientStatus whiteStatus = clientStatus.isFirstMove == 0 ? clientStatus : peerStatus;
        ClientStatus blackStatus = peerStatus.isFirstMove == 1 ? peerStatus : clientStatus;
        if (win == 1) {
            blackStatus.status = "win";
            ++blackStatus.user.score;
            this.updateUser(blackStatus.user);
            whiteStatus.status = "lose";
            logger.info(String.format("Black client %d wins in session %s.", blackStatus.connectionId, blackStatus.sessionId));
            logger.info(String.format("User %s id %d score updated, currently %d", blackStatus.user.name, blackStatus.user.id, blackStatus.user.score));
            this.updateClientStatus(clientStatus);
            this.updateClientStatus(peerStatus);
            this.updateSessionClient(clientStatus);
            this.updateSessionClient(peerStatus);
            clientStatus.send();
            logger.debug(String.format("Sent game end signal to client %s.", clientStatus.connectionId));
            peerStatus.send();
            logger.debug(String.format("Sent game end signal to client %s.", peerStatus.connectionId));
            this.addGamesHistory(clientStatus.sessionId);
            this.removeSession(clientStatus.sessionId);
            this.removeClient(clientStatus);
            this.removeClient(peerStatus);
            logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
            peerStatus.conn.close();
            clientStatus.conn.close();
        } else if (win == 0) {
            blackStatus.status = "lose";
            whiteStatus.status = "win";
            ++whiteStatus.user.score;
            this.updateUser(whiteStatus.user);
            logger.info(String.format("White client %d wins in session %s.", whiteStatus.connectionId, whiteStatus.sessionId));
            logger.info(String.format("User %s id %d score updated, currently %d", whiteStatus.user.name, whiteStatus.user.id, whiteStatus.user.score));
            this.updateClientStatus(clientStatus);
            this.updateClientStatus(peerStatus);
            this.updateSessionClient(clientStatus);
            this.updateSessionClient(peerStatus);
            clientStatus.send();
            logger.debug(String.format("Sent game end signal to client %s.", clientStatus.connectionId));
            peerStatus.send();
            logger.debug(String.format("Sent game end signal to client %s.", peerStatus.connectionId));
            this.addGamesHistory(clientStatus.sessionId);
            this.removeSession(clientStatus.sessionId);
            this.removeClient(clientStatus);
            this.removeClient(peerStatus);
            logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
            peerStatus.conn.close();
            clientStatus.conn.close();
        } else {
            peerStatus.status = "sync";
            this.updateClientStatus(clientStatus);
            this.updateClientStatus(peerStatus);
            this.updateSessionClient(clientStatus);
            this.updateSessionClient(peerStatus);
            peerStatus.send();
            logger.debug(String.format("Sent sync package to client %s.", peerStatus.connectionId));
        }

    }

    private void terminateGame(ClientStatus clientStatus, WebSocket conn) {
        logger.debug(String.format("Client %d wants to end a game.", clientStatus.connectionId));
        if (!this.checkSession(clientStatus)) {
            logger.debug(String.format("Client %d terminate game failed.", clientStatus.connectionId));
        }

        clientStatus.status = "terminated";
        ClientStatus peerStatus = this.getPeer(clientStatus);
        peerStatus.status = "terminated";
        this.updateClientStatus(clientStatus);
        this.updateClientStatus(peerStatus);
        this.updateSessionClient(clientStatus);
        this.updateSessionClient(peerStatus);
        clientStatus.send();
        logger.debug(String.format("Sent terminate signal to client %s.", clientStatus.connectionId));
        peerStatus.send();
        logger.debug(String.format("Sent terminate signal to client %s.", peerStatus.connectionId));
        this.addGamesHistory(clientStatus.sessionId);
        this.removeSession(clientStatus.sessionId);
        this.removeClient(clientStatus);
        this.removeClient(peerStatus);
        logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
        peerStatus.conn.close();
        clientStatus.conn.close();
    }

    private void clientRegister(ClientStatus clientStatus) {
        String userName = clientStatus.user.name;
        String userPassword = clientStatus.user.token;
        logger.debug(String.format("Client %d wants to register with name %s and password %s.", clientStatus.connectionId, userName, userPassword));
        User newUser = this.register(userName, userPassword);
        logger.debug(String.format("Registered userId %d", newUser.id));
        clientStatus.user = newUser;
        clientStatus.status = "registered";
        this.updateClientStatus(clientStatus);
        clientStatus.send();
        logger.debug(String.format("Sent user id to client %s.", clientStatus.user.id));
    }

    private void clientLogin(ClientStatus clientStatus) {
        logger.debug(String.format("Client %d trying to login with name %s userId %s and password %s.", clientStatus.connectionId, clientStatus.user.name, clientStatus.user.id, clientStatus.user.token));
        if (!this.checkUser(clientStatus.user)) {
            logger.debug(String.format("Client %d login failed.", clientStatus.connectionId));
            clientStatus.status = "loginFailed";
            this.updateClientStatus(clientStatus);
            clientStatus.send();
        }

        if (!this.waitingCheck()) {
            this.addWaiting(clientStatus);
            logger.debug(String.format("Client %d is waiting for peer.", clientStatus.connectionId));
        } else {
            ClientStatus peerStatus = this.waitingPoll();
            clientStatus.status = "loggedIn";
            peerStatus.status = "loggedIn";
            Session session = this.assignSession(peerStatus, clientStatus);
            logger.debug(String.format("Client %d and %d are assigned to session %d with sessionToken %s.", clientStatus.connectionId, peerStatus.connectionId, session.sessionId, session.sessionToken));
            this.updateClientStatus(clientStatus);
            this.updateClientStatus(peerStatus);
            this.addSession(session);
            clientStatus.send();
            logger.debug(String.format("Sent game start signal to client %s.", clientStatus.connectionId));
            peerStatus.send();
            logger.debug(String.format("Sent game start signal to client %s.", peerStatus.connectionId));
        }
    }

    public void exit() throws IOException, InterruptedException {
        Iterator var1 = activeSessions.iterator();

        while(var1.hasNext()) {
            Session session = (Session)var1.next();
            this.terminateGame(session.client1, session.client1.conn);
        }

        this.saveToFile();
        this.stop();
    }
}
