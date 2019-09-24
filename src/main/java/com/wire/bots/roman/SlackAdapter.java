package com.wire.bots.roman;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wire.bots.roman.model.IncomingMessage;
import com.wire.bots.sdk.models.TextMessage;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

public class SlackAdapter {

    private final WebTarget target;

    public SlackAdapter(WebTarget target) {
        this.target = target;
    }

    public IncomingMessage send(UUID botId, TextMessage message) {
        _InteractiveRequest request = createReq(message, botId);

        SlackResponse response = send(request);

        IncomingMessage ret = new IncomingMessage();
        ret.type = "text";
        ret.text = response.text;
        return ret;
    }

    private _InteractiveRequest createReq(TextMessage message, UUID botId) {
        _InteractiveRequest request = new _InteractiveRequest();
        return request;
    }

    private SlackResponse send(_InteractiveRequest request) {
        Response post = target
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(request, MediaType.APPLICATION_JSON));
        return post.readEntity(SlackResponse.class);
    }

    static class _Message {
        @JsonProperty("type")
        public String type;

        @JsonProperty("user")
        public String user;

        @JsonProperty("ts")
        public String ts;

        @JsonProperty("text")
        public String text;
    }

    public static class SlackResponse {
        @JsonProperty("username")
        public String username;

        @JsonProperty("icon_emoji")
        public String iconEmoji;

        @JsonProperty("channel")
        public String channel;

        @JsonProperty("text")
        public String text;

        @JsonProperty("response_type")
        public String responseType;

        @JsonProperty("type")
        public String type;

        @JsonProperty("sub_type")
        public String subType;

        @JsonProperty("ts")
        public String ts;

        @JsonProperty("thread_ts")
        public String threadTs;

        @JsonProperty("replace_original")
        public Boolean replaceOriginal;

        @JsonProperty("delete_original")
        public Boolean deleteOriginal;
    }

    static class _SlackRequest {
        public String teamId;
        public String teamDomain;
        public String channelId;
        public String channelName;
        public String userId;
        public String userName;
        public String command;
        public String text;
        public String responseUrl;
    }

    static class _InteractiveRequest {
        @JsonProperty("token")
        public String token;

        @JsonProperty("callback_id")
        public String callbackId;

        @JsonProperty("type")
        public String type;

        @JsonProperty("trigger_id")
        public String triggerId;

        @JsonProperty("response_url")
        public String responseUrl;

        //@JsonProperty("team")
        //public Team team;

        //@JsonProperty("channel")
        //public Channel channel;

        @JsonProperty("user")
        public _User user;

        @JsonProperty("message")
        public _Message message;

        //@JsonProperty("actions")
        //public List<Action> actions;

        @JsonProperty("action_ts")
        public String actionTs;

        @JsonProperty("message_ts")
        public String messageTs;

        @JsonProperty("attachment_id")
        public String attachmentId;

        @JsonProperty("originalMessage")
        public SlackResponse originalMessage;
    }

    static class _User {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;
    }
}