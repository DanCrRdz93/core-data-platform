# Guía de Integración WebSocket — Configuración del Servidor

Esta guía documenta las tecnologías y frameworks que pueden usarse como servidor WebSocket para conectarse con los módulos `:network-ws-core` y `:network-ws-ktor` del SDK Core Data Platform.

---

## Tabla de Contenidos

- [Protocolo WebSocket (RFC 6455)](#protocolo-websocket-rfc-6455)
- [Requisitos del Servidor](#requisitos-del-servidor)
- [Cómo se Conecta el SDK al Servidor](#cómo-se-conecta-el-sdk-al-servidor)
- [Tecnologías de Servidor por Ecosistema](#tecnologías-de-servidor-por-ecosistema)
  - [Ktor Server (Kotlin)](#1-ktor-server-kotlin)
  - [Spring Boot (Kotlin / Java)](#2-spring-boot-kotlin--java)
  - [Node.js](#3-nodejs)
  - [Go](#4-go)
  - [Python (FastAPI)](#5-python-fastapi)
  - [ASP.NET Core (C#)](#6-aspnet-core-c)
- [Servicios Cloud Administrados](#servicios-cloud-administrados)
- [Patrones de Comunicación](#patrones-de-comunicación)
- [Seguridad](#seguridad)
- [Compatibilidad con el SDK](#compatibilidad-con-el-sdk)

---

## Protocolo WebSocket (RFC 6455)

WebSocket es un protocolo de comunicación **full-duplex** sobre una conexión TCP persistente. A diferencia de HTTP (request → response), WebSocket permite que **ambas partes** (cliente y servidor) envíen mensajes en cualquier momento sin esperar una solicitud previa.

### ¿Cuándo usar WebSocket en lugar de HTTP?

| Escenario | HTTP | WebSocket |
|---|---|---|
| Consultar datos una vez | ✅ Ideal | ❌ Sobremata |
| Actualizaciones en tiempo real (precios, chat, notificaciones) | ❌ Polling ineficiente | ✅ Ideal |
| Comunicación bidireccional continua | ❌ No soportado | ✅ Nativo |
| Baja latencia entre mensajes | ❌ Overhead de headers por request | ✅ Sin overhead una vez conectado |
| Datos enviados por el servidor sin solicitud del cliente (push) | ❌ Requiere SSE o polling | ✅ Nativo |

### Ciclo de Vida de una Conexión WebSocket

```
┌──────────┐                              ┌──────────┐
│  Cliente  │                              │ Servidor │
│ (tu app)  │                              │(backend) │
└─────┬─────┘                              └─────┬────┘
      │                                          │
      │  1. HTTP GET /ws/prices                  │
      │     Connection: Upgrade                  │
      │     Upgrade: websocket                   │
      │     Sec-WebSocket-Key: dGhlIH...         │
      │     Sec-WebSocket-Protocol: json         │
      ├─────────────────────────────────────────►│
      │                                          │
      │  2. HTTP 101 Switching Protocols         │
      │     Upgrade: websocket                   │
      │     Sec-WebSocket-Accept: s3pPL...       │
      │◄─────────────────────────────────────────┤
      │                                          │
      │  ═══════ Conexión WebSocket abierta ═══  │
      │                                          │
      │  3. Frame Text: {"subscribe":"BTC-USD"}  │
      ├─────────────────────────────────────────►│
      │                                          │
      │  4. Frame Text: {"price":67432.50}       │
      │◄─────────────────────────────────────────┤
      │                                          │
      │  5. Frame Text: {"price":67435.00}       │
      │◄─────────────────────────────────────────┤
      │                                          │
      │  ... (comunicación bidireccional) ...     │
      │                                          │
      │  6. Frame Close (code=1000)              │
      ├─────────────────────────────────────────►│
      │                                          │
      │  7. Frame Close (code=1000)              │
      │◄─────────────────────────────────────────┤
      │                                          │
      │  ═══════ Conexión TCP cerrada ═══════    │
```

**Pasos clave:**

1. **Handshake HTTP** — El cliente envía un request HTTP GET con headers de Upgrade. Aquí es donde se envían headers de autenticación (Bearer token, API key, cookies).
2. **101 Switching Protocols** — El servidor acepta y la conexión se "eleva" de HTTP a WebSocket.
3. **Intercambio de frames** — Ambas partes envían mensajes libremente. Los frames pueden ser `Text` (UTF-8), `Binary` (bytes crudos), `Ping/Pong` (heartbeat), o `Close` (cierre).
4. **Cierre** — Cualquier parte puede iniciar el cierre con un frame Close. El código 1000 significa cierre limpio.

### Tipos de Frame

| Frame | Descripción | Quién lo envía |
|---|---|---|
| **Text** | Mensaje UTF-8 (normalmente JSON) | Cliente y/o servidor |
| **Binary** | Bytes crudos (protobuf, imágenes, audio) | Cliente y/o servidor |
| **Ping** | Heartbeat — "¿sigues vivo?" | Normalmente el servidor |
| **Pong** | Respuesta a Ping | Automático (el framework lo maneja) |
| **Close** | Solicitud de cierre con código + razón | Cualquier parte |

> **Nota:** El SDK solo expone frames `Text`, `Binary` y `Close` al consumidor. Los frames `Ping/Pong` son manejados automáticamente por Ktor a nivel de transporte — nunca llegan al código de la app.

---

## Requisitos del Servidor

El servidor **no necesita ninguna tecnología específica**. Solo debe cumplir:

| Requisito | Detalle |
|---|---|
| **Protocolo** | WebSocket estándar (RFC 6455) |
| **Handshake** | Aceptar HTTP Upgrade → 101 Switching Protocols |
| **Frames** | Enviar/recibir frames Text y/o Binary |
| **Ping/Pong** | Responder Pong a Ping (la mayoría de frameworks lo hace automáticamente) |
| **TLS** | HTTPS/WSS recomendado (el SDK enforce WSS por defecto) |

**Lo que el servidor NO necesita:**

- No necesita saber que el cliente usa Ktor, OkHttp, o Darwin
- No necesita ninguna librería específica del SDK
- No necesita headers especiales del SDK
- No necesita formato de mensaje específico — el SDK es agnóstico de serialización

---

## Cómo se Conecta el SDK al Servidor

```kotlin
// 1. Configuración — URL del servidor + políticas
val config = WebSocketConfig(
    url = "wss://api.tu-empresa.com",          // ← URL de TU servidor
    pingInterval = 30.seconds,                  // SDK envía Ping cada 30s
    connectTimeout = 15.seconds,                // Timeout del handshake
    reconnectPolicy = ReconnectPolicy.ExponentialBackoff(
        maxAttempts = 10,                       // Reintentar hasta 10 veces
        initialDelay = 1.seconds,               // Primer reintento después de 1s
        maxDelay = 30.seconds                   // Máximo 30s entre reintentos
    )
)

// 2. Engine — Ktor se encarga del transporte
val engine = KtorWebSocketEngine.create(config)

// 3. Executor — agrega reconexión, clasificación de errores, observabilidad
val executor = DefaultSafeWebSocketExecutor(
    engine = engine,
    config = config,
    classifier = KtorWebSocketErrorClassifier(),
    interceptors = listOf(authInterceptor),      // Inyecta Bearer token
    observers = listOf(loggingObserver)           // Logs de ciclo de vida
)

// 4. Conectar a un endpoint específico del servidor
val connection = executor.connect(
    WebSocketRequest(path = "/ws/prices/BTC-USD")
)
// → El SDK hace: wss://api.tu-empresa.com/ws/prices/BTC-USD

// 5. Recibir mensajes del servidor
connection.incoming.collect { frame ->
    when (frame) {
        is WebSocketFrame.Text -> println("Servidor dice: ${frame.text}")
        is WebSocketFrame.Binary -> println("Datos binarios: ${frame.data.size} bytes")
        is WebSocketFrame.Close -> println("Servidor cerró: ${frame.code}")
    }
}
```

---

## Tecnologías de Servidor por Ecosistema

### 1. Ktor Server (Kotlin)

**Ideal si tu backend ya está en Kotlin.** Misma librería Ktor que usa el SDK en el cliente.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
}
```

```kotlin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PriceTick(val symbol: String, val price: Double, val timestamp: Long)

fun main() {
    embeddedServer(Netty, port = 8080) {

        // Instalar el plugin de WebSockets en el servidor
        install(WebSockets) {
            pingPeriodMillis = 30_000      // Enviar Ping cada 30 segundos
            timeoutMillis = 15_000         // Cerrar si no recibe Pong en 15s
            maxFrameSize = 1_048_576       // Máximo 1 MB por frame
        }

        routing {
            // Endpoint: wss://tu-servidor.com/ws/prices/{symbol}
            webSocket("/ws/prices/{symbol}") {
                val symbol = call.parameters["symbol"] ?: return@webSocket close(
                    CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Symbol required")
                )

                println("Cliente conectado para $symbol")

                try {
                    // Enviar precios cada segundo
                    while (true) {
                        val tick = PriceTick(
                            symbol = symbol,
                            price = 67000.0 + (Math.random() * 1000),
                            timestamp = System.currentTimeMillis()
                        )
                        send(Frame.Text(Json.encodeToString(tick)))
                        delay(1000)
                    }
                } catch (e: Exception) {
                    println("Cliente desconectado: ${e.message}")
                }
            }

            // Endpoint de chat bidireccional
            webSocket("/ws/chat/{room}") {
                val room = call.parameters["room"]!!

                // Escuchar mensajes del cliente y responder
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val clientMessage = frame.readText()
                            // Procesar y responder
                            send(Frame.Text("""{"echo":"$clientMessage","room":"$room"}"""))
                        }
                        is Frame.Close -> {
                            println("Cliente cerró conexión en sala $room")
                        }
                        else -> { /* Ignorar Ping/Pong/Binary */ }
                    }
                }
            }
        }
    }.start(wait = true)
}
```

**¿Por qué Ktor Server?**
- Mismo lenguaje (Kotlin) que el SDK y la app
- API de coroutines nativa — `delay()`, `for (frame in incoming)`, `send()`
- Ligero — no necesita Tomcat ni servlet container
- Se despliega en Docker, AWS Lambda, GCP Cloud Run

---

### 2. Spring Boot (Kotlin / Java)

**Ideal si tu organización ya usa Spring.** El framework enterprise más popular en JVM.

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
}
```

```kotlin
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.*
import org.springframework.web.socket.handler.TextWebSocketHandler

@Configuration
@EnableWebSocket
class WebSocketConfig : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Registrar endpoint: wss://tu-servidor.com/ws/prices
        registry.addHandler(PriceWebSocketHandler(), "/ws/prices")
            .setAllowedOrigins("*") // Configurar CORS según necesidad
    }
}

class PriceWebSocketHandler : TextWebSocketHandler() {

    // Se llama cuando un cliente se conecta
    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("Cliente conectado: ${session.id}")

        // Enviar mensaje de bienvenida
        session.sendMessage(TextMessage("""{"status":"connected"}"""))

        // En producción, registrar la sesión para enviarle updates
    }

    // Se llama cuando el cliente envía un mensaje
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        println("Mensaje recibido: $payload")

        // Procesar el mensaje y responder
        val response = """{"echo":"$payload","timestamp":${System.currentTimeMillis()}}"""
        session.sendMessage(TextMessage(response))
    }

    // Se llama cuando el cliente se desconecta
    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: org.springframework.web.socket.CloseStatus
    ) {
        println("Cliente desconectado: ${session.id}, código: ${status.code}")
    }
}
```

**Con STOMP (protocolo de mensajería sobre WebSocket):**

```kotlin
@Configuration
@EnableWebSocketMessageBroker
class StompConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")  // Prefijo para suscripciones
        config.setApplicationDestinationPrefixes("/app")  // Prefijo para envíos
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
    }
}

@Controller
class PriceController {
    @MessageMapping("/subscribe")
    @SendTo("/topic/prices")
    fun subscribe(request: SubscribeRequest): PriceTick {
        return PriceTick(symbol = request.symbol, price = 67432.50)
    }
}
```

> **Nota sobre STOMP:** Si tu servidor usa STOMP, el SDK se conecta al mismo endpoint WebSocket. Los mensajes STOMP son frames Text con formato especial. Tu `StreamingDataSource` necesitaría deserializar el formato STOMP manualmente o usar una librería STOMP del lado del cliente.

**¿Por qué Spring Boot?**
- Ecosistema enterprise maduro con soporte de autenticación (Spring Security)
- Integración con message brokers (RabbitMQ, Kafka) para escalar
- STOMP para patrones pub/sub complejos
- Amplia documentación y comunidad

---

### 3. Node.js

**Ideal para servidores ligeros y rápidos de prototipar.**

#### Con `ws` (librería pura, sin framework)

```bash
npm install ws
```

```javascript
const { WebSocketServer } = require('ws');

// Crear servidor WebSocket en puerto 8080
const wss = new WebSocketServer({
    port: 8080,
    path: '/ws/prices'     // Endpoint que el SDK conectará
});

wss.on('connection', (ws, req) => {
    console.log(`Cliente conectado desde ${req.socket.remoteAddress}`);

    // Verificar autenticación desde headers del handshake
    const token = req.headers['authorization'];
    if (!token || !isValidToken(token)) {
        ws.close(4001, 'Unauthorized');
        return;
    }

    // Enviar precios cada segundo
    const interval = setInterval(() => {
        if (ws.readyState === ws.OPEN) {
            ws.send(JSON.stringify({
                symbol: 'BTC-USD',
                price: 67000 + Math.random() * 1000,
                timestamp: Date.now()
            }));
        }
    }, 1000);

    // Escuchar mensajes del cliente
    ws.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        console.log('Cliente dice:', msg);

        // Responder
        ws.send(JSON.stringify({ echo: msg, server: 'node' }));
    });

    // Limpiar cuando el cliente se desconecta
    ws.on('close', (code, reason) => {
        console.log(`Cliente desconectado: ${code} ${reason}`);
        clearInterval(interval);
    });

    ws.on('error', (err) => {
        console.error('Error WebSocket:', err);
        clearInterval(interval);
    });
});

function isValidToken(token) {
    // Validar JWT o API key
    return token.startsWith('Bearer ');
}

console.log('WebSocket server en ws://localhost:8080/ws/prices');
```

#### Con Express + `express-ws`

```javascript
const express = require('express');
const expressWs = require('express-ws');

const app = express();
expressWs(app);

app.ws('/ws/prices/:symbol', (ws, req) => {
    const { symbol } = req.params;
    console.log(`Suscripción a ${symbol}`);

    const interval = setInterval(() => {
        ws.send(JSON.stringify({ symbol, price: Math.random() * 100000 }));
    }, 1000);

    ws.on('close', () => clearInterval(interval));
});

app.listen(8080);
```

**¿Por qué Node.js?**
- Extremadamente rápido para prototipar
- Manejo nativo de I/O asíncrono — ideal para muchas conexiones simultáneas
- Ecosistema npm con miles de librerías
- Fácil de desplegar en cualquier cloud

---

### 4. Go

**Ideal para alta concurrencia y baja latencia.**

```go
// go get nhooyr.io/websocket

package main

import (
    "context"
    "encoding/json"
    "log"
    "net/http"
    "time"

    "nhooyr.io/websocket"
)

type PriceTick struct {
    Symbol    string  `json:"symbol"`
    Price     float64 `json:"price"`
    Timestamp int64   `json:"timestamp"`
}

func main() {
    http.HandleFunc("/ws/prices", handlePrices)
    log.Println("WebSocket server en :8080")
    log.Fatal(http.ListenAndServe(":8080", nil))
}

func handlePrices(w http.ResponseWriter, r *http.Request) {
    // Aceptar la conexión WebSocket (handshake)
    conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
        OriginPatterns: []string{"*"},
    })
    if err != nil {
        log.Printf("Error aceptando WebSocket: %v", err)
        return
    }
    defer conn.Close(websocket.StatusNormalClosure, "")

    ctx := r.Context()
    log.Println("Cliente conectado")

    // Enviar precios cada segundo
    ticker := time.NewTicker(1 * time.Second)
    defer ticker.Stop()

    for {
        select {
        case <-ctx.Done():
            log.Println("Cliente desconectado")
            return
        case <-ticker.C:
            tick := PriceTick{
                Symbol:    "BTC-USD",
                Price:     67000.0 + float64(time.Now().UnixNano()%1000),
                Timestamp: time.Now().UnixMilli(),
            }
            data, _ := json.Marshal(tick)
            err := conn.Write(ctx, websocket.MessageText, data)
            if err != nil {
                log.Printf("Error enviando: %v", err)
                return
            }
        }
    }
}
```

**¿Por qué Go?**
- Goroutines manejan miles de conexiones simultáneas con mínimo overhead
- Compilación a binario estático — deploy simple sin runtime
- Excelente para microservicios de alta concurrencia
- Latencia extremadamente baja

---

### 5. Python (FastAPI)

**Ideal para equipos de data science o backends existentes en Python.**

```bash
pip install fastapi uvicorn websockets
```

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import asyncio
import json
import random
import time

app = FastAPI()

@app.websocket("/ws/prices/{symbol}")
async def websocket_prices(websocket: WebSocket, symbol: str):
    """
    Endpoint WebSocket que envía precios en tiempo real.
    El SDK se conecta a: wss://tu-servidor.com/ws/prices/BTC-USD
    """
    # Aceptar la conexión (completa el handshake)
    await websocket.accept()
    print(f"Cliente conectado para {symbol}")

    try:
        while True:
            # Enviar precio cada segundo
            tick = {
                "symbol": symbol,
                "price": round(67000 + random.random() * 1000, 2),
                "timestamp": int(time.time() * 1000)
            }
            await websocket.send_json(tick)
            await asyncio.sleep(1)

    except WebSocketDisconnect as e:
        print(f"Cliente desconectado: código {e.code}")


@app.websocket("/ws/chat")
async def websocket_chat(websocket: WebSocket):
    """
    Endpoint bidireccional: el cliente envía y recibe mensajes.
    """
    await websocket.accept()

    try:
        while True:
            # Esperar mensaje del cliente
            data = await websocket.receive_text()
            msg = json.loads(data)

            # Procesar y responder
            response = {"echo": msg, "server": "python", "ts": int(time.time())}
            await websocket.send_json(response)

    except WebSocketDisconnect:
        print("Chat: cliente desconectado")
```

```bash
# Ejecutar
uvicorn main:app --host 0.0.0.0 --port 8080

# Con TLS (producción)
uvicorn main:app --host 0.0.0.0 --port 443 --ssl-keyfile key.pem --ssl-certfile cert.pem
```

**¿Por qué Python/FastAPI?**
- Sintaxis simple y rápida de prototipar
- `async/await` nativo para WebSockets
- Documentación automática (Swagger/OpenAPI)
- Ideal si el backend ya tiene lógica en Python (ML, data pipelines)

---

### 6. ASP.NET Core (C#)

**Ideal para organizaciones con stack Microsoft.**

```csharp
// Program.cs
var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

app.UseWebSockets(new WebSocketOptions {
    KeepAliveInterval = TimeSpan.FromSeconds(30)  // Ping automático
});

app.Map("/ws/prices/{symbol}", async (HttpContext context, string symbol) => {
    if (!context.WebSockets.IsWebSocketRequest) {
        context.Response.StatusCode = 400;
        return;
    }

    using var ws = await context.WebSockets.AcceptWebSocketAsync();
    Console.WriteLine($"Cliente conectado para {symbol}");

    var buffer = new byte[1024];
    var timer = new PeriodicTimer(TimeSpan.FromSeconds(1));

    try {
        while (await timer.WaitForNextTickAsync()) {
            var tick = new {
                symbol,
                price = 67000.0 + Random.Shared.NextDouble() * 1000,
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            var json = System.Text.Json.JsonSerializer.Serialize(tick);
            var bytes = System.Text.Encoding.UTF8.GetBytes(json);

            await ws.SendAsync(
                bytes,
                System.Net.WebSockets.WebSocketMessageType.Text,
                endOfMessage: true,
                CancellationToken.None
            );
        }
    }
    catch (System.Net.WebSockets.WebSocketException) {
        Console.WriteLine("Cliente desconectado");
    }
});

app.Run("https://0.0.0.0:8080");
```

**¿Por qué ASP.NET Core?**
- WebSocket support nativo sin librerías extra
- Alto rendimiento (comparable a Go)
- Integración con Azure SignalR para escalar
- Ideal para organizaciones con stack .NET

---

## Servicios Cloud Administrados

Si no quieres administrar tu propio servidor WebSocket:

| Servicio | Descripción | Cuándo usarlo |
|---|---|---|
| **AWS API Gateway WebSocket** | WebSocket serverless. Lambda procesa cada mensaje. Escala automáticamente | Backend serverless en AWS |
| **Azure SignalR Service** | WebSocket administrado con fallback automático (long polling, SSE) | Stack Microsoft / Azure |
| **Google Cloud Pub/Sub + Cloud Run** | Cloud Run soporta WebSocket nativo. Pub/Sub para fan-out | Stack Google Cloud |
| **Cloudflare Durable Objects** | WebSocket en el edge — latencia ultra-baja globalmente | Apps globales con baja latencia |
| **Ably** | Plataforma de real-time messaging. SDK para servidor y cliente | Cuando no quieres administrar nada |
| **Pusher** | Canales pub/sub administrados sobre WebSocket | Chat, notificaciones, dashboards |
| **PubNub** | Mensajería real-time global con presencia y persistencia | IoT, gaming, chat masivo |
| **Firebase Realtime Database** | Sincronización en tiempo real (protocolo propietario, no WebSocket puro) | Apps Firebase existentes — requiere SDK propio |

> **Nota:** Los servicios administrados usan WebSocket estándar en su mayoría, por lo que el SDK es compatible directamente. Excepciones: Firebase y algunos servicios propietarios usan protocolos custom que requieren sus propios SDKs de cliente.

---

## Patrones de Comunicación

### 1. Push unidireccional (servidor → cliente)

El servidor envía datos continuamente. El cliente solo escucha.

```
Servidor                          Cliente (SDK)
   ├── {"price": 67432}  ──────►  incoming.collect { ... }
   ├── {"price": 67435}  ──────►  incoming.collect { ... }
   ├── {"price": 67440}  ──────►  incoming.collect { ... }
   └── ...
```

**Casos de uso:** Precios en tiempo real, dashboards, feeds de noticias.

### 2. Request-Response sobre WebSocket

El cliente envía un mensaje, el servidor responde.

```
Cliente (SDK)                     Servidor
   sendText(subscribe) ──────►  Procesar
                        ◄──────  {"status":"ok"}
   sendText(query)     ──────►  Procesar
                        ◄──────  {"result": [...]}
```

**Casos de uso:** Queries en tiempo real, comandos remotos.

### 3. Bidireccional (chat, colaboración)

Ambas partes envían mensajes libremente.

```
Cliente A ──► Servidor ──► Cliente B
Cliente B ──► Servidor ──► Cliente A
```

**Casos de uso:** Chat, documentos colaborativos, gaming multijugador.

### 4. Pub/Sub (suscripciones)

El cliente se suscribe a canales. El servidor publica en esos canales.

```
Cliente: sendText({"subscribe": "BTC-USD"})
Cliente: sendText({"subscribe": "ETH-USD"})

Servidor: {"channel":"BTC-USD", "price":67432}
Servidor: {"channel":"ETH-USD", "price":3521}
Servidor: {"channel":"BTC-USD", "price":67440}
```

**Casos de uso:** Streaming de mercados, notificaciones por tema, IoT.

---

## Seguridad

### Autenticación en el Handshake

El SDK envía headers durante el handshake HTTP (antes de que la conexión sea WebSocket):

```kotlin
// En el SDK — interceptor de autenticación
val authInterceptor = WebSocketInterceptor { request ->
    val token = credentialProvider.current()?.accessToken ?: ""
    request.copy(
        headers = request.headers + ("Authorization" to "Bearer $token")
    )
}
```

El servidor valida el token durante el handshake:

```kotlin
// Ktor Server
webSocket("/ws/prices") {
    val token = call.request.headers["Authorization"]
    if (!isValidToken(token)) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
    }
    // ... conexión autenticada
}
```

### TLS (WSS)

El SDK **rechaza conexiones no cifradas por defecto** (`ws://`). Solo acepta `wss://` (WebSocket sobre TLS). Configura TLS en tu servidor:

- **Producción:** Usa un certificado TLS válido (Let's Encrypt, certificado corporativo)
- **Desarrollo:** Usa `allowInsecureConnections = true` en `WebSocketConfig` (solo localhost)

### Certificate Pinning

El SDK soporta certificate pinning vía `TrustPolicy` de `:security-core`:

```kotlin
val engine = KtorWebSocketEngine.create(config, trustPolicy)
```

- **Android:** `CertificatePinner` de OkHttp
- **iOS:** `SecTrust` evaluation en `handleChallenge`

---

## Compatibilidad con el SDK

### ¿Qué formato de mensaje espera el SDK?

**Ninguno específico.** El SDK entrega frames crudos (`WebSocketFrame.Text` o `WebSocketFrame.Binary`). La deserialización es responsabilidad del consumidor:

```kotlin
class PriceDataSource(executor: SafeWebSocketExecutor) : StreamingDataSource(executor) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observePrices(symbol: String): Flow<PriceTick> = observe(
        request = WebSocketRequest(path = "/ws/prices/$symbol"),
        deserialize = { frame ->
            when (frame) {
                is WebSocketFrame.Text -> json.decodeFromString<PriceTick>(frame.text)
                else -> null  // Ignorar frames binarios
            }
        }
    )
}
```

### ¿Qué pasa si el servidor se cae?

El SDK reconecta automáticamente según la `ReconnectPolicy`:

| Escenario | Comportamiento del SDK |
|---|---|
| Servidor reinicia | Reconexión con backoff exponencial |
| Red se cae | Reconexión cuando la red vuelve |
| Servidor envía Close(1000) | **No reconecta** (cierre intencional) |
| Servidor envía Close(1001) — going away | **No reconecta** |
| Servidor envía Close(1011) — error interno | **Reconecta** (isRetryable = true) |
| Timeout de Ping/Pong | **Reconecta** |

### ¿Qué headers envía el SDK?

Durante el handshake HTTP, el SDK envía:

```
GET /ws/prices/BTC-USD HTTP/1.1
Host: api.tu-empresa.com
Connection: Upgrade
Upgrade: websocket
Sec-WebSocket-Key: <generado por Ktor>
Sec-WebSocket-Version: 13
Sec-WebSocket-Protocol: <si se especificaron protocols en WebSocketRequest>
Authorization: Bearer <token>           ← si usas WebSocketInterceptor
X-Custom-Header: <valor>                ← cualquier header custom
```

Los headers `Connection`, `Upgrade`, `Sec-WebSocket-Key` y `Sec-WebSocket-Version` son automáticos (RFC 6455). Los headers de `WebSocketRequest.headers` y `WebSocketConfig.defaultHeaders` se agregan encima.
