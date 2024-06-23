package it.polimi.ingsw.network.client.view.gui.controllers;

import it.polimi.ingsw.model.lobby.InvalidPlayersNumberException;
import it.polimi.ingsw.network.client.view.gui.util.GUIUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import java.rmi.RemoteException;
import java.util.List;

import static it.polimi.ingsw.network.client.view.gui.util.GUIUtil.*;
import static it.polimi.ingsw.network.client.view.gui.util.GUIUtil.connectionSceneHeight;

public class LobbyScene extends SceneController {

    @FXML
    private Pane mainPane;

    @FXML
    private TextArea connectedPlayers;

    @FXML
    private TextArea setNumberRequest;

    @FXML
    private ComboBox<Integer> numberPlayerCatcher;

    @FXML
    private Text requiredPlayer;


    public LobbyScene() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        numberPlayerCatcher.getItems().addAll(2, 3, 4);
        Text title = new Text("Lobby");
        title.setFont(loadTitleFont(60));
        title.setLayoutY(180);
        title.setLayoutX(580);
        title.setStrokeType(StrokeType.OUTSIDE);
        mainPane.getChildren().add(title);
        //connectedPlayers.setText("1 - " + gui.getController().getMainPlayerUsername());
        numberPlayerCatcher.setVisible(false);
        setNumberRequest.setVisible(false);
        mainPane.setBackground(createMainBackground());

        //todo fix when fullScreen
    }

    /**
     * {@inheritDoc}
     */
    public void initializeUsingGameInformation() {
        super.initializeUsingGameInformation();
        addButtonPane(mainPane, buttonPane, 1028, 637);
    }



    /**
     * Method used to show the players required to start the game
     */
    public void showRequiredPlayers() {
        //todo requiredPlayer.setText("Player required to play: " + gui.getController().getRequiredPlayer);
        requiredPlayer.setVisible(true);
    }

    /**
     * Initializes the creator scene, makes <code>numberPlayerCatcher</code> and <code>setNumberRequest</code> visible
     */
    public void initializeCreatorScene() {
        numberPlayerCatcher.setVisible(true);
        setNumberRequest.setVisible(true);
    }

    /**
     * Sets the player connected
     *
     * @param usernames the usernames of the connected players
     */
    public void setPlayerConnected(List<String> usernames) {
        connectedPlayers.clear();
        for (int i = 0; i < usernames.size(); i++) {
            connectedPlayers.setText(i + " - " + usernames.get(i));

            if (usernames.get(i).equals(gui.getController().getMainPlayerUsername())) {
                connectedPlayers.setText(" LEADER");
            }

            connectedPlayers.setText("\n");
        }
    }

    @FXML
    private void setPlayersNumber() {

        try {
            gui.getController().setPlayersNumber(numberPlayerCatcher.getValue());
        } catch (InvalidPlayersNumberException ignored) {
        }

        numberPlayerCatcher.setVisible(false);
        setNumberRequest.setVisible(false);
        requiredPlayer.setText("Player required to play: " + numberPlayerCatcher.getValue());
        requiredPlayer.setVisible(true);

    }

    public double getSceneWindowWidth() {
        return startedGameSceneWidth;
    }

    public double getSceneWindowHeight() {
        return startedGameSceneHeight;
    }


}
