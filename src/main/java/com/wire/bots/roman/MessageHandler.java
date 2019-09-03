package com.wire.bots.roman;

import com.wire.bots.roman.DAO.BotsDAO;
import com.wire.bots.roman.model.ExternalService;
import com.wire.bots.roman.model.OutgoingMessage;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.models.ImageMessage;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.NewBot;
import com.wire.bots.sdk.server.model.SystemMessage;
import com.wire.bots.sdk.tools.Logger;
import org.skife.jdbi.v2.DBI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.wire.bots.roman.Tools.generateToken;

public class MessageHandler extends MessageHandlerBase {
    private final BotsDAO botsDAO;

    private final Client jerseyClient;

    MessageHandler(DBI jdbi, Client jerseyClient) {
        this.botsDAO = jdbi.onDemand(BotsDAO.class);
        this.jerseyClient = jerseyClient;
    }

    @Override
    public boolean onNewBot(NewBot newBot) {
        UUID botId = newBot.id;
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.userId = newBot.origin.id;
        message.type = "conversation.bot_request";

        return send(message);
    }

    @Override
    public void onNewConversation(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.userId = msg.from;
        message.type = "conversation.init";
        message.token = generateToken(botId);

        send(message);
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        UUID botId = client.getId();
        OutgoingMessage message = new OutgoingMessage();
        message.botId = botId;
        message.userId = msg.getUserId();
        message.type = "conversation.new_text";
        message.text = msg.getText();
        message.token = generateToken(botId, TimeUnit.SECONDS.toMillis(30));

        send(message);
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

            send(message);
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

        for (UUID userId : msg.users) {
            message.userId = userId;

            send(message);
        }
    }

    private boolean send(OutgoingMessage message) {
        ExternalService externalService = botsDAO.get(message.botId);

        Response post = jerseyClient.target(externalService.url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + externalService.auth)
                .post(Entity.entity(message, MediaType.APPLICATION_JSON));

        Logger.info("%s: %s code: %d", message.type, message.botId, post.getStatus());
        return post.getStatus() == 200;
    }
}