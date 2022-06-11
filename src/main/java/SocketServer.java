import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.util.*;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class SocketServer extends WebSocketServer {

    public static Queue<ClientStatus> waitingPool = new LinkedList<>();
    public static List<Session> activeSessions = new ArrayList<>(); // all active sessions
    public static List<ClientStatus> activeClients = new ArrayList<>(); // all clients status
    public static List<User> allUsers = new ArrayList<>(); // user list
    public static List<Session> allGamesHistory = new ArrayList<>(); // all games history
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
            Session session = assignSession(peerStatus, clientStatus);
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
            // check game end
            int win = checkGameEnd(clientStatus.game, clientStatus.chakuIndex);
            ClientStatus whiteStatus = clientStatus.isFirstMove == 0 ? clientStatus : peerStatus;
            ClientStatus blackStatus = peerStatus.isFirstMove == 1 ? peerStatus : clientStatus;

            if (win == 1) { // black win
                blackStatus.status = "win";
                whiteStatus.status = "lose";
                logger.info(String.format("Black client %d wins in session %s.", blackStatus.connectionId, blackStatus.sessionId));
                // end game
                updateClientStatus(clientStatus);
                updateClientStatus(peerStatus);
                // update session status
                updateSessionClient(clientStatus);
                updateSessionClient(peerStatus);
                clientStatus.send();
                logger.debug(String.format("Sent game end signal to client %s.", clientStatus.connectionId));
                peerStatus.send();
                logger.debug(String.format("Sent game end signal to client %s.", peerStatus.connectionId));
                // clean up
                addGamesHistory(clientStatus.sessionId);
                removeSession(clientStatus.sessionId);
                removeClient(clientStatus);
                removeClient(peerStatus);
                logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
                peerStatus.conn.close();
                clientStatus.conn.close();
                return;
            } else if (win == 0) { // white win
                blackStatus.status = "lose";
                whiteStatus.status = "win";
                logger.info(String.format("White client %d wins in session %s.", whiteStatus.connectionId, whiteStatus.sessionId));
                // end game
                updateClientStatus(clientStatus);
                updateClientStatus(peerStatus);
                // update session status
                updateSessionClient(clientStatus);
                updateSessionClient(peerStatus);
                clientStatus.send();
                logger.debug(String.format("Sent game end signal to client %s.", clientStatus.connectionId));
                peerStatus.send();
                logger.debug(String.format("Sent game end signal to client %s.", peerStatus.connectionId));
                // clean up
                addGamesHistory(clientStatus.sessionId);
                removeSession(clientStatus.sessionId);
                removeClient(clientStatus);
                removeClient(peerStatus);
                logger.debug(String.format("Removed session %d and clients %d and %d.", clientStatus.sessionId, clientStatus.connectionId, peerStatus.connectionId));
                peerStatus.conn.close();
                clientStatus.conn.close();
                return;
            } else { // continue
                peerStatus.status = "sync"; // update peer status
                // update client status
                updateClientStatus(clientStatus);
                updateClientStatus(peerStatus);
                // update session status
                updateSessionClient(clientStatus);
                updateSessionClient(peerStatus);
                // then inform peer client
                peerStatus.send();
                logger.debug(String.format("Sent sync package to client %s.", peerStatus.connectionId));
            }
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
            updateClientStatus(clientStatus);
            updateClientStatus(peerStatus);
            updateSessionClient(clientStatus);
            updateSessionClient(peerStatus);
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

        client1.isFirstMove = 1;// client1 is default first move
        client2.isFirstMove = 0;
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

    void updateSessionClient(ClientStatus clientStatus) {
        Session session = activeSessions.get(clientStatus.sessionId);
        if (clientStatus.isFirstMove == 1) {
            session.client1 = clientStatus;
        } else {
            session.client2 = clientStatus;
        }
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

    private int[] get2DCoord(int index) {
        int[] coord = new int[2];
        coord[0] = index / 15; // dim0
        coord[1] = index % 15; // dim1
        return coord;
    }

    private int get1DCoord(int dim0, int dim1) {
        int coord;
        coord = dim0 * 15 + dim1;
        return coord;
    }


    int checkGameEnd(GameObject game, int chaku) {
        // check if game is ended
        int[] coord = get2DCoord(chaku);
        int color = game.chessboard[chaku];
//        int[] steps = {0, 0, 0, 0, 0, 0, 0, 0};
        int[] steps = {0, 0};
        int[] currCoord = {coord[0], coord[1]};
        Consumer<int[]> stepNext = (int[] d) -> {
            currCoord[0] = coord[0] + d[0];
            currCoord[1] = coord[1] + d[1];
        };
        Runnable reset = () -> {
            currCoord[0] = coord[0];
            currCoord[1] = coord[1];
        };
        Function<int[], Boolean> checkOutOfRange = (int[] c) -> (c[1] >= 15 || c[1] < 0 || c[0] >= 15 || c[0] < 0);
        Function<int[], Boolean> checkWinAndHighlighten = (int[] s) -> {
            logger.debug(String.format("Direction (%d,%d) steps (%d,%d).", s[0], s[1], steps[0], steps[1]));
            if (steps[0] + steps[1] >= 6) {
                // highlight them
                currCoord[0] = currCoord[0] + s[0] * (steps[0] - 1); // -1 because steps is counting the center one
                currCoord[1] = currCoord[1] + s[1] * (steps[1] - 1);
                int[] retro = {-s[0], -s[1]};
                for (int i = 1; i < steps[0] + steps[1]; ++i) {
                    game.chessboard[get1DCoord(currCoord[0], currCoord[1])] = 2; // mark as highlight
                    stepNext.accept(retro);
                }
                return true;
            } else return false;
        };
        Consumer<int[]> walkThrough = (int[] s) -> {
            steps[0] = 0;
            for (int step = 1; step <= 5; step++) {
                if (checkOutOfRange.apply(currCoord) || (game.chessboard[get1DCoord(currCoord[0], currCoord[1])] != color)) {
                    System.out.println(checkOutOfRange.apply(currCoord));
                    System.out.println(game.chessboard[get1DCoord(currCoord[0], currCoord[1])] != color);
                    break;
                }
                steps[0] ++;
                stepNext.accept(s); // make a move
            }
            // retro walk through
            int[] retro = {-s[0], -s[1]};
            steps[1] = 0;
            reset.run(); // reset to center
            for (int step = 1; step <= 5; step++) {
                if (checkOutOfRange.apply(currCoord) || (game.chessboard[get1DCoord(currCoord[0], currCoord[1])] != color)) {
                    System.out.println(checkOutOfRange.apply(currCoord));
                    System.out.println(game.chessboard[get1DCoord(currCoord[0], currCoord[1])] != color);
                    break;
                }
                steps[1] ++;
                stepNext.accept(retro); // make a retro move
            }
        };
        // horizontal check
        walkThrough.accept(new int[]{0, 1});
        if (checkWinAndHighlighten.apply(new int[]{0, 1})) {
            return color;
        }
        // vertical walk through
        walkThrough.accept(new int[]{-1, 0});
        if (checkWinAndHighlighten.apply(new int[]{-1, 0})) {
            return color;
        }
        // anti-diagonal walk through
        walkThrough.accept(new int[]{-1, 1});
        if (checkWinAndHighlighten.apply(new int[]{-1, 1})) {
            return color;
        }
        // diagonal walk through
        walkThrough.accept(new int[]{-1, -1});
        if (checkWinAndHighlighten.apply(new int[]{-1, -1})) {
            return color;
        }
        return -1; // by default, continue
    }
}
