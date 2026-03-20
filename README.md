# 🎮 Pokémon GO Cooldown Engine

> Motor de cooldown personal inspirado en PGSharp/PokeNav para optimizar la experiencia de spoofing en Pokémon GO. Plataforma event-driven construida sobre Apache Kafka, Java Spring Boot y Python.

---

## 📋 Tabla de Contenidos

- [Contexto del Proyecto](#contexto-del-proyecto)
- [Reglas de Negocio](#reglas-de-negocio)
- [Arquitectura](#arquitectura)
- [Stack Tecnológico](#stack-tecnológico)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Requisitos Previos](#requisitos-previos)
- [Instalación y Ejecución](#instalación-y-ejecución)
- [API REST](#api-rest)
- [Topics de Kafka](#topics-de-kafka)
- [Conceptos Kafka Aplicados](#conceptos-kafka-aplicados)

---

## Contexto del Proyecto

Cuando un jugador de Pokémon GO se teletransporta a una ubicación lejana para atrapar un Pokémon, el juego impone un periodo de espera obligatorio antes de permitir interacciones en otra ubicación. Este periodo se llama **cooldown** y puede durar hasta 2 horas dependiendo de la distancia recorrida.

Violar el cooldown provoca un **soft ban**: los Pokémon escapan, los PokéStops no entregan ítems, y las raids fallan. En casos repetidos, la cuenta puede ser suspendida permanentemente.

Este proyecto resuelve ese problema con un motor event-driven que:

1. Recibe el evento de teleport del jugador vía Apache Kafka
2. Calcula la distancia exacta usando la fórmula de Haversine
3. Determina el tiempo de cooldown obligatorio según la tabla oficial
4. Genera un ranking de los mejores destinos posibles desde la ubicación actual



---

## Reglas de Negocio

### Tabla de Cooldown Oficial

Fuente: PGSharp (referencia oficial de la comunidad).

| Distancia | Cooldown | Distancia | Cooldown |
|-----------|----------|-----------|----------|
| 1 km      | 1 min    | 125 km    | 33 min   |
| 2 km      | 1 min    | 150 km    | 36 min   |
| 4 km      | 2 min    | 180 km    | 39 min   |
| 10 km     | 8 min    | 200 km    | 42 min   |
| 15 km     | 11 min   | 300 km    | 50 min   |
| 25 km     | 15 min   | 500 km    | 64 min   |
| 30 km     | 18 min   | 600 km    | 72 min   |
| 40 km     | 22 min   | 700 km    | 80 min   |
| 45 km     | 23 min   | 800 km    | 86 min   |
| 60 km     | 25 min   | 1,000 km  | 100 min  |
| 80 km     | 27 min   | 1,250 km  | 118 min  |
| 100 km    | 30 min   | 1,266+ km | 120 min  |

**Cooldown máximo:** 120 minutos (2 horas) para cualquier distancia igual o mayor a 1,266 km.

### Buffer de Seguridad

El sistema aplica un buffer de **+2 minutos** sobre el cooldown calculado. Esto cubre el hecho de que los segundos también cuentan (si la acción ocurrió a las 13:00:55, el cooldown de 2 horas termina a las 15:00:55, no a las 15:00:00).

### Acciones que ACTIVAN el cooldown

- Atrapar un Pokémon salvaje (incluye Incienso, Módulos de Señuelo, Caja Misteriosa)
- Soltar una Poké Ball accidentalmente en la pantalla de captura
- Dar una baya a un Pokémon salvaje o jefe de incursión
- Girar un PokéStop (incluso si la bolsa está llena)
- Colocar un Pokémon en un Gimnasio
- Dar bayas a un defensor de Gimnasio dentro del radar
- Batallar en un Gimnasio
- Que un Pokémon escape por límite de capturas

### Acciones que NO activan el cooldown

- Teletransportarse a una nueva ubicación
- Encontrarse con un Pokémon salvaje sin atraparlo
- Dar bayas a un defensor de Gimnasio remotamente
- Eclosionar huevos
- Reclamar recompensas de tareas
- Incursiones de velocidad (si se completó el cooldown previo)
- Intercambiar y abrir regalos
- Evolucionar o potenciar Pokémon
- Batallas JcJ

### Algoritmo de Ranking de Destinos

El score compuesto de cada destino se calcula con la siguiente fórmula:

```
score = (spawns_density × 0.40) + (pokestops_density × 0.30) + (cooldown_score × 0.30)
cooldown_score = (1 - cooldown_minutes / 122) × 3.0
```

Clasificación por score:
- `EXCELENTE`: score >= 7.5
- `BUENO`: score >= 6.0
- `ACEPTABLE`: score < 6.0

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                  POKEMON GO COOLDOWN ENGINE                  │
└─────────────────────────────────────────────────────────────┘

  ┌──────────────┐    topic: teleport-events    ┌─────────────────────┐
  │   PRODUCER   │  ──────────────────────────► │     CONSUMER        │
  │   (Python)   │   key: player_id             │  (Spring Boot)      │
  │              │   partitions: 3              │                     │
  │  Simula el   │                              │  1. Haversine dist  │
  │  evento de   │  { event_id,                 │  2. Cooldown lookup │
  │  teleport    │    player_id,                │  3. Suggestion rank │
  │  del jugador │    from_location,            │  4. Publish result  │
  └──────────────┘    to_location,              └─────────────────────┘
                       timestamp,                         │
                       action_type }          topic: cooldown-results
                                                          │
                                              ┌───────────▼──────────┐
                                              │      REST API         │
                                              │   puerto :8080        │
                                              │                       │
                                              │ GET /cooldown/calc    │
                                              │ GET /suggestions      │
                                              │ GET /health           │
                                              └──────────────────────┘
```

### Decisiones de Arquitectura

| Decisión | Elección | Razón |
|---|---|---|
| Serialización | JSON (String) | Simplicidad inicial; Avro en versión futura |
| Partitions por topic | 3 | Preparado para paralelismo sin reestructurar el cluster |
| Message Key | `player_id` | Garantiza orden de eventos por jugador en la misma partition |
| Consumer Group | `cooldown-engine-group` | Permite múltiples instancias con balanceo automático |
| Estado en memoria | `HashMap` / `TreeMap` | Sprint 1; Redis/DB en versión futura |
| Cooldown lookup | `TreeMap.floorKey()` | O(log n), búsqueda por rango sin iterar la tabla |

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Message Broker | Apache Kafka | 7.5.0 (Confluent) |
| Coordinación | Apache Zookeeper | 7.5.0 (Confluent) |
| Infraestructura | Docker + Docker Compose | - |
| Monitoreo | Kafka UI (Provectus) | latest |
| Producer | Python + kafka-python-ng | 3.12 / 2.2.3 |
| Consumer | Java + Spring Boot | 21 / 3.2.x |
| Framework Kafka Java | Spring for Apache Kafka | 3.x |
| Serialización | Jackson (JSON) | 2.x |
| Utilidades | Lombok | 1.18.x |
| API | Spring Web (Tomcat) | 3.x |

---

## Estructura del Proyecto

```
pokemon-cooldown-engine/
├── docker/
│   └── docker-compose.yml          # Kafka + Zookeeper + Kafka UI
│
├── producer/                       # Módulo Python
│   ├── venv/                       # Entorno virtual (no commitear)
│   ├── requirements.txt
│   ├── destinations.json           # 15 hotspots globales de Pokémon GO
│   └── teleport_producer.py        # Productor de eventos de teleport
│
└── consumer/                       # Módulo Java
    └── cooldown-engine/
        ├── pom.xml
        └── src/main/
            ├── java/com/pokego/cooldownengine/
            │   ├── config/
            │   │   └── JacksonConfig.java
            │   ├── consumer/
            │   │   └── TeleportEventConsumer.java  # @KafkaListener
            │   ├── producer/
            │   │   └── CooldownResultProducer.java # KafkaTemplate
            │   ├── service/
            │   │   ├── CooldownService.java        # Haversine + lookup
            │   │   └── SuggestionService.java      # Ranking de destinos
            │   ├── model/
            │   │   ├── TeleportEvent.java
            │   │   ├── Location.java
            │   │   ├── CooldownResult.java
            │   │   ├── DestinationSuggestion.java
            │   │   └── DestinationsConfig.java
            │   └── controller/
            │       └── CooldownController.java     # REST API
            └── resources/
                ├── application.properties
                └── destinations.json
```

---

## Requisitos Previos

- Docker Desktop instalado y corriendo
- Java 17 o 21
- Python 3.10+
- Maven (incluido via `./mvnw`)

---

## Instalación y Ejecución

### 1. Levantar la infraestructura Kafka

```bash
cd docker/
docker compose up -d

# Verificar contenedores
docker ps

# Crear topics (solo la primera vez)
docker exec poke-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic teleport-events --partitions 3 --replication-factor 1
docker exec poke-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic cooldown-results --partitions 3 --replication-factor 1

# Verificar topics
docker exec poke-kafka kafka-topics --list --bootstrap-server localhost:9092
```

Kafka UI disponible en: http://localhost:8090

### 2. Levantar el Consumer (Spring Boot)

```bash
cd consumer/cooldown-engine/
./mvnw spring-boot:run
```

API disponible en: http://localhost:8080

### 3. Ejecutar el Producer (Python)

```bash
cd producer/
python3 -m venv venv
source venv/bin/activate          # Linux/WSL
# venv\Scripts\activate           # Windows CMD

pip install -r requirements.txt
python3 teleport_producer.py
```

---

## API REST

### `GET /api/v1/health`
Verifica que el servicio esté activo.

```json
{
  "status": "UP",
  "service": "cooldown-engine",
  "version": "1.0.0"
}
```

### `GET /api/v1/cooldown/calculate`
Calcula distancia y cooldown entre dos coordenadas GPS.

**Parámetros:**
- `fromLat`, `fromLon` — coordenadas de origen
- `toLat`, `toLon` — coordenadas de destino

**Ejemplo:**
```
GET /api/v1/cooldown/calculate?fromLat=19.4326&fromLon=-99.1332&toLat=35.6595&toLon=139.7004
```

```json
{
  "distanceKm": 11307.19,
  "cooldownMinutes": 120,
  "status": "COOLDOWN_REQUIRED",
  "fromCoords": { "lat": 19.4326, "lon": -99.1332 },
  "toCoords":   { "lat": 35.6595, "lon": 139.7004 }
}
```

### `GET /api/v1/suggestions`
Devuelve el ranking de mejores destinos desde una coordenada.

**Parámetros:**
- `lat`, `lon` — coordenadas actuales del jugador
- `top` — cantidad de resultados (default: 5)

**Ejemplo:**
```
GET /api/v1/suggestions?lat=35.6595&lon=139.7004&top=5
```

```json
{
  "fromCoords": { "lat": 35.6595, "lon": 139.7004 },
  "total": 5,
  "suggestions": [
    {
      "destinationId": "dest_001",
      "destinationName": "Shibuya, Tokyo",
      "country": "Japan",
      "distanceKm": 0.0,
      "cooldownMinutes": 0,
      "spawnsDensity": 9.0,
      "pokestopsDensity": 9.5,
      "score": 9.45,
      "recommendation": "EXCELENTE"
    }
  ]
}
```

---

## Topics de Kafka

### `teleport-events`
Publicado por: Producer Python
Consumido por: Consumer Spring Boot

```json
{
  "event_id": "uuid-v4",
  "player_id": "player_geovanny_001",
  "timestamp": "2026-03-19T18:40:24.271594+00:00",
  "action_type": "TELEPORT",
  "from_location": {
    "lat": 19.4326,
    "lon": -99.1332,
    "name": "Ciudad de México"
  },
  "to_location": {
    "id": "dest_001",
    "lat": 35.6595,
    "lon": 139.7004,
    "name": "Shibuya, Tokyo",
    "country": "Japan"
  }
}
```

### `cooldown-results`
Publicado por: Consumer Spring Boot (después de procesar)
Consumido por: Futuras integraciones (Weather Explorer, frontend React)

```json
{
  "eventId": "uuid-v4",
  "playerId": "player_geovanny_001",
  "fromLocation": "Ciudad de México",
  "toLocation": "Shibuya, Tokyo",
  "distanceKm": 11307.19,
  "cooldownMinutes": 122,
  "processedAt": "2026-03-19T18:40:24Z",
  "status": "COOLDOWN_REQUIRED",
  "suggestions": [...]
}
```

---

## Conceptos Kafka Aplicados

| Concepto | Dónde se usa |
|---|---|
| **Topic** | `teleport-events` y `cooldown-results` separan las responsabilidades del pipeline |
| **Partition** | 3 partitions por topic — preparado para paralelismo sin modificar el cluster |
| **Message Key** | `player_id` como key garantiza que todos los eventos de un jugador van a la misma partition → orden garantizado |
| **Offset** | Número incremental por partition; el consumer group guarda su posición para reanudar sin perder mensajes |
| **Consumer Group** | `cooldown-engine-group` — permite múltiples instancias con rebalanceo automático de partitions |
| **auto-offset-reset=earliest** | Al arrancar por primera vez, procesa mensajes desde el más antiguo disponible |
| **acks=all** | El producer espera confirmación del broker antes de continuar — garantiza durabilidad |
| **KafkaTemplate** | Spring abstraction para publicar en Kafka desde el consumer (patrón request-reply via topics) |

---

## Próximos Pasos

- [ ] Dockerizar Producer Python y Consumer Spring Boot
- [ ] Desplegar en entorno cloud (AWS ECS / Azure Container Apps)
- [ ] Integración con Pokémon Weather Explorer via topic `cooldown-results`
- [ ] Persistencia de resultados en PostgreSQL
- [ ] Dead Letter Topic para manejo de mensajes con error
- [ ] Schema Registry + Apache Avro para serialización tipada
- [ ] Métricas con Micrometer + Prometheus + Grafana
