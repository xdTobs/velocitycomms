package org.tobs.velocitycomms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.tobs.velocitycomms.shared.GameStartPayload;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class Velocitycomms implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(GameStartPayload.ID, GameStartPayload.CODEC);

        System.out.println("[LobbyMod] Initialised — ready to send game-start signals.");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("startgame")
                .executes(context -> {
                    List<GameStartPayload.Team> teams = List.of(
                            new GameStartPayload.Team("Red",  List.of(
                                    context.getSource().getPlayer().getUuidAsString()
                            )),
                            new GameStartPayload.Team("Blue", List.of())
                    );
                    Velocitycomms.sendGameStart(context.getSource().getPlayer(), "gameserver", teams);
                    context.getSource().sendFeedback(() -> Text.literal("Game started!"), false);
                    return 1;
                })));
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
