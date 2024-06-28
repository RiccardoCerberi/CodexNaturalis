package it.polimi.ingsw.network.client.view.tui;

import it.polimi.ingsw.controller.InvalidIdForDrawingException;
import it.polimi.ingsw.model.InvalidGamePhaseException;
import it.polimi.ingsw.model.SuspendedGameException;
import it.polimi.ingsw.model.board.Playground;
import it.polimi.ingsw.model.board.Position;
import it.polimi.ingsw.model.card.color.InvalidColorException;
import it.polimi.ingsw.model.card.color.PlayerColor;
import it.polimi.ingsw.model.card.EmptyDeckException;
import it.polimi.ingsw.model.card.NotExistingFaceUp;
import it.polimi.ingsw.model.card.Side;
import it.polimi.ingsw.model.chat.message.InvalidMessageException;
import it.polimi.ingsw.model.chat.message.Message;
import it.polimi.ingsw.model.gamephase.GamePhase;
import it.polimi.ingsw.model.lobby.InvalidPlayersNumberException;
import it.polimi.ingsw.network.client.UnReachableServerException;
import it.polimi.ingsw.network.client.controller.ClientController;
import it.polimi.ingsw.network.client.model.board.ClientPlayground;
import it.polimi.ingsw.network.client.model.card.ClientCard;
import it.polimi.ingsw.network.client.model.player.ClientPlayer;
import it.polimi.ingsw.network.client.view.View;
import it.polimi.ingsw.network.client.view.tui.drawplayground.*;

import java.util.*;
import java.util.regex.PatternSyntaxException;

/**
 * Client TUI represents the client that uses a Text-based user interface
 */
public class ClientTUI implements View {
    private final Scanner console;
    private final Set<TUIActions> availableActions = new HashSet<>();

    private Side cardSide = Side.FRONT;

    private final ClientController controller;

    /* saves the coordinate to start print */
    private Position currOffset = new Position(0, 0);

    /* saves the current watching player, so updates are correctly applied */
    private String currentWatchingPlayer;

    /**
     * Constructs clientTUI with the <code>controller</code> provided.
     *
     * @param controller the representation of the client controller.
     */
    public ClientTUI(ClientController controller) {
        this.controller = controller;
        this.console = new Scanner(System.in);
        availableActions.add(TUIActions.HELP);
        availableActions.add(TUIActions.QUIT);
        availableActions.add(TUIActions.CONNECT);
    }

    private void setActionsForClosingTheApplication() {
        availableActions.clear();
        availableActions.add(TUIActions.QUIT);
    }

    /**
     * Parses the player's commands.
     */
    private void parseCommands() {
        while (console.hasNextLine()) {
            // split command with spaces and analyze the first word
            String[] nextCommand = console.nextLine().toLowerCase().split(" ", 4);

            synchronized (this) {
                try {
                    if (availableActions.contains(TUIActions.valueOf(nextCommand[0].toUpperCase()))) {
                        // before running command
                        ClientUtil.clearExceptionSpace();

                        switch (TUIActions.valueOf(nextCommand[0].toUpperCase())) {
                            case CONNECT -> connect(nextCommand[1], Integer.parseInt(nextCommand[2]));
                            case SELECTUSERNAME -> selectUsername(nextCommand[1]);
                            case BACK -> goBack();
                            case COLOR -> chooseColor(nextCommand[1]);
                            case FLIP -> flip();
                            case HELP -> printHelp();
                            case SPY -> spy(nextCommand[1]);
                            case M, PM -> sendMessage(nextCommand);
                            case DRAW -> draw(Integer.parseInt(nextCommand[1]));
                            case PLACE -> place(Integer.parseInt(nextCommand[1]), nextCommand[2], nextCommand[3]);
                            case QUIT -> quit();
                            case LOBBYSIZE ->
                                    setupLobbyPlayerNumber(Integer.parseInt(nextCommand.length == 2 ? nextCommand[1] : "0"));
                            case OBJECTIVE -> chooseObjective(Integer.parseInt(nextCommand[1]));
                            case STARTER -> placeStarter(nextCommand[1]);
                            case RULEBOOK -> displayRulebook(Integer.parseInt(nextCommand[1]));
                            case MVPG -> movePlayground(nextCommand[1]);
                        }
                    } else {
                        ClientUtil.printExceptions(ExceptionsTUI.INVALID_GAME_COMMAND.getMessage());
                        // print help for consented commands
                        ClientUtil.printHelpCommands(availableActions);
                    }
                } catch (UndrawablePlaygroundException |
                         InvalidPlayersNumberException |
                         InvalidMessageException | InvalidIdForDrawingException | EmptyDeckException |
                         InvalidColorException | NotExistingFaceUp | Playground.UnavailablePositionException |
                         Playground.NotEnoughResourcesException | InvalidGamePhaseException | SuspendedGameException |
                         TUIException e) {

                    ClientUtil.printExceptions(e.getMessage());
                    // print help for consented commands
                    ClientUtil.printHelpCommands(availableActions);
                } catch (IllegalArgumentException e) {
                    ClientUtil.printExceptions(ExceptionsTUI.INVALID_GAME_COMMAND.getMessage());
                    // print help for consented commands
                    ClientUtil.printHelpCommands(availableActions);
                } catch (IndexOutOfBoundsException e) {
                    ClientUtil.printExceptions("few arguments");
                    //ClientUtil.printExceptions(ExceptionsTUI.INVALID_SPY_INPUT.getMessage());
                    // print help for consented commands
                    ClientUtil.printHelpCommands(availableActions);
                } catch (UnReachableServerException e) {
                    ClientUtil.printExceptions("Server is down, please connect to another server");
                    availableActions.clear();
                    availableActions.add(TUIActions.HELP);
                    availableActions.add(TUIActions.QUIT);
                    availableActions.add(TUIActions.CONNECT);
                } finally {
                    ClientUtil.putCursorToInputArea();
                }
            }
        }
    }

    private Position stringToPos(String string) {
        String[] myPos = string.split(",", 2);
        int x = Integer.parseInt(myPos[0].trim());
        int y = Integer.parseInt(myPos[1].trim());

        return new Position(x, y);
    }

    /**
     * Method used to move the playground to a position (classic cartesian system)
     *
     * @param requestedOffsetString is the argument of the move command
     */
    private void movePlayground(String requestedOffsetString)
            throws UndrawablePlaygroundException {
        Position requestedOffset = stringToPos(requestedOffsetString);

        currOffset = ClientUtil.printPlayground(controller.getPlaygroundByUsername(currentWatchingPlayer),
                currOffset, requestedOffset);

        // clear input area
        ClientUtil.printCommandSquare();
    }

    /**
     * Restores the current playing area of the player.
     */
    private void goBack() throws UnInitializedPlaygroundException, FittablePlaygroundException,
                                              InvalidCardRepresentationException, InvalidCardDimensionException {
        synchronized (controller) {
            // so updates arrive correctly to main player
            currentWatchingPlayer = controller.getMainPlayerUsername();
            // add back commands
            setAvailableActions();
            // print main player stuff again
            showUpdateAfterConnection();
            // clear input area
            ClientUtil.printCommandSquare();
        }
    }

    /**
     * Flips the cards over.
     */
    private void flip() {
        synchronized (controller) {
            // invert side
            cardSide = cardSide.equals(Side.FRONT) ? Side.BACK : Side.FRONT;

            List<ClientCard> toPrint = (controller.isMainPlaygroundEmpty()) ?
                    Collections.singletonList(controller.getMainPlayerStarter()) : controller.getMainPlayerCards();

            ClientUtil.printPlayerHand(toPrint, cardSide);

            // clear input area
            ClientUtil.printCommandSquare();
        }
    }

    private void printHelp() {
        ClientUtil.printHelpCommands(availableActions);
    }

    /**
     * Quits the game.
     */
    private void quit() {
        controller.disconnect(controller.getMainPlayerUsername());

        ClientUtil.clearScreen();
        System.exit(0);
    }

    /**
     * Receives the index of the secret objective card chosen by the player.
     *
     * @throws InvalidGamePhaseException if the game phase doesn't allow choosing objective cards.
     * @throws SuspendedGameException    if the game is suspended.
     * @throws TUIException              if the player enters an invalid objective index.
     */
    private void chooseObjective(int objectiveIdx) throws InvalidGamePhaseException, SuspendedGameException, TUIException {
        if (objectiveIdx != 1 && objectiveIdx != 2) {
            throw new TUIException(ExceptionsTUI.INVALID_IDX);
        }

        controller.placeObjectiveCard(objectiveIdx - 1);
        ClientUtil.printCommandSquare();
    }

    /**
     * Sets the number of players allowed in the lobby.
     *
     * @param size of the lobby.
     * @throws InvalidPlayersNumberException if the <code>size</code> is greater than 4 or less than 2
     * @throws NumberFormatException         if the player digits an entry that isn't a number.
     */
    private void setupLobbyPlayerNumber(int size) throws InvalidPlayersNumberException, NumberFormatException {
        controller.setPlayersNumber(size);
        // remove manually: only creator has this command
        availableActions.remove(TUIActions.LOBBYSIZE);

        ClientUtil.printCommandSquare();
        //ClientUtil.putCursorToInputArea();
    }

    private void printAvailableColorList() {
        String availableColors = String.join(",", controller.getAvailableColors()
                .stream().map(PlayerColor::toString)
                .toList());

        // could have been any area
        ClientUtil.printExceptions("Available colors: " + availableColors);
    }

    /**
     * Receives the color chosen by the player.
     *
     * @throws InvalidColorException     if the color has already been selected by others.
     * @throws InvalidGamePhaseException if the game phase doesn't allow choosing colors.
     * @throws SuspendedGameException    if the game is suspended.
     * @throws TUIException              if the player enters an invalid color.
     */
    private void chooseColor(String colorName) throws InvalidColorException, InvalidGamePhaseException, SuspendedGameException, IllegalArgumentException, TUIException {
        try {
            PlayerColor color = PlayerColor.valueOf(colorName.toUpperCase());
            controller.chooseColor(color);
        } catch (IllegalArgumentException e) {//catch (IllegalArgumentException |InvalidColorException e){
            throw new TUIException(ExceptionsTUI.INVALID_COLOR);
        }

        // clear input area
        ClientUtil.printCommandSquare();
        //ClientUtil.putCursorToInputArea();
    }

    /**
     * Sends a message.
     *
     * @param command an array of strings containing the message and the form in which the message is to be transmitted.
     * @throws InvalidMessageException if the author doesn't match the author or the recipient doesn't exist.
     * @throws TUIException            if the player attempts to send a message incorrectly, that means, not following
     *                                 the sending message structure.
     */
    private void sendMessage(String[] command) throws InvalidMessageException, TUIException {
        try {
            String commandContent = command[0].equals("pm") ? command[1].concat(" " + command[2]) : command[1];
            Message myMessage = command[0].equals("pm") ? createPrivateMessage(commandContent) : createBroadcastMessage(commandContent);
            controller.sendMessage(myMessage);

            // clear input area
            ClientUtil.printCommandSquare();
            //ClientUtil.putCursorToInputArea();
        } catch (IndexOutOfBoundsException e) {
            throw new TUIException(ExceptionsTUI.INVALID_MESSAGE_INPUT);
        }
    }

    /**
     * Creates a private message
     *
     * @param messageDetails the recipient and the content of the message.
     * @return a message with an author, a recipient and the content of the message.
     */
    private Message createPrivateMessage(String messageDetails) throws InvalidMessageException {
        // messageDetails contains recipient too
        String[] messageSplit = messageDetails.split(" ", 2);
        String recipient = messageSplit[0];
        String messageContent = messageSplit[1];
        return new Message(controller.getMainPlayerUsername(), recipient, messageContent);
    }

    /**
     * Creates a message in broadcast.
     *
     * @param messageContent the content of the message.
     * @return a message with the author and the content of the message.
     */
    private Message createBroadcastMessage(String messageContent) throws InvalidMessageException {
        return new Message(controller.getMainPlayerUsername(), messageContent);
    }

    /**
     * Receives the position of the card to draw.
     *
     * @throws InvalidIdForDrawingException if the id isn't valid for drawing.
     * @throws EmptyDeckException           in the event that the deck is empty.
     * @throws NotExistingFaceUp            if the face up slot is empty.
     * @throws InvalidGamePhaseException    if the game doesn't allow to draw cards.
     * @throws SuspendedGameException       if the game is suspended.
     * @throws TUIException                 if the player inserts an inappropriate argument.
     */
    private void draw(int drawFromId) throws InvalidIdForDrawingException, EmptyDeckException, NotExistingFaceUp, InvalidGamePhaseException, SuspendedGameException, TUIException {
        try {
            controller.draw(drawFromId - 1);

            // clear input area
            ClientUtil.printCommandSquare();
            //ClientUtil.putCursorToInputArea();
        } catch (IllegalArgumentException e) {
            throw new TUIException(ExceptionsTUI.INVALID_CARD_POSITION);
        }
    }

    /**
     * Places a specific card on a specific side at a specific position of the playground.
     *
     * @throws Playground.UnavailablePositionException if the position isn't available or if it is already occupied.
     * @throws Playground.NotEnoughResourcesException  if the needed resources to place the card aren't enough.
     * @throws InvalidGamePhaseException               if the game phase doesn't allow placing cards.
     * @throws SuspendedGameException                  if the game is suspended.
     * @throws TUIException                            if the player enters an invalid side or position to place in.
     */
    private void place(int handPos, String sideName, String pos) throws Playground.UnavailablePositionException, Playground.NotEnoughResourcesException, InvalidGamePhaseException, SuspendedGameException, TUIException {
        if (handPos < 1 || handPos > 3) {
            throw new TUIException(ExceptionsTUI.INVALID_CARD_POSITION);
        }

        Side cardSide;
        try {
            cardSide = Side.valueOf(sideName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TUIException(ExceptionsTUI.INVALID_SIDE);
        }

        Position newCardPos;
        try {
            newCardPos = stringToPos(pos);
        } catch (PatternSyntaxException e) {
            throw new TUIException(ExceptionsTUI.INVALID_CARD_POSITION);
        }

        controller.placeCard(handPos - 1, cardSide, newCardPos);

        ClientUtil.printCommandSquare();
    }

    /**
     * Places the starter card on the given side.
     *
     * @throws InvalidGamePhaseException if the game doesn't allow placing starter cards.
     * @throws SuspendedGameException    if the game is suspended.
     * @throws TUIException              if the player enters an invalid card side.
     */
    private void placeStarter(String sideName) throws InvalidGamePhaseException, SuspendedGameException, TUIException {
        try {
            Side starterSide = Side.valueOf(sideName.toUpperCase());
            controller.placeStarter(starterSide);
        } catch (IllegalArgumentException e) {
            throw new TUIException(ExceptionsTUI.INVALID_SIDE);
        }
        // clear input area
        ClientUtil.printCommandSquare();
        //ClientUtil.putCursorToInputArea();
    }

    /**
     * Displays the rulebook on the screen.
     *
     * @param numberOfPage the page number of the manual to be printed on the screen.
     * @throws TUIException if the player enters an invalid page number.
     */
    private void displayRulebook(int numberOfPage) throws TUIException {
        if (numberOfPage != 1 && numberOfPage != 2) {
            throw new TUIException(ExceptionsTUI.INVALID_PAGE);
        }
        ClientUtil.printRulebook(numberOfPage);
        // remove game actions while reading manual
        availableActions.clear();
        availableActions.add(TUIActions.M);
        availableActions.add(TUIActions.PM);
        availableActions.add(TUIActions.HELP);
        availableActions.add(TUIActions.QUIT);
        availableActions.add(TUIActions.BACK);
        availableActions.add(TUIActions.RULEBOOK);
        //ClientUtil.putCursorToInputArea();
    }

    private void connect(String ip, Integer port) throws UnReachableServerException {
        controller.configureClient(this, ip, port);
        availableActions.remove(TUIActions.CONNECT);
        availableActions.add(TUIActions.SELECTUSERNAME);
        ClientUtil.printCommand("Insert your username: type `selectusername <your username>` (max 12 characters):");
    }

    private void selectUsername(String username) throws TUIException {
        if (username.length() > 12) { // max username length
            throw new TUIException(ExceptionsTUI.INVALID_USERNAME);
        }
        this.currentWatchingPlayer = username;
        availableActions.remove(TUIActions.SELECTUSERNAME);
        controller.connect(username);
        ClientUtil.printCommandSquare();
    }

    /**
     * This method is invoked in a new thread at the beginning of a game
     * Commands can't be interrupted
     */
    @Override
    public void runView() {
        ClientUtil.printFirstScreen();
        ClientUtil.printCommand("Welcome.\nTo play connect to the server: type connect <ip> <port>");
        new Thread(this::parseCommands).start();
    }

    /**
     * Displays the playground of the <code>playerIdx</code> player.
     *
     * @throws TUIException if the player attempts to spy himself.
     */
    private void spy(String username) throws TUIException, UndrawablePlaygroundException {
        synchronized (controller) {
            ClientPlayer player = this.controller.getPlayer(username);
            if (player == null)
                throw new TUIException(ExceptionsTUI.INVALID_SPY_INPUT);

            ClientPlayground playground = player.getPlayground();

            if (this.controller.getMainPlayer().equals(player)) {
                throw new TUIException(ExceptionsTUI.INVALID_SPY_COMMAND);
            } else {
                // make showUpdate work on this player
                this.currentWatchingPlayer = username;
                // update commands when you are looking at other players
                availableActions.remove(TUIActions.DRAW);
                availableActions.remove(TUIActions.PLACE);
                availableActions.remove(TUIActions.FLIP);
                availableActions.add(TUIActions.BACK);

                // update only playground, hand and resources
                ClientUtil.printResourcesArea(playground.getResources());
                currOffset = ClientUtil.printPlayground(playground, currOffset);
                ClientUtil.printPlayerHand(controller.getPlayer(username).getPlayerCards(), cardSide);

                //clear input area
                ClientUtil.printCommandSquare();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showServerCrash() {
        ClientUtil.printCommand("Server is crashed");
        availableActions.clear();
        setActionsForClosingTheApplication();
    }

    /**
     * Sets available actions in accordance to the current phase of the game.
     */
    private void setAvailableActions() {
        synchronized (controller) {
            GamePhase currentPhase = controller.getGamePhase();

            this.availableActions.clear();
            availableActions.add(TUIActions.HELP);
            availableActions.add(TUIActions.QUIT);

            // add actions only if game is active
            if (controller.isGameActive()) {
                if (currentPhase != null) { // if not in lobby
                    availableActions.add(TUIActions.M);
                    availableActions.add(TUIActions.PM);
                    availableActions.add(TUIActions.FLIP);
                    availableActions.add(TUIActions.RULEBOOK);
                    availableActions.add(TUIActions.MVPG);
                }

                switch (currentPhase) {
                    case Setup -> {
                        // starter command available only if user have to do starter stuff
                        if (controller.isMainPlaygroundEmpty()) {
                            availableActions.add(TUIActions.STARTER);
                        } else if (controller.getMainColor() == null) {
                            availableActions.add(TUIActions.COLOR);
                        } else if (controller.getMainPlayer().getObjectiveCards().size() != 1) {
                            availableActions.add(TUIActions.OBJECTIVE);
                        }
                    }
                    // don't let user use unneeded commands when it's not their turn
                    case DrawNormal -> {
                        if (this.controller.getCurrentPlayerUsername().equals(this.controller.getMainPlayerUsername()))
                            availableActions.add(TUIActions.DRAW);

                        availableActions.add(TUIActions.SPY);
                    }
                    case PlaceNormal, PlaceAdditional -> {
                        if (this.controller.getCurrentPlayerUsername().equals(this.controller.getMainPlayerUsername()))
                            availableActions.add(TUIActions.PLACE);

                        availableActions.add(TUIActions.SPY);
                    }
                    case null -> {}
                    case End -> {}
                }
            }
        }
    }

    // SHOW UPDATE

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdatePlayersInLobby() {
        ClientUtil.printWaitingList(controller.getUsernames());

        ClientUtil.putCursorToInputArea();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateCreator() {
        ClientUtil.printCommand("""
                Welcome to the new lobby!
                Please set the lobby size (2 to 4 players allowed)
                Type 'lobbysize <number>' to set the lobby size""");

        // add manually: only creator has this command
        availableActions.add(TUIActions.LOBBYSIZE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateAfterLobbyCrash() {
        ClientUtil.printCommand("Lobby crashed! You will be disconnected. Please connect again...");
        setActionsForClosingTheApplication();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateExceedingPlayer() {
        ClientUtil.printCommand("You're an exceeding player!");
        setActionsForClosingTheApplication();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showInvalidLogin(String details) {
        /*
        Synchronization not needed here because the thread from the server is blocked until this operation finishes and the heartbeat thread
        is never activated in case of login failure.
         */
        ClientUtil.printExceptions("Invalid username! Reason: " + details);
        ClientUtil.printCommand("Please insert a new username with the command <selectusername> <your name>");
        availableActions.add(TUIActions.SELECTUSERNAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateAfterConnection() {
        ClientUtil.clearScreen();

        synchronized (controller) {
            // set currentWatchingUsername for the first time
            currentWatchingPlayer = controller.getCurrentPlayerUsername();

            ClientPlayground playerPlayground = controller.getMainPlayerPlayground();

            ClientUtil.printScoreboard(this.controller.getPlayers());
            ClientUtil.printResourcesArea(playerPlayground.getResources());
            ClientUtil.printFaceUpCards(this.controller.getFaceUpCards());
            ClientUtil.printCommandSquare();
            ClientUtil.printChatSquare();
            // print decks
            ClientUtil.printDecks(controller.getGoldenDeckTopBack(), controller.getResourceDeckTopBack());

            // print objective(s)
            showUpdateObjectiveCard();

            // when printing for first time,

            try {
                currOffset = ClientUtil.printPlayground(playerPlayground, currOffset);
            } catch (UndrawablePlaygroundException e) {
                ClientUtil.writeLine(GameScreenArea.INPUT_AREA.getScreenPosition().getX() + 11,
                        GameScreenArea.INPUT_AREA.getScreenPosition().getY() + 1,
                        GameScreenArea.INPUT_AREA.getWidth() - 2,
                        e.getMessage());
            }
            // check if there is any occupied tile: it means starter has been placed
            ClientUtil.printPlayerHand(controller.isMainPlaygroundEmpty() ?
                            Collections.singletonList(this.controller.getMainPlayerStarter()) :
                            controller.getMainPlayerCards(),
                    cardSide);
            ClientUtil.putCursorToInputArea();
            setAvailableActions();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdatePlayerStatus() {
        synchronized (controller) {
            ClientUtil.printScoreboard(this.controller.getPlayers());
            ClientUtil.putCursorToInputArea();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showStarterPlacement(String username) {
        synchronized (controller) {
            // update only user that placed the card
            if (controller.getMainPlayerUsername().equals(username)) {
                availableActions.add(TUIActions.COLOR);
                availableActions.remove(TUIActions.STARTER);

                // resources may have changed
                ClientUtil.printResourcesArea(this.controller.getMainPlayer().getPlayground().getResources());

                try {
                    currOffset = ClientUtil.printPlayground(this.controller.getMainPlayerPlayground(), currOffset);
                } catch (UndrawablePlaygroundException e) {
                    ClientUtil.writeLine(GameScreenArea.INPUT_AREA.getScreenPosition().getX() + 11,
                            GameScreenArea.INPUT_AREA.getScreenPosition().getY() + 1,
                            GameScreenArea.INPUT_AREA.getWidth() - 2,
                            e.getMessage());
                }
                ClientUtil.printPlayerHand(controller.getMainPlayerCards(), cardSide);

                ClientUtil.putCursorToInputArea();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateColor(String username) { //showUpdateColor shows the new scoreBoard with the updated colors
        synchronized (controller) {
            ClientUtil.printScoreboard(this.controller.getPlayers());

            // don't print if main player has already chosen the color
            if (controller.getMainColor() == null)
                printAvailableColorList();

            if (controller.getMainPlayerUsername().equals(username)){
                setAvailableActions();
                // remove color list if present
                ClientUtil.clearExceptionSpace();
            }

            ClientUtil.putCursorToInputArea();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateObjectiveCard() {
        synchronized (controller) {
            // print private objective card
            ClientUtil.printObjectiveCards(controller.getPlayerObjectives(), GameScreenArea.PRIVATE_OBJECTIVE);
            //print common objective cards
            ClientUtil.printObjectiveCards(controller.getObjectiveCards(), GameScreenArea.COMMON_OBJECTIVE);

            setAvailableActions();
        }

        ClientUtil.putCursorToInputArea();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateAfterPlace(String username) {
        synchronized (controller) {
            // points may have changed: show to everyone
            ClientUtil.printScoreboard(this.controller.getPlayers());

            if (this.currentWatchingPlayer.equals(username)) {
                ClientUtil.printPlayerHand(controller.getPlayer(username).getPlayerCards(), cardSide);
                // print playground
                try {
                    currOffset = ClientUtil.printPlayground(controller.getPlaygroundByUsername(username), currOffset);
                } catch (UndrawablePlaygroundException e) {
                    ClientUtil.writeLine(GameScreenArea.INPUT_AREA.getScreenPosition().getX() + 11,
                            GameScreenArea.INPUT_AREA.getScreenPosition().getY() + 1,
                            GameScreenArea.INPUT_AREA.getWidth() - 2,
                            e.getMessage());
                }
                // resources may have changed
                ClientUtil.printResourcesArea(controller.getPlaygroundByUsername(username).getResources());
                ClientUtil.putCursorToInputArea();

                setAvailableActions();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateAfterDraw(String username) {
        synchronized (controller) {
            // faceUpCards
            ClientUtil.printFaceUpCards(controller.getFaceUpCards());
            // print decks
            ClientUtil.printDecks(controller.getGoldenDeckTopBack(), controller.getResourceDeckTopBack());
            // print hand
            ClientUtil.printPlayerHand(controller.getPlayer(currentWatchingPlayer).getPlayerCards(), cardSide);

            setAvailableActions();

            ClientUtil.putCursorToInputArea();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateChat() {
        List<Message> messages;
        synchronized (controller) {
            messages = new ArrayList<>(controller.getMessages());
        }
        ClientUtil.printChat(messages, controller.getMainPlayer());

        ClientUtil.putCursorToInputArea();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showUpdateCurrentPlayer() {
        String currentUsername = controller.getCurrentPlayerUsername();
        String myUsername = controller.getMainPlayerUsername();
        // return to my playground if it's my turn, and I'm spying someone
        if (myUsername.equals(currentUsername) && !myUsername.equals(currentWatchingPlayer)) {
            currentWatchingPlayer = myUsername;

            showUpdateAfterPlace(currentUsername);
        }

        String currentPlayerPrint = myUsername.equals(currentUsername) ?
                "your" : currentUsername + "'s";
        ClientUtil.printCommand("It's " + currentPlayerPrint + " turn");
        setAvailableActions();
        ClientUtil.putCursorToInputArea();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showUpdateSuspendedGame() {
        synchronized (controller) {
            ClientUtil.printScoreboard(this.controller.getPlayers());
            boolean isActive = controller.isGameActive();
            if (isActive) {
                ClientUtil.printCommand(" GAME IS NOW ACTIVE \n");
            } else {
                ClientUtil.printCommand(" SUSPENDED GAME \n");
            }
        }
        ClientUtil.putCursorToInputArea();

        setAvailableActions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void showWinners(List<String> winners) {
        ClientUtil.printCommand("Winners:\n" + String.join("\n", winners) + "\nQuit to play again");
        availableActions.clear();
        availableActions.add(TUIActions.HELP);
        availableActions.add(TUIActions.QUIT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void reportError(String details) {
        ClientUtil.printCommand("Error: " + details);
    }

    @Override
    public synchronized void showConnectionLost() {
        ClientUtil.printCommand("Connection lost");
        setActionsForClosingTheApplication();
    }
}