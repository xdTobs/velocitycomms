package org.tobs.velocitycomms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.tobs.velocitycomms.shared.GameStartPayload;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

public class Velocitycomms implements ModInitializer {

    private static final Map<String, Formatting> TEAMS = Map.of(
        "red", Formatting.RED,
        "blue", Formatting.BLUE
    );

    private static final Map<String, List<String>> teamRoster = new HashMap<>(Map.of(
        "red", new ArrayList<>(),
        "blue", new ArrayList<>()
    ));

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(GameStartPayload.ID, GameStartPayload.CODEC);
        System.out.println("[LobbyMod] Initialised — ready to send game-start signals.");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(literal("team")
                .then(literal("join")
                    .then(literal("red").executes(ctx -> joinTeam(ctx.getSource().getPlayer(), "red")))
                    .then(literal("blue").executes(ctx -> joinTeam(ctx.getSource().getPlayer(), "blue"))))
                .then(literal("leave")
                    .executes(ctx -> leaveTeam(ctx.getSource().getPlayer()))));

            dispatcher.register(literal("startgame")
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    List<GameStartPayload.Team> payloadTeams = teamRoster.entrySet().stream()
                        .map(e -> {
                            List<String> uuids = e.getValue().stream()
                                .map(playerName -> {
                                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerName);
                                    return p != null ? p.getUuidAsString() : null;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                            return new GameStartPayload.Team(e.getKey(), uuids);
                        })
                        .collect(Collectors.toList());

                    Velocitycomms.sendGameStart(context.getSource().getPlayer(), "gameserver", payloadTeams);
                    context.getSource().sendFeedback(() -> Text.literal("Game started!"), false);
                    return 1;
                }));
        });
    }

    private static int joinTeam(ServerPlayerEntity player, String teamName) {
        String playerName = player.getName().getString();
        leaveAllTeams(playerName);
        teamRoster.get(teamName).add(playerName);
        syncScoreboardTeam(player.getCommandSource().getServer(), teamName);
        Formatting color = TEAMS.get(teamName);
        player.sendMessage(Text.literal("Joined team " + teamName + "!").formatted(color));
        return 1;
    }

    private static int leaveTeam(ServerPlayerEntity player) {
        String playerName = player.getName().getString();
        boolean wasInTeam = leaveAllTeams(playerName);
        if (!wasInTeam) {
            player.sendMessage(Text.literal("You are not in a team."));
            return 0;
        }
        TEAMS.keySet().forEach(t -> syncScoreboardTeam(player.getCommandSource().getServer(), t));
        player.sendMessage(Text.literal("Left your team."));
        return 1;
    }

    private static boolean leaveAllTeams(String playerName) {
        boolean found = false;
        for (List<String> members : teamRoster.values()) {
            if (members.remove(playerName)) found = true;
        }
        return found;
    }

    private static void syncScoreboardTeam(MinecraftServer server, String teamName) {
        ServerScoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            Formatting color = TEAMS.get(teamName);
            team.setColor(color);
            team.setPrefix(Text.literal("[" + teamName + "] ").formatted(color));
        }

        for (String member : new ArrayList<>(team.getPlayerList())) {
            scoreboard.removeScoreHolderFromTeam(member, team);
        }
        for (String member : teamRoster.get(teamName)) {
            scoreboard.addScoreHolderToTeam(member, team);
        }
    }

    public static void sendGameStart(
            ServerPlayerEntity carrier,
            String targetServer,
            List<GameStartPayload.Team> teams
    ) {
        GameStartPayload payload = new GameStartPayload(targetServer, teams);
        ServerPlayNetworking.send(carrier, payload);
        System.out.printf("[LobbyMod] Sent game-start to '%s' with %d team(s).%n",
                targetServer, teams.size());
    }
}
