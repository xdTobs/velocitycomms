package org.tobs.velocitycomms.shared;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record GameStartPayload(String targetServer, List<Team> teams) implements CustomPayload {

    public static final CustomPayload.Id<GameStartPayload> ID =
            new CustomPayload.Id<>(Identifier.of("velocitycomms", "game_start"));

    public static final PacketCodec<PacketByteBuf, GameStartPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeString(payload.targetServer());
                buf.writeVarInt(payload.teams().size());
                for (Team team : payload.teams()) {
                    buf.writeString(team.name());
                    buf.writeVarInt(team.playerUuids().size());
                    for (String uuid : team.playerUuids()) {
                        buf.writeString(uuid);
                    }
                }
            },
            buf -> {
                String targetServer = buf.readString();
                int teamCount = buf.readVarInt();
                List<Team> teams = new ArrayList<>(teamCount);
                for (int i = 0; i < teamCount; i++) {
                    String teamName = buf.readString();
                    int playerCount = buf.readVarInt();
                    List<String> uuids = new ArrayList<>(playerCount);
                    for (int j = 0; j < playerCount; j++) {
                        uuids.add(buf.readString());
                    }
                    teams.add(new Team(teamName, uuids));
                }
                return new GameStartPayload(targetServer, teams);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record Team(String name, List<String> playerUuids) {}
}