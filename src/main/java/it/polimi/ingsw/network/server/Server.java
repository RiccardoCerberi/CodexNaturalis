package it.polimi.ingsw.network.server;

import it.polimi.ingsw.controller.Controller;
import it.polimi.ingsw.model.card.Side;
import it.polimi.ingsw.network.ClientHandler;
import it.polimi.ingsw.network.GameActions;
import it.polimi.ingsw.network.VirtualServer;
import it.polimi.ingsw.network.heartbeat.HeartBeatHandler;
import it.polimi.ingsw.model.board.Position;
import it.polimi.ingsw.model.card.Color.PlayerColor;
import it.polimi.ingsw.model.chat.message.Message;
import it.polimi.ingsw.network.heartbeat.HeartBeatMessage;
import it.polimi.ingsw.network.server.rmi.ServerRMI;
import it.polimi.ingsw.network.server.socket.SocketHandler;

import java.util.Map;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

/**
 * The class represents the server to which clients connect.
 */
public class Server implements HeartBeatHandler, GameActions {
    private Map<String, ClientHandler> activeClients;
    private Controller controller;
    private String ip;
    private int portForSocket;
    private int portForRMI;
    private final Object lockOnConnections;

    public Server(String ip, int portForSocket, int portForRMI) {
        this.lockOnConnections = new Object();
        this.controller = new Controller();
        this.activeClients = new HashMap<>();
        this.ip = ip;
        this.portForSocket = portForSocket;
        this.portForRMI = portForRMI;
    }

    /**
     * Starts the server socket to receive tcp calls.
     */
    public void startServerSocket() {
        Socket clientSocket = null;
        ServerSocket listenSocket = null;
        try {
            listenSocket = new ServerSocket(portForSocket);
        } catch (IOException e) {
            System.err.println("failure in creating the socket : " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Server socket is ready: IP = " + ip + " port = " + portForSocket);
        try {
            while ((clientSocket = listenSocket.accept()) != null) {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                System.out.println("Received connection");
                SocketHandler handler = new SocketHandler(this, in, out, clientSocket);
                new Thread(handler::run).start();
            }
        } catch (IOException e) {
            System.err.println("Error while listening: " + e.getMessage());
        }
    }

    /**
     * Export the RMI server.
     */
    public void exportRMIServer() {
        ServerRMI myServer = new ServerRMI(this);
        VirtualServer stub = null;
        System.setProperty("java.rmi.server.hostname", ip);

        try {
            stub = (VirtualServer) UnicastRemoteObject.exportObject(myServer, portForRMI);
        } catch (RemoteException e) {
            System.err.println("Export failed: " + e.getMessage());
            System.exit(1);
        }

        Registry registry = null;
        try {
            registry = LocateRegistry.createRegistry(portForRMI);
        } catch (RemoteException e) {
            System.err.println("Registry cannot be exported");
            System.exit(1);
        }

        try {
            registry.bind(ServerRMI.getServerName(), stub);
        } catch (RemoteException | AlreadyBoundException e) {
            System.err.println("Failed to bind the server");
            System.exit(1);
        }

        System.out.println("ServerRMI is ready: IP = " + ip + " port = " + portForRMI);
    }

    /**
     * Adds the <code>clientHandler</code> to the list of clients being tracked by the server.
     * @param clientHandler the handler of the client connecting to the server.
     * @param username of the client connecting to the server.
     */
    public void connect(ClientHandler clientHandler, String username) {
        synchronized (lockOnConnections) {
            System.out.println("Received connection from " + username);
            boolean hasBeenAccepted = controller.handleConnection(username, clientHandler);
            if (hasBeenAccepted) {
                System.out.println("\thas been accepted");
                clientHandler.startHeartBeat();
                activeClients.put(username, clientHandler);
            } else {
                System.out.println("\thas not been accepted");
            }
        }
    }

    /**
     * Disconnects the client from the server and the game
     *
     * @param username of the client to disconnect.
     */
    public void disconnect(String username) {
        synchronized (lockOnConnections) {
            ClientHandler client = activeClients.get(username);
            if (client != null) {
                System.out.println("Spontaneous disconnection from " + username);
                handleDisconnection(username);
            }
        }
    }

    /**
     * Removes the client (specified by their username) to the server without removing them from the game.
     *
     * @param username of the client to disconnect.
     */
    public void remove(String username) {
        synchronized (lockOnConnections) {
            activeClients.remove(username);
        }
    }

    private void handleDisconnection(String username) {
        synchronized (lockOnConnections) {
            ClientHandler handler = activeClients.get(username);

            if (handler == null) {
                System.err.println("Request of disconnection from unknown connectionId");
                return;
            }
            System.out.println("Handle disconnection of " + username);
            controller.handleDisconnection(username);
            handler.terminate();
            activeClients.remove(username);
            System.out.println("User " + username + " left the server :(");
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void placeStarter(String username, Side side) {
        controller.placeStarter(username, side);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void chooseColor(String username, PlayerColor color) {
        controller.chooseColor(username, color);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void placeObjectiveCard(String username, int chosenObjective) {
        controller.placeObjectiveCard(username, chosenObjective);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void placeCard(String username, int frontId, int backId, Side side, Position position) {
        controller.placeCard(username, frontId, backId, side, position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(String username, int idToDraw) {
        controller.draw(username, idToDraw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(Message message) {
        controller.sendMessage(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPlayersNumber(String username, int playersNumber) {
        controller.setPlayersNumber(username, playersNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleUnresponsiveness(String inactiveUser) {
        synchronized (lockOnConnections) {
            System.out.println("Client " + inactiveUser + " is unresponsive");
            handleDisconnection(inactiveUser);
        }
    }

    /**
     * Receives ping from the client.
     * @param ping sent by the client to the server.
     */
    public void receivePing(HeartBeatMessage ping) {
        //System.out.println("Received ping from " + ping.getSender());
        synchronized (lockOnConnections) {
            ClientHandler client = activeClients.get(ping.getSender());
            if (client == null) {
                System.out.println("received ping from " + ping.getSender() + " which is unknown user: never connected or crashed");
            } else {
                client.registerPingFromClient(ping);
            }
        }
    }

    public static void main(String[] args) {
        String ip = args[0];
        int portSocket = Integer.parseInt(args[1]);
        int portRMI = Integer.parseInt(args[2]);
        Server server = new Server(ip, portSocket, portRMI);
        server.exportRMIServer();
        server.startServerSocket();
    }
}
