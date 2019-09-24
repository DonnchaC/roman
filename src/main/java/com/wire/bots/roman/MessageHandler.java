package com.wire.bots.roman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.roman.DAO.BotsDAO;
import com.wire.bots.roman.DAO.ProvidersDAO;
import com.wire.bots.roman.model.IncomingMessage;
import com.wire.bots.roman.model.OutgoingMessage;
import com.wire.bots.roman.model.Provider;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.assets.Picture;
import com.wire.bots.sdk.models.ImageMessage;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.NewBot;
import com.wire.bots.sdk.server.model.SystemMessage;
import com.wire.bots.sdk.tools.Logger;
import org.skife.jdbi.v2.DBI;

import javax.annotation.Nullable;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.wire.bots.roman.Tools.generateToken;

public class MessageHandler extends MessageHandlerBase {

    private final Client jerseyClient;
    private final DBI jdbi;

    MessageHandler(DBI jdbi, Client jerseyClient) {
        this.jerseyClient = jerseyClient;
        this.jdbi = jdbi;
    }

    @Override
    public boolean onNewBot(NewBot newBot) {
        UUID botId = newBot.id;
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.userId = newBot.origin.id;
        message.type = "conversation.bot_request";

        Provider provider = getProvider(botId);
        if (provider != null) {
            send(provider, message, null);
        }

        return true;
    }

    @Override
    public void onNewConversation(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.userId = msg.from;
        message.type = "conversation.init";
        message.text = msg.conversation.name;
        message.token = generateToken(botId);

        try {
            client.sendDirectText(message.token, msg.from);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Provider provider = getProvider(botId);
        if (provider != null) {
            boolean send = send(provider, message, client);
            if (!send)
                Logger.warning("onNewConversation: failed to deliver message to: bot: %s", botId);
        }
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        UUID botId = client.getId();

        Provider provider = getProvider(botId);
        if (provider != null) {

            if (provider.type.equals("slack")) {
                WebTarget target = jerseyClient.target(provider.serviceUrl);
                SlackAdapter adapter = new SlackAdapter(target);
                IncomingMessage response = adapter.send(botId, msg);
                sendResponse(client, response);
            } else {
                OutgoingMessage message = new OutgoingMessage();
                message.botId = botId;
                message.userId = msg.getUserId();
                message.type = "conversation.new_text";
                message.text = msg.getText();
                message.token = generateToken(botId, TimeUnit.SECONDS.toMillis(30));

                boolean send = send(provider, message, client);
                if (!send)
                    Logger.warning("onText: failed to deliver message to: bot: %s", botId);
            }
        }
    }

    @Override
    public void onImage(WireClient client, ImageMessage msg) {
        UUID botId = client.getId();

        try {
            byte[] img = client.downloadAsset(msg.getAssetKey(),
                    msg.getAssetToken(),
                    msg.getSha256(),
                    msg.getOtrKey());

            OutgoingMessage message = new OutgoingMessage();
            message.botId = botId;
            message.userId = msg.getUserId();
            message.type = "conversation.new_image";
            message.image = Base64.getEncoder().encodeToString(img);
            message.token = generateToken(botId);

            Provider provider = getProvider(botId);
            if (provider != null) {
                if (!send(provider, message, client))
                    Logger.warning("onImage: failed to deliver message to: bot: %s", botId);
            }
        } catch (Exception e) {
            Logger.error("onImage: %s %s", botId, e);
        }
    }

    @Override
    public void onMemberJoin(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.type = "conversation.user_joined";
        message.token = generateToken(botId, TimeUnit.SECONDS.toMillis(30));

        Provider provider = getProvider(botId);

        if (provider != null) {
            for (UUID userId : msg.users) {
                message.userId = userId;

                send(provider, message, client);
            }
        }
    }

    private boolean send(Provider provider, OutgoingMessage message, @Nullable WireClient wireClient) {
        trace(message);

        UUID botId = message.botId;

        boolean useWebhook = provider.serviceUrl != null;

        if (useWebhook) {
            Response response = sendWebhook(provider, message);
            if (response.getStatus() == 200 && wireClient != null) {
                IncomingMessage incomingMessage = response.readEntity(IncomingMessage.class);
                sendResponse(wireClient, incomingMessage);
            }

            return response.getStatus() == 200;
        } else {
            return WebSocket.send(provider.id, message, botId);
        }
    }

    private void sendResponse(WireClient wireClient, IncomingMessage incomingMessage) {
        try {
            if (!incomingMessage.isValidImage() || incomingMessage.isValidText()) {

            }
            //todo validate   incomingMessage
            switch (incomingMessage.type) {
                case "text": {
                    wireClient.sendText(incomingMessage.text);
                }
                break;
                case "image": {
                    Picture picture = new Picture(Base64.getDecoder().decode(incomingMessage.image));
                    wireClient.sendPicture(picture.getImageData(), picture.getMimeType());
                }
                break;
            }
        } catch (Exception e) {

        }
    }

    private Provider getProvider(UUID botId) {
        UUID providerId = jdbi.onDemand(BotsDAO.class).getProviderId(botId);
        Provider provider = jdbi.onDemand(ProvidersDAO.class).get(providerId);

        if (provider == null) {
            Logger.error("MessageHandler.send: provider == null. providerId: %s, bot: %s",
                    providerId, botId);
        }
        return provider;
    }

    private Response sendWebhook(Provider provider, Object message) {
        String serviceAuth = provider.serviceAuth;
        String serviceUrl = provider.serviceUrl;

        return jerseyClient.target(serviceUrl)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + serviceAuth)
                .post(Entity.entity(message, MediaType.APPLICATION_JSON));
    }

    private void trace(Object message) {
        try {
            if (Logger.getLevel() == Level.FINE) {
                ObjectMapper objectMapper = new ObjectMapper();
                Logger.debug(objectMapper.writeValueAsString(message));
            }
        } catch (Exception ignore) {

        }
    }
}