package it.polimi.ingsw.network.server.socket.client.clientmessage;

public enum ClientType {
    UPDATE_CREATOR,
    UPDATE_AFTER_LOBBY_CRASH,
    UPDATE_AFTER_CONNECTION,
    SHOW_UPDATE_PLAYERS_IN_LOBBY,
    SHOW_UPDATE_PLAYER_STATUS,
    SHOW_UPDATE_COLOR,
    SHOW_UPDATE_OBJECTIVE_CARD,
    SHOW_UPDATE_AFTER_PLACE,
    SHOW_UPDATE_AFTER_DRAW,
    SHOW_UPDATE_CHAT,
    SHOW_UPDATE_CURRENT_PLAYER,
    SHOW_UPDATE_SUSPENDED_GAME,
    SHOW_WINNERS,
    ERROR
}