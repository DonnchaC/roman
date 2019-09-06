# Wire Bot API Proxy
Uses [lithium](https://github.com/wireapp/lithium) to utilize Wire Bot API

### API documentation:
https://services.zinfra.io/proxy/swagger

### Register as Wire Bot Developer
 - [register](https://services.zinfra.io/proxy/swagger#!/default/register)

### Login
 - [login](https://services.zinfra.io/proxy/swagger#!/default/login)

### Create a service
 - [create service](https://services.zinfra.io/proxy/swagger#!/default/create)

```
{
  "name": "My Cool Bot",
  "url": "https://my.server.com/webhook",
  "avatar": "..." // Base64 encoded image 
}
```

Only `name` is mandatory. Specify `url` if you want to use your _Webhook_ to receive events from Wire Backend.
Leave `url` _null_ if you prefer _Websocket_. `avatar` for your bot is optional and it is `Base64` encoded `jpeg`|`png` image. If
`avatar` filed is left _null_ default avatar is assigned for the Service.

After creating your Service the following json is returned:
```
{
  "email": "dejan@wire.com",
  "company": "ACME",
  "service": "ACME Integration",
  "service_code": "8d935243-828f-45d8-b52e-cdc1385334fc:d8371f5e-cd41-4528-a2bb-f3feefea160f",
  "service_authentication": "g_ZiEfOnMdVnbbpyKIWCVZIk",
  "app_key": "..."
}
```

Go to your Team Settings page and navigate to _Services_ tab. Add this `service_code` and enable this service for your team.
Now your team members should be able to see your _Service_ when they open _people picker_ and navigate to _services_ tab.

### Webhook
In case `url` was specified when creating the service webhook will be used. All requests coming from Wire to your
Service's endpoint will have HTTP Header `Authorization` with value:
 `Bearer <service_authentication>`. Make sure you verify this value in your webhook implementation.
Wire will send events as `POST` HTTP request to the `url` you specified when creating the Service.
Your webhook should always return HTTP code `200` as the result

### Websocket
In order to receive events via _Websocket_ connect to:

```
wss://services.zinfra.io/proxy/await/`<app_key>`
```

### Events that are sent as HTTP `POST` to your endpoint (Webhook or Websocket)

- `bot_request`: When bot is added to a conversation ( 1-1 conversation or a group)
```
{
    "type": "conversation.bot_request",
    "botId": "493ede3e-3b8c-4093-b850-3c2be8a87a95",
    "userId": "4dfc5c70-dcc8-4d9e-82be-a3cbe6661107"
}
```

Your service must be available at the moment `bot_request` event is sent. It must respond with http code `200`.
 In case of Websocket implementation it is enough the socket is connected to the Proxy at that moment.

- `init`: If your Service responded with `200` to a `bot_request` another event is sent. `text` field contains the name
of the conversation your bot is being added
```
{
    "type": "conversation.init",
    "botId": "216efc31-d483-4bd6-aec7-4adc2da50ca5",
    "userId": "4dfc5c70-dcc8-4d9e-82be-a3cbe6661107",
    "token": "...",
    "text": "Bot Example Conversation"
}
```

- `new_text`: When text is posted in a conversation where this bot is present
```
{
    "type": "conversation.new_text",
    "botId": "216efc31-d483-4bd6-aec7-4adc2da50ca5",
    "userId": "4dfc5c70-dcc8-4d9e-82be-a3cbe6661107",
    "text": "Hi everybody!",
    "token": "..." // token
}
```
- `new_image`: When an image is posted in a conversation where this bot is present

```
{
    "type": "conversation.new_image",
    "botId": "216efc31-d483-4bd6-aec7-4adc2da50ca5",
    "userId": "4dfc5c70-dcc8-4d9e-82be-a3cbe6661107",
    "token": "...",
    "image": "..." // Base64 encoded image
}
```

If the event contains `token` field this `token` can be used to respond to this event by sending `Outgoing Message` like:

### Posting back to Wire conversation
In order to post text or an image as a bot into Wire conversation you need to send a `POST` request to `/conversation`
You must also specify the HTTP header as `Authorization: <token>` where `token` was obtained in `init` or other events
 like: `new_text` or `new_image` ...

Example:
```
POST https://services.zinfra.io/proxy/conversation -d '{"type": "text", "text": "Hello!"}' \
-H'Authorization:eyJhbGciOiJIUzM4NCJ9.eyJpc3MiOiJodHRwczovL3dpcmUuY29tIiwic3ViIjoiMjE2ZWZjMzEtZDQ4My00YmQ2LWFlYzctNGFkYzJkYTUwY2E1In0.h1iGvhzCcbSea_Hoi5oIcIgr_GyPjcKUGUXXD_AXWVKTMIml9e3UIbec2jf2gETK'
```

_Outgoing Message_ can be of 2 types:
- **Text message**
```
{
    "type": "text",
    "text": "Hello!"
}
```

- **Image message**
```
{
    "type": "image",
    "image": "..." // Base64 encoded image
}
```
Full description: https://services.zinfra.io/proxy/swagger#!/default/post

**Note:** `token` that comes with `conversation.init` events is _lifelong_. It should be stored for later usage. `token`
 that comes with other event types has lifespan of 20 seconds.

### Bot Example
 - Echo bot in Java: https://github.com/dkovacevic/demo-proxy