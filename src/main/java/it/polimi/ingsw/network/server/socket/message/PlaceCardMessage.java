package it.polimi.ingsw.network.server.socket.message;

import it.polimi.ingsw.model.board.Position;
import it.polimi.ingsw.model.card.Side;
import it.polimi.ingsw.network.NetworkMessage;
import it.polimi.ingsw.network.Type;

public class PlaceCardMessage extends NetworkMessage {
    private int frontId;
    private int backId;
    private Side side;
    private Position position;

    public PlaceCardMessage(String sender, int frontId, int backId, Side side, Position position) {
        super(Type.PLACE_CARD, sender);
        this.frontId = frontId;
        this.backId = backId;
        this.side = side;
        this.position = position;
    }

    public int getFrontId() {
        return frontId;
    }

    public int getBackId() {
        return backId;
    }

    public Side getSide() {
        return side;
    }

    public Position getPosition() {
        return position;
    }
}
