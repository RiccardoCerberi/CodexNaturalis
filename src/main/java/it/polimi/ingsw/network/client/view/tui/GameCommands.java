package it.polimi.ingsw.network.client.view.tui;

public enum GameCommands {
    COLOR("choose color"),
    DRAW("draw a card"),
    HELP("show this message"),
    LOBBYSIZE("set size of the lobby"),
    OBJECTIVE("choose your objective"),
    PLACE("place a card in your playground"),
    STARTER("place your starter"),
    PM("write a private message"),
    M("write a public message"),
    QUIT("quit the game safely");

    private final String description;

    GameCommands(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
