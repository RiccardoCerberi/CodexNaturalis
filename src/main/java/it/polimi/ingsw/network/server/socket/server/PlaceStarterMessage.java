package it.polimi.ingsw.network.server.socket.server;

import it.polimi.ingsw.model.card.Side;

public class PlaceStarterMessage extends ServerMessage {
    private Side side;
    public PlaceStarterMessage(String sender, Side side) {
        super(sender, ServerType.PLACE_STARTER);
        this.side = side;
    }

    public Side getSide() {
        return side;
    }
}
