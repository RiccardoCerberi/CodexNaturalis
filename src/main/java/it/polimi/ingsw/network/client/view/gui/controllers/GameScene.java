package it.polimi.ingsw.network.client.view.gui.controllers;

import it.polimi.ingsw.model.InvalidGamePhaseException;
import it.polimi.ingsw.model.SuspendedGameException;
import it.polimi.ingsw.model.board.Availability;
import it.polimi.ingsw.model.board.Playground;
import it.polimi.ingsw.model.board.Position;
import it.polimi.ingsw.model.card.color.PlayerColor;
import it.polimi.ingsw.model.card.Side;
import it.polimi.ingsw.model.chat.message.InvalidMessageException;
import it.polimi.ingsw.model.chat.message.Message;
import it.polimi.ingsw.model.gamephase.GamePhase;
import it.polimi.ingsw.network.client.controller.ClientController;
import it.polimi.ingsw.network.client.model.board.ClientPlayground;
import it.polimi.ingsw.network.client.model.card.ClientCard;
import it.polimi.ingsw.network.client.model.player.ClientPlayer;
import it.polimi.ingsw.network.client.view.gui.util.GUIPlayground;
import it.polimi.ingsw.network.client.view.gui.util.BoardPane;
import it.polimi.ingsw.network.client.view.gui.util.PlayerInfoPane;
import it.polimi.ingsw.network.client.view.gui.util.PlaygroundInfoPane;
import it.polimi.ingsw.network.client.view.gui.util.chat.Chat;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.rmi.RemoteException;
import java.util.*;
import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsw.network.client.view.gui.util.GUICards.*;
import static it.polimi.ingsw.network.client.view.gui.util.GUIUtil.*;

/**
 * GameScene is the controller concerning game scene
 */
public class GameScene extends SceneController {

    @FXML
    private Pane mainPane;

    @FXML
    private Pane playgroundPane;

    @FXML
    private TextFlow sentMessages;

    @FXML
    private ComboBox<String> recipients;

    @FXML
    private TextField text;

    @FXML
    private ScrollPane chatPane;

    @FXML
    private Pane currentPlayerPane;

    private Text currentPlayerUsername;

    private Text currentPhase;

    private Pane mainPlayerCardsPane;

    private PlaygroundInfoPane playgroundInfoPane;

    private BoardPane boardPane;

    private List<PlayerInfoPane> playerInfoPanes;

    private List<Side> playerCardsVisibleSide;

    private List<Rectangle> mainPlayerCards;

    private List<Rectangle> availablePositions;

    private String currentVisiblePlaygroundOwner;

    private int selectedCardHandPosition;

    private Chat chat;

    public GameScene() {
    }

    /**
     * {@inheritDoc}
     */
    public void initialize() {

        mainPane.setBackground(createMainBackground());
        playerCardsVisibleSide = new ArrayList<>();
        availablePositions = new ArrayList<>();
        mainPlayerCards = new ArrayList<>();
        selectedCardHandPosition = -1;
        sentMessages.minWidthProperty().bind(chatPane.widthProperty().subtract(8));
    }

    private boolean isReferringToMe(Message m) {
        String myName = gui.getController().getMainPlayerUsername();
        return
                m.getRecipient().equals(myName)
                        ||
                        m.getSender().equals(myName)
                        ||
                        m.isBroadcast();
    }

    private void appendMessage(Message m) {
        ClientController controller = gui.getController();
        String myName = controller.getMainPlayerUsername();
        String preposition;
        String user;
        String sender = m.getSender();
        String recipient = m.getRecipient();
        if (sender.equals(myName)) {
            preposition = " to";
            user = recipient;
        } else {
            if (m.getRecipient().equals("Everyone")) {
                preposition = " <Everyone> from";
            } else {
                preposition = " <Private> from";
            }
            user = sender;
        }
        Text userText = new Text(user);
        if (!user.equals(Message.getNameForGroup())) {
            userText.setStyle("-fx-font-weight: bold;" + "-fx-fill:"
                    +
                    convertPlayerColorIntoHexCode(controller.getPlayer(user).getColor())
            );
        }
        Text content = new Text(" : " + m.getContent() + "\n");
        sentMessages.getChildren().addAll(new Text(preposition + " "), userText, content);
    }

    private void initializeChat() {
        Text chatTitle = new Text("Chat");
        chatTitle.setFont(loadTitleFont(25));
        chatTitle.setLayoutX(1028);
        chatTitle.setLayoutY(214);
        ClientController controller = gui.getController();
        List<String> usernames = new ArrayList<>(controller.getUsernames());
        String myName = controller.getMainPlayerUsername();
        usernames.remove(myName);
        usernames.add("Everyone");
        recipients.getItems().addAll(usernames);
        chat = new Chat(myName);
        List<Message> messages = new ArrayList<>(gui.getController().getMessages());
        for (Message m : messages) {
            if (isReferringToMe(m)) {
                appendMessage(m);
            }
        }
        mainPane.getChildren().add(chatTitle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeUsingGameInformation() {
        ClientController controller = gui.getController();
        synchronized (controller) {
            super.initializeUsingGameInformation();
            currentVisiblePlaygroundOwner = gui.getController().getMainPlayerUsername();

            initializePlayerInfoBox();
            initializeMainPlayerCardPane();
            drawPlayground(gui.getController().getMainPlayerPlayground());
            initializeBoard();
            initializeChat();
            initializePlaygroundInfoPane();
            addButtonPane(mainPane, buttonPane, 1028, 637);
            initializeCurrentPlayerUsernameText();
            initializeCurrentPhaseText();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void removeUpdatePaneFromMainPane(StackPane errorPane) {
        mainPane.getChildren().remove(errorPane);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showError(String details) {
        StackPane errorPane = generateError(details);
        errorPane.setLayoutX((getSceneWindowWidth() - errorPaneWidth) / 2);
        errorPane.setLayoutY(10);
        mainPane.getChildren().add(errorPane);
    }

    private void initializeCurrentPlayerUsernameText() {
        Text currentPlayerText = new Text("Current Player: ");
        currentPlayerText.setFont(loadFontLiberationSerifRegular(15.5));
        currentPlayerText.setLayoutX(8);
        currentPlayerText.setLayoutY(25);

        currentPlayerUsername = new Text();
        updateCurrentPlayerUsername();
        currentPlayerUsername.setLayoutX(109);
        currentPlayerUsername.setLayoutY(25);

        currentPlayerPane.getChildren().add(currentPlayerText);
        currentPlayerPane.getChildren().add(currentPlayerUsername);
    }

    private void initializeCurrentPhaseText() {

        Text currentPhaseTitle = new Text("Current Phase: ");
        currentPhaseTitle.setFont(loadFontLiberationSerifRegular(15.5));
        currentPhaseTitle.setLayoutX(8);
        currentPhaseTitle.setLayoutY(50);

        currentPhase = new Text();
        updateCurrentPhase();
        currentPhase.setLayoutX(105);
        currentPhase.setLayoutY(50);

        currentPlayerPane.getChildren().add(currentPhase);
        currentPlayerPane.getChildren().add(currentPhaseTitle);
    }

    /**
     * Updates the username of the current player
     */
    public void updateCurrentPlayerUsername() {
        ClientController controller = gui.getController();
        currentPlayerUsername.setText(controller.getCurrentPlayerUsername());
        if (controller.isGameActive()) {
            showCurrentPlayerUpdatePane();
        }
        currentPlayerUsername.setFont(loadFontLiberationSerifRegular(13.5));
    }

    private void showCurrentPlayerUpdatePane() {
        ClientController controller = gui.getController();

        if (controller.getCurrentPlayerUsername().equals(controller.getMainPlayerUsername())) {
            StackPane currentPlayerUpdatePane = generateUpdatePane("It's your turn");
            currentPlayerUpdatePane.setStyle("-fx-background-color: #3CB371;" + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0.2, 0, 0);" + " -fx-background-radius: 10px;");
            currentPlayerUpdatePane.setLayoutY((getSceneWindowHeight() - updatePaneHeight - 10) / 2);
            mainPane.getChildren().add(currentPlayerUpdatePane);
        }
    }

    /**
     * Updates the current phase
     */
    public void updateCurrentPhase() {
        ClientController controller = gui.getController();
        GamePhase phase = controller.getGamePhase();

        if (controller.isGameActive()) {
            if ((phase == GamePhase.PlaceNormal || phase == GamePhase.DrawNormal) && !currentPhase.getText().equals("Normal Turn")) {
                StackPane phaseUpdatePane = generateUpdatePane("Normal Phase");
                phaseUpdatePane.setStyle("-fx-background-color: #3CB371;" + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0.2, 0, 0);" + " -fx-background-radius: 10px;");
                mainPane.getChildren().add(phaseUpdatePane);
                currentPhase.setText("Normal Turn");
                currentPhase.setFill(Color.web("#3CB371"));
            } else if (phase == GamePhase.PlaceAdditional && !currentPhase.getText().equals("Additional Turn")) {
                currentPhase.setText("Additional Turn");
                StackPane phaseUpdatePane = generateUpdatePane("Additional Phase");
                phaseUpdatePane.setStyle("-fx-background-color: #0077B6;" + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0.2, 0, 0);" + " -fx-background-radius: 10px;");
                mainPane.getChildren().add(phaseUpdatePane);
                currentPhase.setFill(convertPlayerColor(PlayerColor.BLUE));
            }
            currentPhase.setFont(loadFontLiberationSerifRegular(13.5));
        }
    }

    /**
     * Updates the phase to suspended if the game is not active
     */
    public void updateSuspendedGame() {
        ClientController controller = gui.getController();

        if (!controller.isGameActive()) {
            StackPane phaseUpdatePane = generateUpdatePane("GAME IS SUSPENDED");
            phaseUpdatePane.setStyle("-fx-background-color: #FF0000;" + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0.2, 0, 0);" + " -fx-background-radius: 10px;");
            mainPane.getChildren().add(phaseUpdatePane);
            currentPhase.setText("GAME SUSPENDED");
            currentPhase.setFill(convertPlayerColor(PlayerColor.RED));
        } else {
            showCurrentPlayerUpdatePane();
            updateCurrentPhase();
        }
    }

    private StackPane generateUpdatePane(String updateMessage) {
        StackPane updatePane = new StackPane();
        updatePane.setPrefSize(updatePaneWidth, updatePaneHeight);
        Label updateMessageLabel = new Label();
        updateMessageLabel.setStyle("-fx-text-fill: #000000;");
        updateMessageLabel.setFont(loadFontLiberationSerifBold(20.5));
        updateMessageLabel.setWrapText(true);
        updatePane.getChildren().add(updateMessageLabel);
        StackPane.setAlignment(updateMessageLabel, Pos.CENTER);
        updateMessageLabel.setText(updateMessage);
        updatePane.setLayoutX((getSceneWindowWidth() - updatePaneWidth) / 2);
        updatePane.setLayoutY((getSceneWindowHeight() + updatePaneHeight) / 2);

        Timeline timeline = new Timeline(new KeyFrame(
                Duration.seconds(1),
                timerEndEvent -> fadeOutUpdatePane(updatePane)
        ));

        timeline.play();


        return updatePane;
    }

    private void initializePlaygroundInfoPane() {
        playgroundInfoPane = new PlaygroundInfoPane();
        playgroundInfoPane.setPlaygroundInfo(gui.getController().getMainPlayer(), true);
        mainPane.getChildren().add(playgroundInfoPane.getMainPane());
        initializeReturnToMainPlayground();
        playgroundInfoPane.updateRank(gui.getController().getPlayerRank(gui.getController().getMainPlayerUsername()));
        playgroundInfoPane.updateScore(gui.getController().getMainPlayerPlayground().getPoints());
    }

    private void initializeReturnToMainPlayground() {

        ImageView returnToMainPlayground = playgroundInfoPane.getReturnToMainPlayground();
        returnToMainPlayground.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (returnToMainPlayground.isVisible() && isClicked(mouseEvent, MouseButton.PRIMARY)) {
                    changeVisiblePlayground(gui.getController().getMainPlayerUsername());
                }
            }
        });
    }


    private void initializePlayerInfoBox() {

        playerInfoPanes = new ArrayList<>();


        int distance = 20;
        int layoutX = 44;
        int layoutY = 14;

        ClientController controller = gui.getController();
        for (ClientPlayer player : controller.getPlayers()) {
            if (!player.getUsername().equals(controller.getMainPlayerUsername())) {
                PlayerInfoPane playerInfoPane = new PlayerInfoPane(player);
                playerInfoPane.updateRank(controller.getPlayerRank(player.getUsername()));
                playerInfoPane.updateScore(player.getScore());
                Pane pane = playerInfoPane.getPlayerMainPane();
                pane.setLayoutX(layoutX);
                pane.setLayoutY(layoutY);
                mainPane.getChildren().add(pane);
                playerInfoPanes.add(playerInfoPane);
                initializeSwitchPlayground(playerInfoPane);
                layoutX = layoutX + 361 + distance;
            }
        }
    }

    private void initializeMainPlayerCardPane() {
        mainPlayerCardsPane = new Pane();

        Text secretObjectiveTitle = new Text();
        secretObjectiveTitle.setFont(loadFontLiberationSerifRegular(15.5));
        secretObjectiveTitle.setLayoutY(630.5);
        secretObjectiveTitle.setLayoutX(345);
        secretObjectiveTitle.setText("Secret Objective");

        Text playerCardsTitle = new Text();
        playerCardsTitle.setFont(loadFontLiberationSerifRegular(15.5));
        playerCardsTitle.setLayoutY(630.5);
        playerCardsTitle.setLayoutX(545);
        playerCardsTitle.setText("Your Cards");

        mainPane.getChildren().add(playerCardsTitle);
        mainPane.getChildren().add(secretObjectiveTitle);

        mainPlayerCardsPane.setPrefSize(700, 90);
        mainPlayerCardsPane.setLayoutX(345);
        mainPlayerCardsPane.setLayoutY(640);
        initializeMainPlayerObjectiveCard();
        initializeMainPlayerCards();
        mainPane.getChildren().add(mainPlayerCardsPane);
    }


    private void initializeMainPlayerObjectiveCard() {
        double layoutX = 0.0;
        Rectangle rectangle = new Rectangle(playerCardsWidth, playerCardsHeight);
        rectangle.setLayoutX(layoutX);
        rectangle.setFill(pathToImage(gui.getController().getMainPlayerObjectiveCard().getPath()));
        mainPlayerCardsPane.getChildren().add(rectangle);
        //mainPlayerCardsPane.setBackground(setBackgroundColor("EEE5BC"));
    }


    private void initializeBoard() {
        boardPane = new BoardPane(gui.getController().getBoard());
        initializeBoardCards(boardPane);
        mainPane.getChildren().add(boardPane.getBoardMainPane());
    }

    private ImagePattern getFacePath(String username, int cardHandPosition, Side side) {
        String path = gui.getController().getPlayer(username).getPlayerCard(cardHandPosition).getSidePath(side);
        return new ImagePattern(new Image(path));
    }

    private void initializeMainPlayerCards() {

        for (Rectangle card : mainPlayerCards) {
            mainPlayerCardsPane.getChildren().remove(card);
        }
        mainPlayerCards.clear();

        double layoutX = 200;
        List<ClientCard> cards = new ArrayList<>(gui.getController().getMainPlayerCards());

        for (int i = 0; i < cards.size(); i++) {

            int cardHandPosition = i;
            Rectangle rectangle = new Rectangle(playerCardsWidth, playerCardsHeight);
            ImagePattern backImage = new ImagePattern(new Image(cards.get(i).getBackPath()));
            ImagePattern frontImage = new ImagePattern(new Image(cards.get(i).getFrontPath()));
            rectangle.setFill(frontImage);
            playerCardsVisibleSide.add(i, Side.FRONT);
            rectangle.setLayoutX(layoutX);
            layoutX = layoutX + playerCardsWidth + 30;

            rectangle.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent mouseEvent) {
                    if (isClicked(mouseEvent, MouseButton.SECONDARY)) {
                        if (rectangle.getFill() == frontImage) {
                            rectangle.setFill(backImage);
                            playerCardsVisibleSide.set(cardHandPosition, Side.BACK);
                            return;
                        }
                        rectangle.setFill(frontImage);
                        playerCardsVisibleSide.set(cardHandPosition, Side.FRONT);

                    } else if (isClicked(mouseEvent, MouseButton.PRIMARY)) {
                        if (!currentVisiblePlaygroundOwner.equals(gui.getController().getMainPlayerUsername())) {
                            changeVisiblePlayground(gui.getController().getMainPlayerUsername());
                        }
                        selectCard(cardHandPosition);
                    }
                }

            });

            mainPlayerCardsPane.getChildren().add(rectangle);
            mainPlayerCards.add(rectangle);
        }

    }

    /**
     * Updates the current status of the players
     */
    public void updatePlayersStatus() {
        for (ClientPlayer player : gui.getController().getPlayers()) {
            if (!player.getUsername().equals(gui.getController().getMainPlayerUsername())) {
                Objects.requireNonNull(getPlayerInfoPane(player.getUsername())).updateStatus(player.isConnected());
            }

        }
    }

    private void initializeSwitchPlayground(PlayerInfoPane playerInfoPane) {

        ImageView switchPlayground = playerInfoPane.getSwitchPlayground();
        switchPlayground.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (selectedCardHandPosition == -1 && isClicked(mouseEvent, MouseButton.PRIMARY)) {
                    changeVisiblePlayground(playerInfoPane.getPlayerUsername());
                }
            }
        });

    }

    private void selectCard(int cardHandPosition) {

        boolean availableTilesVisibility;

        //case the card I click is already selected
        if (selectedCardHandPosition == cardHandPosition) {
            selectedCardHandPosition = -1;
            availableTilesVisibility = false;
        }
        //case there aren't cards selected or the card selected was one clicked in the past
        else {
            selectedCardHandPosition = cardHandPosition;
            availableTilesVisibility = true;
        }

        for (Rectangle availableTile : availablePositions) {
            availableTile.setVisible(availableTilesVisibility);
        }

    }

    /**
     * Method used to draw the playground
     *
     * @param clientPlayground the representation of the playground
     */
    public void drawPlayground(ClientPlayground clientPlayground) {

        // CRITICAL
        //do not remove
        for (Rectangle rectangle : availablePositions) {
            mainPane.getChildren().remove(rectangle);
        }

        availablePositions.clear();

        GUIPlayground guiPlayground = new GUIPlayground(playerCardsWidth, playerCardsHeight);
        guiPlayground.setDimension(clientPlayground);
        playgroundPane.setPrefSize(guiPlayground.getPaneWidth(), guiPlayground.getPaneHeight());
        List<Rectangle> cardsAsRectangles = new ArrayList<>();


        // clean the pane
        playgroundPane.getChildren().clear();

        //it's necessary to add available position after the occupied one

        for (Position pos : clientPlayground.getPositioningOrder()) {
            if (!clientPlayground.getTile(pos).sameAvailability(Availability.EMPTY)) {
                playgroundPane.getChildren().add(guiPlayground.getRectangle(pos, pathToImage(clientPlayground.getTile(pos).getFace().getPath())));
            } else {
                if (clientPlayground.getAvailablePositions().contains(pos)) {
                    playgroundPane.getChildren().add(guiPlayground.getRectangleEmptyTile(pos));
                }

            }
        }

        for (Position pos : clientPlayground.getAvailablePositions()) {
            Rectangle rectangle = guiPlayground.getRectangleEmptyTile(pos);
            rectangle.setVisible(false);
            rectangle.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent mouseEvent) {

                    if (isClicked(mouseEvent, MouseButton.PRIMARY) && selectedCardHandPosition != -1) {

                        try {
                            gui.getController().placeCard(selectedCardHandPosition, playerCardsVisibleSide.get(selectedCardHandPosition), pos);
                        } catch (Playground.UnavailablePositionException | InvalidGamePhaseException |
                                 SuspendedGameException | Playground.NotEnoughResourcesException e) {

                            showError(e.getMessage());
                            selectedCardHandPosition = -1;
                        }

                        //selectedCardHandPosition = -1;

                        for (Rectangle availableTile : availablePositions) {
                            availableTile.setVisible(false);
                        }
                    }
                }

            });
            availablePositions.add(rectangle);
            playgroundPane.getChildren().add(rectangle);
        }
    }

    private void initializeBoardCards(BoardPane boardPane) {
        setIdToDraw(boardPane.getGoldenDeckTopCard(), 4);
        setIdToDraw(boardPane.getResourceDeckTopCard(), 5);
        setIdToDraw(boardPane.getGoldenFaceUp().getFirst(), 2);
        setIdToDraw(boardPane.getGoldenFaceUp().getLast(), 3);
        setIdToDraw(boardPane.getResourceFaceUp().getFirst(), 0);
        setIdToDraw(boardPane.getResourceFaceUp().getLast(), 1);
    }

    private void setIdToDraw(Rectangle card, int idToDraw) {

        card.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (isClicked(mouseEvent, MouseButton.PRIMARY) && gui.getController().getGamePhase() == GamePhase.DrawNormal) {
                    try {
                        gui.getController().draw(idToDraw);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Method used to update the screen after a draw
     *
     * @param username the username of the player
     */
    public void updateAfterDraw(String username) {
        ClientController controller = gui.getController();
        synchronized (controller) {
            if (username.equals(gui.getController().getMainPlayerUsername())) {
                initializeMainPlayerCards();
            } else {
                PlayerInfoPane playerInfoPane = getPlayerInfoPane(username);
                assert playerInfoPane != null;
                playerInfoPane.updatePlayerCards(gui.getController().getPlayer(username).getPlayerCards());
            }

            boardPane.boardUpdate(gui.getController().getBoard());
            for (Rectangle emptyFace : boardPane.getEmptySlotsToInitialize()) {
                emptyFace.setOnMouseClicked(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent mouseEvent) {
                        if (isClicked(mouseEvent, MouseButton.PRIMARY)) {
                            showError("You can't draw from an empty slot, please try again");
                        }
                    }
                });
            }

            boardPane.setEmptySlotsToInitialize(new ArrayList<>());
        }
    }


    private void changeVisiblePlayground(String username) {
        currentVisiblePlaygroundOwner = username;
        updatePlayground(username);
    }

    private void updatePlayground(String username) {
        ClientController controller = gui.getController();
        synchronized (controller) {
            drawPlayground(gui.getController().getPlaygroundByUsername(username));
            playgroundInfoPane.setPlaygroundInfo(gui.getController().getPlayer(username), username.equals(gui.getController().getMainPlayerUsername()));
        }
    }

    /**
     * Method used to update the screen after a placement
     *
     * @param username the username of the player
     */
    public void updateAfterPlace(String username) {
        ClientController controller = gui.getController();
        synchronized (controller) {
            if (username.equals(currentVisiblePlaygroundOwner)) {
                updatePlayground(username);
            } else if (username.equals(gui.getController().getMainPlayerUsername())) {
                changeVisiblePlayground(gui.getController().getMainPlayerUsername());
            }

            if (!username.equals(gui.getController().getMainPlayerUsername())) {
                Objects.requireNonNull(getPlayerInfoPane(username)).updateResources(gui.getController().getPlaygroundByUsername(username).getResources());
            } else {
                mainPlayerCards.get(selectedCardHandPosition).setVisible(false);
                selectedCardHandPosition = -1;
            }

            for (PlayerInfoPane playerInfoPane : playerInfoPanes) {
                playerInfoPane.updateRank(gui.getController().getPlayerRank(playerInfoPane.getPlayerUsername()));
                playerInfoPane.updateScore(gui.getController().getPlaygroundByUsername(playerInfoPane.getPlayerUsername()).getPoints());
            }
            playgroundInfoPane.updateRank(gui.getController().getPlayerRank(gui.getController().getMainPlayerUsername()));
            playgroundInfoPane.updateScore(gui.getController().getMainPlayerPlayground().getPoints());
        }
    }

    /*
    private void setPlaygroundFrameColor(){
        ClientPlayer player = gui.getController().getPlayer(currentVisiblePlaygroundOwner);
        framePane.setBackground(setBackgroundColor("#FF0000"));
        framePane.setVisible(true);
    }

     */

    private PlayerInfoPane getPlayerInfoPane(String username) {
        for (PlayerInfoPane playerInfoPane : playerInfoPanes) {
            if (playerInfoPane.getPlayerUsername().equals(username)) {
                return playerInfoPane;
            }
        }

        return null;
    }

    @FXML
    private void selectRecipient() {
        chat.selectRecipient(recipients.getValue());
    }

    @FXML
    private void sendMessage(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            chat.insertText(text.getText());
            try {
                chat.sendMessage(gui.getController());

            } catch (InvalidMessageException e) {
                showError("Invalid message\n" + e.getMessage());
            }
            text.setText("");
        }
    }

    /**
     * Method used to receive a message
     *
     * @param message the incoming message
     */
    public void receiveMessage(Message message) {
        if (isReferringToMe(message)) {
            appendMessage(message);
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getSceneWindowWidth() {
        return startedGameSceneWidth;
    }

    /**
     * {@inheritDoc}
     */
    public double getSceneWindowHeight() {
        return startedGameSceneHeight;
    }


}
