package me.azzimov.ortuscapture.testbot;

import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HeadlessTestBot {
    private HeadlessTestBot() {
    }

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("ortus.bot.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("ortus.bot.port", "25565"));
        String name = System.getProperty("ortus.bot.name", "OrtusBot");
        boolean logChat = Boolean.parseBoolean(System.getProperty("ortus.bot.logChat", "true"));
        String autoCommandsRaw = System.getProperty("ortus.bot.autoCommands", "");
        String[] autoCommands = Arrays.stream(autoCommandsRaw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        MinecraftProtocol protocol = new MinecraftProtocol(name);
        ClientSession session = ClientNetworkSessionFactory.factory()
                .setAddress(host, port)
                .setProtocol(protocol)
                .create();

        CountDownLatch disconnectLatch = new CountDownLatch(1);
        AtomicBoolean inGame = new AtomicBoolean(false);
        AtomicBoolean autoSent = new AtomicBoolean(false);

        session.addListener(new SessionAdapter() {
            @Override
            public void connected(org.geysermc.mcprotocollib.network.event.session.ConnectedEvent event) {
                    log("Connected to " + host + ":" + port);
            }

            @Override
            public void packetReceived(Session s, org.geysermc.mcprotocollib.network.packet.Packet packet) {
                if (packet instanceof ClientboundLoginFinishedPacket loginFinished) {
                    log("Login finished. Profile: " + loginFinished.getProfile().getName());
                }

                if (packet instanceof ClientboundLoginPacket && inGame.compareAndSet(false, true)) {
                    log("Bot joined the world. Type commands in this console (example: /oc info).");
                    if (autoCommands.length > 0 && autoSent.compareAndSet(false, true)) {
                        for (String command : autoCommands) {
                            sendCommand(session, command);
                            sleep(350L);
                        }
                    }
                }

                if (logChat && packet instanceof ClientboundSystemChatPacket chatPacket) {
                    log("[CHAT] " + chatPacket.getContent());
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                log("Disconnected: " + event.getReason());
                if (event.getCause() != null) {
                    log("Cause: " + event.getCause().getClass().getSimpleName() + ": " + event.getCause().getMessage());
                    String msg = String.valueOf(event.getCause().getMessage());
                    if (msg.contains("Connection refused")) {
                        log("Hint: no server is listening on " + host + ":" + port + ". Start Paper first.");
                    }
                }
                disconnectLatch.countDown();
            }
        });

        log("Starting HEADLESS test bot (no game window): " + name + " -> " + host + ":" + port);
        log("If using non-premium bot on local tests, set server.properties: online-mode=false");
        log("Use online-mode=false only on local/private test servers.");

        session.connect(false);

        Thread consoleThread = new Thread(() -> readConsoleLoop(session), "ortus-testbot-console");
        consoleThread.setDaemon(true);
        consoleThread.start();

        disconnectLatch.await();
    }

    private static void readConsoleLoop(ClientSession session) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String input = line.trim();
                if (input.isEmpty()) {
                    continue;
                }

                String normalized = input.toLowerCase(Locale.ROOT);
                if (normalized.equals("exit") || normalized.equals("quit")) {
                    session.disconnect("Test bot closed from console");
                    return;
                }

                if (!session.isConnected()) {
                    log("Session already closed.");
                    return;
                }

                sendCommand(session, input);
            }
        } catch (Exception ex) {
            log("Console input error: " + ex.getMessage());
        }
    }

    private static void sendCommand(ClientSession session, String rawInput) {
        String command = rawInput.startsWith("/") ? rawInput.substring(1) : rawInput;
        if (command.isBlank()) {
            return;
        }
        session.send(new ServerboundChatCommandPacket(command));
        log("[CMD] /" + command);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println("[OrtusTestBot] " + message);
    }
}
