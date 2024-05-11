package it.polimi.ingsw.model;

import com.google.gson.reflect.TypeToken;
import it.polimi.ingsw.jsondeserializer.DeserializationHandler;
import it.polimi.ingsw.model.Deck.Deck;
import it.polimi.ingsw.model.Deck.DeckType;
import it.polimi.ingsw.model.board.*;
import it.polimi.ingsw.model.card.*;
import it.polimi.ingsw.model.card.Color.InvalidColorException;
import it.polimi.ingsw.model.card.Color.PlayerColor;
import it.polimi.ingsw.model.chat.ChatDatabase;
import it.polimi.ingsw.model.gamePhase.GamePhase;
import it.polimi.ingsw.model.listenerhandler.ListenerHandler;
import it.polimi.ingsw.model.lobby.InvalidUsernameException;
import it.polimi.ingsw.model.notifier.Notifier;
import it.polimi.ingsw.model.player.InvalidPlayerActionException;
import it.polimi.ingsw.model.player.Player;
import it.polimi.ingsw.model.gamePhase.PhaseHandler;
import it.polimi.ingsw.model.chat.message.Message;
import it.polimi.ingsw.model.chat.message.InvalidMessageException;
import it.polimi.ingsw.network.VirtualView;
import it.polimi.ingsw.network.client.model.ClientGame;
import it.polimi.ingsw.network.client.model.player.ClientPlayer;
import it.polimi.ingsw.network.client.model.card.ClientCard;

import java.io.FileNotFoundException;
import java.rmi.RemoteException;
import java.util.*;

import static java.lang.Math.min;

/**
 * Representation of the game.
 * Each player is recognised through the system by their username.
 */
public class Game {
    private Deck<Card> resourceCards;
    private Deck<Card> goldenCards;
    private Deck<Card> starterCards;
    private Deck<ObjectiveCard> objectiveCards;

    private final List<String> validUsernames;

    Set<PlayerColor> availableColors;

    private List<Card> faceUpCards;

    private List<ObjectiveCard> commonObjects;

    private static final int MAX_POINT = 20;

    private int currentPlayerIdx; // index in the current player list.

    private final boolean isFinished;

    private boolean isActive;

    private List<Player> players;

    private GamePhase phase;

    private PhaseHandler phaseHandler;

    public static final int MAX_DELAY_FOR_SUSPENDED_GAME = 1000000;

    private ListenerHandler<VirtualView> listenerHandler;

    // synchronization
    //private Object actionLock = new Object();

    // Advanced Features

    // chat database containing the history of all sent messages
    private ChatDatabase chatDatabase;

    private List<Card> createCardList(List<Front> fronts, List<Back> backs) {
        assert fronts.size() == backs.size();

        List<Card> cards = new ArrayList<>();

        for (int i = 0; i < fronts.size(); ++i) {
            cards.add(new Card(fronts.get(i), backs.get(i)));
        }

        return cards;
    }

    private List<ObjectiveCard> createObjectiveCardList(List<ObjectivePositionCard> objectivePositions, List<ObjectiveResourceCard> objectiveResources) {
        List<ObjectiveCard> objectiveCards = new ArrayList<>();

        objectiveCards.addAll(objectivePositions);

        objectiveCards.addAll(objectiveResources);

        return objectiveCards;
    }

    private List<Front> frontFromGoldenList(String goldenFrontCardsPath) throws FileNotFoundException {
        List<GoldenFront> gFronts =
                new DeserializationHandler<GoldenFront>().jsonToList(goldenFrontCardsPath, new TypeToken<>() {
                });
        return new ArrayList<>(gFronts);
    }

    private void loadAvailableColors() {
        this.availableColors = new HashSet<>(
                Arrays.asList(
                        PlayerColor.RED,
                        PlayerColor.RED,
                        PlayerColor.BLUE,
                        PlayerColor.BLUE,
                        PlayerColor.GREEEN,
                        PlayerColor.GREEEN,
                        PlayerColor.YELLOW,
                        PlayerColor.YELLOW
                )
        );
    }

    private void loadCards() {
        String backCardsPath = "src/main/resources/cards/backCards.json";
        String goldenFrontCardsPath = "src/main/resources/cards/goldenFrontCards.json";
        String resourceFrontCardsPath = "src/main/resources/cards/resourceFrontCards.json";
        String startingFrontCardsPath = "src/main/resources/cards/startingFrontCards.json";
        String startingBackCardsPath = "src/main/resources/cards/startingBackCards.json";
        String objectivePositionFrontCardsPath = "src/main/resources/cards/objectivePositionFrontCards.json";
        String objectiveResourceCardsPath = "src/main/resources/cards/objectiveResourceFrontCards.json";

        try {
            this.resourceCards = new Deck<>(createCardList(
                    new DeserializationHandler<Front>().jsonToList(resourceFrontCardsPath, new TypeToken<>() {
                    }),
                    new DeserializationHandler<Back>().jsonToList(backCardsPath, new TypeToken<>() {
                    })
            ));

            this.goldenCards = new Deck<>(
                    createCardList(
                            frontFromGoldenList(goldenFrontCardsPath),
                            new DeserializationHandler<Back>().jsonToList(backCardsPath, new TypeToken<>() {
                            })
                    )
            );

            this.starterCards = new Deck<>(createCardList(
                    new DeserializationHandler<Front>().jsonToList(startingFrontCardsPath, new TypeToken<>() {
                    }),
                    new DeserializationHandler<Back>().jsonToList(startingBackCardsPath, new TypeToken<>() {
                    })
            ));

            this.objectiveCards = new Deck<>(
                    createObjectiveCardList(
                            new DeserializationHandler<ObjectivePositionCard>().jsonToList(objectivePositionFrontCardsPath, new TypeToken<>() {
                            }),
                            new DeserializationHandler<ObjectiveResourceCard>().jsonToList(objectiveResourceCardsPath, new TypeToken<>() {
                            })
                    )
            );

        } catch (FileNotFoundException e) {
            System.err.println("File not found");
            e.printStackTrace();
        }
    }

    // todo. change notify create player with notification of the current game's state
    private Player createPlayer(String username) throws EmptyDeckException {
        Card startingCard = starterCards.draw();

        List<Card> userHand = new ArrayList<>();
        userHand.add(resourceCards.draw());
        userHand.add(resourceCards.draw());
        userHand.add(goldenCards.draw());

        List<ObjectiveCard> userObjectives = new ArrayList<>();
        userObjectives.add(objectiveCards.draw());
        userObjectives.add(objectiveCards.draw());

        listenerHandler.notify(username, receiver -> {
            // create the client player representation
            List<ClientCard> clientHand = new ArrayList<>();
            for (Card c : userHand) {
                clientHand.add(new ClientCard(c));
            }
            List<ClientCard> clientObjectives = new ArrayList<>();
            for (ObjectiveCard o : userObjectives) {
                clientObjectives.add(new ClientCard(o));
            }
            ClientPlayer clientPlayer = new ClientPlayer(
                    username,
                    new ClientCard(startingCard),
                    clientHand,
                    clientObjectives);
            receiver.showInitialPlayerStatus(clientPlayer);
        });
        return new Player(username, startingCard, userHand, userObjectives);
    }

    /**
     * Creates game based on the lobby
     */
    public Game(List<String> validUsernames) {
        loadAvailableColors();
        loadCards();
        try {
            players = new ArrayList<>();
            for (String username : validUsernames) {
                players.add(createPlayer(username));
            }
            Collections.shuffle(players);
        } catch (EmptyDeckException e) {
            // there must be enough cards for the beginning
            e.printStackTrace();
        }

        this.validUsernames = validUsernames;
        phase = GamePhase.Setup;
        currentPlayerIdx = 0;
        isFinished = false;
        isActive = true;
        chatDatabase = new ChatDatabase();
        phaseHandler = new PhaseHandler(players);
        listenerHandler = new ListenerHandler<>();
    }

    // methods

    private boolean isValidIdx(int idx) {
        return 0 <= idx && idx < players.size();
    }

    // find the next valid current player idx, that is, the first player still connected.
    private void updateCurrentPlayerIdx() {
        currentPlayerIdx = (currentPlayerIdx + 1) % players.size();
        Player currentPlayer = players.get(currentPlayerIdx);
        // the next player can only be in place state
        if (!currentPlayer.isConnected()) {
            skipTurn(currentPlayer.getUsername());
        }
    }

    /**
     * Constructs a game using the information stored in <code>gameBeforeCrash</code>.
     * In this way is possible to recover the game's status before the last crash.
     *
     * @param gameBeforeCrash the game's status before crashing.
     */
    public Game(Game gameBeforeCrash) {
        this.resourceCards = gameBeforeCrash.resourceCards;
        this.goldenCards = gameBeforeCrash.goldenCards;

        this.starterCards = gameBeforeCrash.starterCards;

        this.objectiveCards = gameBeforeCrash.objectiveCards;

        this.faceUpCards = gameBeforeCrash.faceUpCards;

        this.commonObjects = gameBeforeCrash.commonObjects;

        this.currentPlayerIdx = gameBeforeCrash.currentPlayerIdx;
        this.isFinished = gameBeforeCrash.isFinished;
        this.isActive = gameBeforeCrash.isActive;
        this.players = gameBeforeCrash.players;
        this.phase = gameBeforeCrash.phase;
        this.listenerHandler = gameBeforeCrash.listenerHandler;
        this.validUsernames = gameBeforeCrash.validUsernames;
        this.availableColors = gameBeforeCrash.availableColors;
        this.phaseHandler = gameBeforeCrash.phaseHandler;
        this.chatDatabase = gameBeforeCrash.chatDatabase;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIdx);
    }

    public GamePhase getPhase() {
        return phase;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isFinished() {
        return phase == GamePhase.End;
    }

    public List<Card> getFaceUpCards() {
        return faceUpCards;
    }

    public Set<PlayerColor> getAvailableColor() {
        return availableColors;
    }

    public void add(String username, VirtualView client) throws InvalidUsernameException {
        // only previously connected users can join the game
        if (!validUsernames.contains(username)) {
            throw new InvalidUsernameException();
        }

        listenerHandler.add(username, client);

        setNetworkStatus(username, true);

        if (getActivePlayers().size() > 1) {
            isActive = true;
        }

        listenerHandler.notify(username, receiver -> receiver.updateAfterConnection(new ClientGame(Game.this)));
    }

    public void remove(String username) throws InvalidUsernameException, RemoteException {
        if (!validUsernames.contains(username)) {
            throw new InvalidUsernameException();
        }
        listenerHandler.remove(username);
        setNetworkStatus(username, false);
        if (getActivePlayers().size() <= 1) {
            listenerHandler.notifyBroadcast(VirtualView::showUpdateSuspendedGame);
            isActive = false;
        }
    }

    private void setNetworkStatus(String username, boolean networkStatus) {
        players.stream()
                .filter(p -> p.getUsername().equals(username))
                .findFirst()
                .ifPresent(p -> p.setNetworkStatus(networkStatus));
    }

    /**
     * Returns a player based on the username.
     *
     * @param username of the player.
     * @return the player.
     */
    public Player getPlayerByUsername(String username) {
        return players.stream()
                .filter(p -> p.getUsername().equals(username))
                .findFirst()
                .get();
    }

    /**
     * Places the starter on the specified side.
     *
     * @param username of the player
     * @param side     of the starter
     * @throws InvalidPlayerActionException if the player cannot perform the operation. For example the player has already chosen the side.
     */
    public void placeStarter(String username, Side side)
            throws InvalidPlayerActionException,
            InvalidGamePhaseException {
        if (!phase.equals(GamePhase.Setup)) {
            throw new InvalidGamePhaseException();
        }

        Player player = getPlayerByUsername(username);

        try {
            player.placeStarter(side);
            listenerHandler.notify(username, receiver -> receiver.showStarterPlacement(username, player.getStarter().getFace(side).getId()));
        }
        // the starter card shouldn't cause any exception related to the playground
        catch (Playground.UnavailablePositionException | Playground.NotEnoughResourcesException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assigns at <code>username</code> the specified <code>color</code>.
     *
     * @param username of the player who has chose the color.
     * @param color    chosen.
     * @throws InvalidPlayerActionException if the player cannot perform this action.
     * @throws InvalidColorException        if the color has already been selected by others.
     * @throws NonexistentPlayerException   if the username is invalid.
     * @throws InvalidGamePhaseException    if the player has already finished their setup.
     */
    public void assignColor(String username, PlayerColor color) throws InvalidPlayerActionException, InvalidColorException, NonexistentPlayerException, InvalidGamePhaseException, RemoteException {
        if (!phase.equals(GamePhase.Setup)) {
            throw new InvalidGamePhaseException();
        }

        if (!availableColors.contains(color)) {
            throw new InvalidColorException();
        }

        Player player = getPlayerByUsername(username);
        player.assignColor(color);
        availableColors.remove(color);
        listenerHandler.notifyBroadcast(receiver -> receiver.showUpdateColor(color, username));
    }

    /**
     * Places the secret objective from one of the two available.
     *
     * @param username        of the player.
     * @param chosenObjective the chosen objective. 0 for the first one, 1 for the second one.
     * @throws InvalidPlayerActionException if the player cannot perform the operation. For example the player has already chosen the objective.
     * @throws InvalidGamePhaseException    if the player has already finished the setup.
     */
    public void placeObjectiveCard(String username, int chosenObjective) throws InvalidPlayerActionException, InvalidGamePhaseException {
        if (chosenObjective != 0 && chosenObjective != 1) {
            throw new IllegalArgumentException();
        }
        if (!phase.equals(GamePhase.Setup)) {
            throw new InvalidGamePhaseException();
        }

        Player player = getPlayerByUsername(username);

        player.placeObjectiveCard(chosenObjective);

        phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);

        listenerHandler.notifyBroadcast(receiver -> {
            ObjectiveCard secretObjective = player.getObjective();
            receiver.showUpdateObjectiveCard(new ClientCard(secretObjective), username);
        });
    }

    /**
     * Places the card on the side and position specified.
     *
     * @param username of the player.
     * @param card     to place.
     * @param side     of the card.
     * @param position in the playground.
     * @throws InvalidPlayerActionException            if the player cannot perform the operation.
     * @throws Playground.UnavailablePositionException if the position is not available. For example the player is trying to place the card in an already covered corner.
     * @throws Playground.NotEnoughResourcesException  if the player's resource are not enough to place the card.
     * @throws InvalidGamePhaseException               if the game phase cannot allow placing cards.
     */
    public void placeCard(String username, Card card, Side side, Position position)
            throws InvalidPlayerActionException,
            Playground.UnavailablePositionException,
            Playground.NotEnoughResourcesException,
            InvalidGamePhaseException, SuspendedGameException {

        if (!isActive) {
            throw new SuspendedGameException();
        }

        if (!phase.equals(GamePhase.PlaceNormal) && !phase.equals(GamePhase.PlaceAdditional)) {
            throw new InvalidGamePhaseException();
        }

        Player currentPlayer = getPlayerByUsername(username);

        if (!currentPlayer.getUsername().equals(username)) {
            throw new InvalidPlayerActionException();
        }

        int newScore = currentPlayer.placeCard(card, side, position);

        if (newScore >= MAX_POINT) {
            phaseHandler.setLastNormalTurn();
        }

        phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);

        if (phase == GamePhase.PlaceAdditional) {
            updateCurrentPlayerIdx();
        }

        listenerHandler.notifyBroadcast(new Notifier<VirtualView>() {
            @Override
            public void sendUpdate(VirtualView receiver) throws RemoteException {
                Map<Position, CornerPosition> positionToCornerCovered = new HashMap<>();
                List<CornerPosition> cornerToTest = Arrays.asList(CornerPosition.LOWER_LEFT, CornerPosition.TOP_LEFT, CornerPosition.TOP_RIGHT, CornerPosition.LOWER_RIGHT);
                Playground playground = currentPlayer.getPlayground();
                for (CornerPosition cornerPosition : cornerToTest) {
                    Position adjacentPos = playground.getAdjacentPosition(position, cornerPosition);
                    if (playground.getTile(adjacentPos).sameAvailability(Availability.OCCUPIED)) {
                        positionToCornerCovered.put(adjacentPos, cornerPosition);
                    }
                }
                receiver.showUpdateAfterPlace(positionToCornerCovered, currentPlayer.getPlayground().getAvailablePositions(), playground.getResources(), currentPlayer.getPoints(), username, new ClientCard(card), position);
            }
        });

        if (phase == GamePhase.End) {
            listenerHandler.notifyBroadcast(receiver -> receiver.showWinners(getWinners()));
        }
    }

    /**
     * Draws a card from the deck specified.
     *
     * @param username of the player to draw.
     * @param deckType of the deck to draw a card from.
     * @throws InvalidPlayerActionException if the player cannot perform the operation.
     * @throws EmptyDeckException           if the selected deck is empty.
     * @throws InvalidGamePhaseException    if the game phase doesn't allow the operation.
     */
    public void drawFromDeck(String username, DeckType deckType) throws InvalidPlayerActionException, EmptyDeckException, InvalidGamePhaseException {
        if (!phase.equals(GamePhase.DrawNormal)) {
            throw new InvalidGamePhaseException();
        }

        Player currentPlayer = players.get(currentPlayerIdx);

        if (!currentPlayer.getUsername().equals(username)) {
            throw new InvalidPlayerActionException();
        }

        Deck<Card> deck;
        if (deckType.equals(DeckType.GOLDEN))
            deck = goldenCards;
        else
            deck = resourceCards;

        Card newCard = deck.draw();

        try {
            currentPlayer.addCard(newCard);
            if (goldenCards.isEmpty() && resourceCards.isEmpty()) {
                phaseHandler.setLastNormalTurn();
            }
            phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);

            updateCurrentPlayerIdx();

        } catch (InvalidPlayerActionException invalidPlayerActionException) {
            deck.add(newCard);
            throw invalidPlayerActionException;
        }

        listenerHandler.notifyBroadcast(new Notifier<VirtualView>() {
            @Override
            public void sendUpdate(VirtualView receiver) throws RemoteException {
                receiver.showUpdateAfterDraw(new ClientCard(newCard), deck.isEmpty(), new ClientCard(deck.getTop()), null, null, phase == GamePhase.PlaceAdditional, username, convertDeckTypeIntoId(deckType));
            }
        });
    }

    /**
     * Fills the missing spot on faceUpCards
     *
     * @param faceUpCardIdx the spot of the taken card
     * @return the deck from which the face up card has been replaced
     */
    private Deck<Card> replaceFaceUpCard(int faceUpCardIdx) {
        List<Deck<Card>> decks = Arrays.asList(resourceCards, goldenCards);
        int deckIdx = faceUpCardIdx <= 1 ? 0 : 1;

        Card replacement = null;
        Deck<Card> deckForReplacement = null;
        try {
            // updates the index of deckIdx based on the supposed deck's emptiness
            deckIdx = deckIdx + (decks.get(deckIdx).isEmpty() ? 0 : 1) % 2;
            deckForReplacement = decks.get(deckIdx);
            replacement = deckForReplacement.draw();
        } catch (EmptyDeckException e) {
            // the replacement may be invalid if both decks are empty, so there's no exception to throw
        }

        faceUpCards.set(faceUpCardIdx, replacement); // null if there's none card for replacement

        if (goldenCards.isEmpty() && resourceCards.isEmpty()) {
            phaseHandler.setLastNormalTurn();
        }

        return deckForReplacement;
    }

    /**
     * Draws from one of the available face up cards.
     *
     * @param username      of the player.
     * @param faceUpCardIdx specifying the face up card.
     * @throws InvalidPlayerActionException if the player cannot perform the operation.
     */
    public void drawFromFaceUpCards(String username, int faceUpCardIdx) throws InvalidPlayerActionException, InvalidGamePhaseException {
        if (!phase.equals(GamePhase.DrawNormal)) {
            throw new InvalidGamePhaseException();
        }

        Player currentPlayer = getPlayerByUsername(username);

        if (!currentPlayer.getUsername().equals(username)) {
            throw new InvalidPlayerActionException();
        }

        assert (isValidIdx(faceUpCardIdx));

        Card newCard = faceUpCards.get(faceUpCardIdx);

        Deck<Card> deckForReplacement;
        try {
            currentPlayer.addCard(newCard);
            deckForReplacement = replaceFaceUpCard(faceUpCardIdx);
            phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);
            updateCurrentPlayerIdx();
        } catch (InvalidPlayerActionException e) {
            throw new InvalidPlayerActionException();
        }

        listenerHandler.notifyBroadcast(new Notifier<VirtualView>() {
            @Override
            public void sendUpdate(VirtualView receiver) throws RemoteException {
                receiver.showUpdateAfterDraw(
                        new ClientCard(newCard),
                        deckForReplacement.isEmpty(),
                        new ClientCard(deckForReplacement.getTop()),
                        new ClientCard(faceUpCards.get(faceUpCardIdx)),
                        new ClientCard(deckForReplacement.getTop()),
                        phase == GamePhase.PlaceAdditional,
                        username, faceUpCardIdx);
            }
        });
    }

    /**
     * Skips the current player's turn.
     * The method is invoked whenever the current player is not active.
     */
    public void skipTurn(String currentPlayer) {
        if (phase != GamePhase.PlaceNormal && phase != GamePhase.PlaceAdditional) {
            throw new RuntimeException("A turn can be skipped only at the beginning");
        }
        if (currentPlayer.equals(players.get(currentPlayerIdx).getUsername())) {
            throw new RuntimeException("Only the current player can skip their turn");
        }

        if (phase == GamePhase.PlaceNormal) {
            // additional getNextPhase is required because the player has to skip the placement and the draw
            phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);
        }

        phase = phaseHandler.getNextPhase(phase, currentPlayerIdx);
    }

    /**
     * Registers message sent by author.
     *
     * @param author  of the message.
     * @param message to register.
     * @throws InvalidMessageException if the author doesn't match the sender or the recipient is an invalid username.
     */
    public void registerMessage(String author, Message message) throws InvalidMessageException {
        if (!message.getSender().equals(author)) {
            throw new InvalidMessageException("sender doesn't match the author's username");
        }
        if (getPlayerByUsername(message.getRecipient()) == null) {
            throw new InvalidMessageException("recipient doesn't exists");
        }
        chatDatabase.addMessage(message);
        listenerHandler.notifyBroadcast(receiver -> receiver.showUpdateChat(message));
    }

    /**
     * Returns a list of active players.
     *
     * @return an active player's list.
     */
    public List<Player> getActivePlayers() {
        return players.stream()
                .filter(Player::isConnected)
                .toList();
    }

    /**
     * Gets the card associated to the <code>cardId</code>.
     *
     * @param username the player's username.
     * @param frontId  the id of the card's front.
     * @param backId   the id of the card's back.
     * @return the player's card associated with <code>cardId</code>.
     */
    public Card getCard(String username, int frontId, int backId) {
        Player player = getPlayerByUsername(username);
        return player.getCard(frontId, backId);
    }

    /**
     * Returns winners' name.
     *
     * @return the list of players' name winning the game (they can be more than one in case of a tie).
     */
    private List<String> getWinners() {
        if (phase != GamePhase.End) {
            System.err.println("Error: game not finished yet");
        }

        int maxPointNormalTurns = 29;
        int maxPoints = -1;
        List<Player> winners = new ArrayList<>();

        for (Player player : players) {
            int finalPoints = min(maxPointNormalTurns, player.getPoints()) + player.calculateExtraPoints(commonObjects);
            boolean win = false;
            boolean tie = false;

            if (finalPoints > maxPoints) {
                win = true;
            } else if (finalPoints == maxPoints) {
                int winnerSatisfiedObjectives = winners.getFirst().getNumSatisfiedObjectives();
                int playerObjectives = player.getNumSatisfiedObjectives();

                if (winnerSatisfiedObjectives < playerObjectives) {
                    win = true;
                } else if (winnerSatisfiedObjectives == playerObjectives) {
                    tie = true;
                }
            }

            if (win) {
                maxPoints = finalPoints;
                winners.clear();
                winners.add(player);
            }
            if (tie) {
                winners.add(player);
            }
        }
        return winners.stream().map(Player::getUsername).toList();
    }

    public void terminateForInactivity() {
        try {
            String lastConnectedPlayer = getActivePlayers().getFirst().getUsername();
            listenerHandler.notifyBroadcast(receiver -> receiver.showWinners(Collections.singletonList(lastConnectedPlayer)));
        } catch (NoSuchElementException noSuchElementException) {
            // empty list: there's no player to notify
        }
    }

    private int convertDeckTypeIntoId(DeckType type){
        if(DeckType.GOLDEN == type){
            return 4;
        }
        else{
            return 5;
        }
    }

}

