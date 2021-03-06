package com.wire.bots.roman.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.roman.model.IncomingMessage;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.assets.Picture;
import com.wire.bots.sdk.exceptions.MissingStateException;
import com.wire.bots.sdk.server.model.ErrorMessage;
import com.wire.bots.sdk.tools.Logger;
import io.jsonwebtoken.JwtException;
import io.swagger.annotations.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

import static com.wire.bots.roman.Tools.validateToken;

@Api
@Path("/conversation")
@Produces(MediaType.APPLICATION_JSON)
public class ConversationResource {
    private final ClientRepo repo;

    public ConversationResource(ClientRepo repo) {
        this.repo = repo;
    }

    @POST
    @ApiOperation(value = "Post message on Wire", authorizations = {@Authorization(value = "Bearer")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Not authenticated"),
            @ApiResponse(code = 409, message = "Unknown bot. This bot might be deleted by the user")
    })
    public Response post(@ApiParam @NotNull @HeaderParam("Authorization") String token,
                         @ApiParam @NotNull @Valid IncomingMessage message) {
        try {
            trace(message);

            String subject = validateToken(token);
            UUID botId = UUID.fromString(subject);

            Logger.info("ConversationResource: `%s` bot: %s", message.type, botId);

            return send(message, botId);
        } catch (JwtException e) {
            Logger.warning("ConversationResource %s", e);
            return Response.
                    ok(new ErrorMessage("Invalid Authorization token")).
                    status(403).
                    build();
        } catch (Exception e) {
            Logger.error("ConversationResource: %s", e);
            e.printStackTrace();
            return Response
                    .ok(new ErrorMessage(e.getMessage()))
                    .status(500)
                    .build();
        }
    }

    private Response send(IncomingMessage message, UUID botId) throws Exception {
        try (WireClient client = repo.getClient(botId)) {
            switch (message.type) {
                case "text": {
                    client.sendText(message.text);
                }
                break;
                case "image": {
                    Picture picture = new Picture(Base64.getDecoder().decode(message.image));
                    client.sendPicture(picture.getImageData(), picture.getMimeType());
                }
                break;
                default:
                    return Response.
                            ok(new ErrorMessage("Unknown message type: " + message.type)).
                            status(400).
                            build();
            }

            return Response.
                    ok().
                    build();
        } catch (MissingStateException e) {
            Logger.info("ConversationResource bot: %s err: %s", botId, e);
            return Response.
                    ok(new ErrorMessage("Unknown bot. This bot might be deleted by the user")).
                    status(409).
                    build();
        }
    }

    private void trace(IncomingMessage message) {
        try {
            if (Logger.getLevel() == Level.FINE) {
                ObjectMapper objectMapper = new ObjectMapper();
                Logger.debug(objectMapper.writeValueAsString(message));
            }
        } catch (Exception ignore) {

        }
    }
}