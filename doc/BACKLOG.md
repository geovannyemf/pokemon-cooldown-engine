# 📋 Backlog — Pokémon GO Cooldown Engine

> Versión actual: **v1.0.0**
> Metodología: Sprints iterativos con entregables funcionales al final de cada uno.

---

## Épicas del Proyecto

| ID | Épica | Descripción |
|---|---|---|
| E-01 | Infraestructura | Configuración del cluster Kafka y entorno de desarrollo local |
| E-02 | Productor de Eventos | Simulación del evento de teleport del jugador |
| E-03 | Motor de Cooldown | Cálculo de distancia, lookup de cooldown y lógica central |
| E-04 | Motor de Sugerencias | Ranking de destinos óptimos con scoring compuesto |
| E-05 | API REST | Exposición de funcionalidades vía endpoints HTTP |
| E-06 | Despliegue | Containerización y publicación en entorno cloud |
| E-07 | Integración | Conexión con Pokémon Weather Explorer y otras aplicaciones |

---

## ✅ Sprint 1 — Infraestructura Kafka

**Objetivo:** Tener un cluster Kafka funcionando localmente con los topics del proyecto creados y verificados.

**Estado:** `COMPLETADO ✅`

---

### US-001 · Levantar cluster Kafka en Docker

**Como** desarrollador,
**quiero** un cluster Kafka corriendo en Docker con Zookeeper y una UI de administración,
**para** tener una infraestructura de mensajería local lista para el desarrollo.

**Criterios de aceptación:**
- [ ] `docker compose up -d` levanta 3 contenedores: Zookeeper, Kafka Broker, Kafka UI
- [ ] `docker ps` muestra los 3 contenedores en estado `Up`
- [ ] Kafka UI accesible en `http://localhost:8090`
- [ ] El cluster aparece como `pokemon-local` en la UI

**Archivos creados:**
- `docker/docker-compose.yml`

**Configuración clave:**
- Imagen: `confluentinc/cp-kafka:7.5.0`
- Broker ID: 1
- `auto.create.topics.enable: false` (topics solo por configuración explícita)

---

### US-002 · Crear topics del proyecto

**Como** desarrollador,
**quiero** crear los topics `teleport-events` y `cooldown-results` con la configuración correcta de partitions,
**para** separar el flujo de entrada y salida del motor de cooldown.

**Criterios de aceptación:**
- [ ] Topic `teleport-events` creado con 3 partitions y replication-factor 1
- [ ] Topic `cooldown-results` creado con 3 partitions y replication-factor 1
- [ ] `kafka-topics --list` muestra ambos topics
- [ ] Kafka UI muestra los topics con su configuración correcta

**Decisión técnica:**
- 3 partitions desde el inicio (no 1) para que el diseño escale sin modificar el cluster

---

## ✅ Sprint 2 — Productor Python

**Objetivo:** Un script Python que simule eventos de teleport del jugador y los publique en Kafka con la estructura de mensaje correcta.

**Estado:** `COMPLETADO ✅`

---

### US-101 · Configurar entorno Python

**Como** desarrollador,
**quiero** un entorno virtual Python con las dependencias necesarias para conectarme a Kafka,
**para** aislar las dependencias del proyecto y evitar conflictos con el sistema.

**Criterios de aceptación:**
- [ ] `python3 -m venv venv` crea el entorno virtual
- [ ] `pip install -r requirements.txt` instala sin errores
- [ ] `kafka-python-ng==2.2.3` instalado correctamente (compatible con Python 3.12)

**Archivos creados:**
- `producer/requirements.txt`

**Notas técnicas:**
- `kafka-python==2.0.2` tiene bug de compatibilidad con Python 3.12+ (módulo `six`)
- Se usa el fork `kafka-python-ng` que corrige el problema con API idéntica

---

### US-102 · Definir catálogo de destinos

**Como** jugador de Pokémon GO,
**quiero** un catálogo de destinos globales con sus coordenadas y densidad de spawns/pokestops,
**para** tener una base de datos de hotspots a los que vale la pena teleportarse.

**Criterios de aceptación:**
- [ ] JSON con mínimo 15 destinos globales
- [ ] Cada destino incluye: id, name, country, lat, lon, pokestops_density, spawns_density, notes
- [ ] Destinos distribuidos en distintos continentes y zonas horarias
- [ ] Densidades en escala 0-10

**Archivos creados:**
- `producer/destinations.json`

**Destinos incluidos (v1.0):**
Shibuya Tokyo, Santa Monica LA, Central Park NY, Marina Bay Singapore, Circular Quay Sydney, Reforma CDMX, Copacabana Rio, Millennium Park Chicago, Tsim Sha Tsui HK, Hyde Park London, Champ de Mars Paris, Darling Harbour Sydney, Shinjuku Gyoen Tokyo, Grant Park Chicago, Orchard Road Singapore.

---

### US-103 · Productor de eventos de teleport

**Como** sistema,
**quiero** publicar eventos de teleport en el topic `teleport-events` con la estructura de mensaje correcta,
**para** que el consumer pueda procesarlos y calcular el cooldown correspondiente.

**Criterios de aceptación:**
- [ ] El script conecta con el broker Kafka en `localhost:9092`
- [ ] Presenta menú interactivo con todos los destinos disponibles
- [ ] Opción para enviar un destino específico o todos en batch
- [ ] Cada mensaje incluye: `event_id` (UUID), `player_id`, `timestamp` UTC, `action_type`, `from_location`, `to_location`
- [ ] El `player_id` se usa como **key** del mensaje
- [ ] El log muestra topic, partition y offset de cada mensaje publicado
- [ ] Los mensajes son visibles en Kafka UI con estructura JSON correcta

**Archivos creados:**
- `producer/teleport_producer.py`

**Conceptos Kafka aplicados:**
- `key_serializer`: serializa el `player_id` como key → routing determinístico a partition
- `acks="all"`: espera confirmación del broker antes de continuar
- `record_metadata.partition` y `record_metadata.offset` visibles en el output

---

## ✅ Sprint 3 — Motor de Cooldown (Consumer Core)

**Objetivo:** Un consumer Spring Boot que lea eventos de Kafka, calcule distancia y cooldown, y retorne el resultado.

**Estado:** `COMPLETADO ✅`

---

### US-201 · Configurar proyecto Spring Boot

**Como** desarrollador,
**quiero** un proyecto Spring Boot con las dependencias correctas para conectarme a Kafka como consumer y producer,
**para** implementar el motor de cooldown en Java.

**Criterios de aceptación:**
- [ ] Proyecto generado con Spring Initializr
- [ ] Dependencias: `spring-kafka`, `spring-web`, `lombok`, `jackson-databind`
- [ ] `application.properties` configurado con broker, consumer group, deserializers
- [ ] `auto-offset-reset=earliest` para procesar mensajes previos al arranque
- [ ] La aplicación levanta sin errores en el puerto 8080

**Archivos creados:**
- `consumer/cooldown-engine/pom.xml`
- `consumer/cooldown-engine/src/main/resources/application.properties`
- `consumer/cooldown-engine/src/main/java/com/pokego/cooldownengine/config/JacksonConfig.java`

---

### US-202 · Calcular distancia y cooldown

**Como** sistema,
**quiero** calcular la distancia entre dos coordenadas GPS y determinar el tiempo de cooldown requerido,
**para** informar al jugador cuánto tiempo debe esperar antes de interactuar en el nuevo destino.

**Criterios de aceptación:**
- [ ] Implementar fórmula de Haversine para distancia entre coordenadas GPS
- [ ] Tabla de cooldown oficial cargada en `TreeMap` (lookup O(log n))
- [ ] `floorKey()` para encontrar el rango correcto de distancia
- [ ] Buffer de seguridad de +2 minutos aplicado sobre el cooldown base
- [ ] Status `SAFE` o `COOLDOWN_REQUIRED` calculado correctamente
- [ ] Log claro con destino, distancia, cooldown y status

**Archivos creados:**
- `consumer/.../model/TeleportEvent.java`
- `consumer/.../model/Location.java`
- `consumer/.../model/CooldownResult.java`
- `consumer/.../service/CooldownService.java`

**Conceptos Kafka aplicados:**
- `@KafkaListener`: Spring se suscribe automáticamente y llama al método por cada mensaje
- `ConsumerRecord<String, String>`: acceso a partition, offset y key del mensaje
- Consumer group guarda el offset — al reiniciar, retoma desde donde se quedó

---

### US-203 · Consumer event-driven

**Como** sistema,
**quiero** un listener Kafka que procese cada evento de teleport en tiempo real,
**para** que el cálculo de cooldown ocurra automáticamente sin intervención manual.

**Criterios de aceptación:**
- [ ] `@KafkaListener` suscrito al topic `teleport-events`
- [ ] Deserialización automática de JSON a `TeleportEvent`
- [ ] Manejo de errores con log y continuación (no detiene el consumer)
- [ ] Log de partition y offset de cada mensaje procesado
- [ ] Al reiniciar la app, NO reprocesa mensajes ya leídos por el consumer group

**Archivos creados:**
- `consumer/.../consumer/TeleportEventConsumer.java`

---

## ✅ Sprint 4 — Motor de Sugerencias

**Objetivo:** Dado el destino del teleport actual, calcular un ranking de los mejores próximos destinos posibles y publicar el resultado en Kafka.

**Estado:** `COMPLETADO ✅`

---

### US-301 · Ranking de destinos por score compuesto

**Como** jugador,
**quiero** recibir un Top 5 de destinos óptimos desde mi ubicación actual,
**para** decidir cuál es el mejor próximo teleport considerando distancia, cooldown y densidad de Pokémon.

**Criterios de aceptación:**
- [ ] `destinations.json` cargado al arrancar la aplicación (`@PostConstruct`)
- [ ] Score compuesto calculado: spawns (40%) + pokestops (30%) + cooldown penalty (30%)
- [ ] Destinos ordenados por score descendente
- [ ] Clasificación `EXCELENTE` / `BUENO` / `ACEPTABLE` asignada por score
- [ ] Top N configurable (default: 5)
- [ ] Log formateado con ranking numerado visible en consola

**Archivos creados:**
- `consumer/.../model/DestinationSuggestion.java`
- `consumer/.../model/DestinationsConfig.java`
- `consumer/.../service/SuggestionService.java`

---

### US-302 · Publicar resultados en Kafka

**Como** sistema,
**quiero** publicar el resultado del cálculo (cooldown + sugerencias) en el topic `cooldown-results`,
**para** que otras aplicaciones puedan consumir los resultados de forma desacoplada.

**Criterios de aceptación:**
- [ ] `KafkaTemplate` publica en `cooldown-results` después de cada procesamiento
- [ ] Key del mensaje: `player_id` (mismo criterio que el topic de entrada)
- [ ] El resultado incluye: cooldown calculado + lista de sugerencias rankeadas
- [ ] El mensaje es visible en Kafka UI con estructura JSON correcta
- [ ] Errores de publicación logueados sin interrumpir el flujo principal

**Archivos creados:**
- `consumer/.../producer/CooldownResultProducer.java`

**Conceptos Kafka aplicados:**
- El consumer actúa también como producer → patrón **Kafka Streams lite**
- `CompletableFuture` para publicación asíncrona con callback de error
- Misma key en ambos topics → correlación entre evento de entrada y resultado

---

## ✅ Sprint 5 — REST API

**Objetivo:** Exponer los cálculos del motor vía endpoints HTTP para integración con frontends o aplicaciones externas.

**Estado:** `COMPLETADO ✅`

---

### US-401 · Endpoint de cálculo de cooldown

**Como** desarrollador externo o frontend,
**quiero** un endpoint REST para calcular el cooldown entre dos coordenadas,
**para** integrar el cálculo en cualquier aplicación sin depender del flujo Kafka.

**Criterios de aceptación:**
- [ ] `GET /api/v1/cooldown/calculate?fromLat&fromLon&toLat&toLon`
- [ ] Respuesta incluye: distanceKm, cooldownMinutes, status, fromCoords, toCoords
- [ ] Respuesta en formato JSON con código HTTP 200
- [ ] CORS habilitado para integración con frontend React

---

### US-402 · Endpoint de sugerencias

**Como** desarrollador externo o frontend,
**quiero** un endpoint REST que retorne el ranking de destinos desde una coordenada,
**para** mostrar las mejores opciones de teleport al jugador en tiempo real.

**Criterios de aceptación:**
- [ ] `GET /api/v1/suggestions?lat&lon&top`
- [ ] Parámetro `top` con valor default de 5
- [ ] Respuesta incluye: fromCoords, total, array de suggestions con score y recommendation
- [ ] Respuesta en formato JSON con código HTTP 200

---

### US-403 · Health check

**Como** operador del sistema,
**quiero** un endpoint de health check,
**para** verificar que el servicio está activo y obtener información de versión.

**Criterios de aceptación:**
- [ ] `GET /api/v1/health` retorna `{ status, service, version }`
- [ ] Respuesta HTTP 200 cuando el servicio está activo

**Archivos creados:**
- `consumer/.../controller/CooldownController.java`

---

## 🔲 Sprint 6 — Containerización y Despliegue

**Objetivo:** Dockerizar todos los módulos del proyecto y desplegarlo en un entorno cloud accesible desde el exterior para integración con el Weather Explorer.

**Estado:** `PENDIENTE 🔲`

---

### US-501 · Dockerizar el Producer Python

**Como** desarrollador,
**quiero** un `Dockerfile` para el producer Python,
**para** que pueda ejecutarse en cualquier entorno sin configuración manual.

**Criterios de aceptación:**
- [ ] `Dockerfile` basado en `python:3.12-slim`
- [ ] `KAFKA_BROKER` configurable via variable de entorno
- [ ] Imagen construible con `docker build`
- [ ] Contenedor ejecutable con `docker run`

---

### US-502 · Dockerizar el Consumer Spring Boot

**Como** desarrollador,
**quiero** un `Dockerfile` para el consumer Spring Boot,
**para** que pueda desplegarse como contenedor independiente.

**Criterios de aceptación:**
- [ ] `Dockerfile` multi-stage: build con Maven + runtime con JRE slim
- [ ] Variables de entorno para `KAFKA_BROKER` y `SERVER_PORT`
- [ ] Health check configurado en el Dockerfile
- [ ] Imagen optimizada (< 300 MB)

---

### US-503 · Docker Compose completo del proyecto

**Como** desarrollador,
**quiero** un `docker-compose.yml` unificado que levante todos los servicios del proyecto,
**para** poder reproducir el entorno completo con un solo comando.

**Criterios de aceptación:**
- [ ] Un solo `docker compose up` levanta: Zookeeper, Kafka, Kafka UI, Consumer Spring Boot
- [ ] Topics creados automáticamente al iniciar (script de init)
- [ ] Variables de entorno externalizadas en `.env`
- [ ] Servicios con health checks y depends_on correctos
- [ ] Documentado en README

---

### US-504 · Despliegue en cloud

**Como** desarrollador,
**quiero** desplegar el cooldown-engine en un entorno cloud accesible públicamente,
**para** que el Weather Explorer pueda consumir sus resultados desde cualquier ubicación.

**Criterios de aceptación:**
- [ ] Consumer Spring Boot desplegado y accesible via URL pública
- [ ] Kafka desplegado o sustituido por servicio gestionado (AWS MSK / Confluent Cloud)
- [ ] `GET /api/v1/health` responde desde URL pública
- [ ] Variables sensibles manejadas con secrets del cloud provider

**Opciones de despliegue evaluadas:**
- AWS ECS + MSK
- Azure Container Apps + Event Hubs
- Fly.io + Confluent Cloud (opción más económica para dev)
- Railway (opción más simple para MVP)

---

## 🔲 Sprint 7 — Integración con Weather Explorer

**Objetivo:** Conectar el Cooldown Engine con el Pokémon Weather Explorer para enriquecer el mapa con datos de cooldown en tiempo real.

**Estado:** `PENDIENTE 🔲`

---

### US-601 · Consumer del topic `cooldown-results` en Weather Explorer

**Como** Weather Explorer,
**quiero** consumir los mensajes del topic `cooldown-results`,
**para** mostrar en el mapa el estado de cooldown del jugador y los destinos sugeridos.

**Criterios de aceptación:**
- [ ] Weather Explorer suscrito a `cooldown-results`
- [ ] Marcadores en el mapa actualizados en tiempo real con cooldown status
- [ ] Destinos sugeridos visibles como pins en el mapa Leaflet
- [ ] Indicador visual de tiempo restante de cooldown

---

### US-602 · Endpoint de integración entre aplicaciones

**Como** Weather Explorer,
**quiero** poder consultar el cooldown engine vía REST cuando no haya eventos Kafka activos,
**para** obtener sugerencias bajo demanda sin necesidad de publicar un evento.

**Criterios de aceptación:**
- [ ] Weather Explorer llama a `GET /api/v1/suggestions` con coordenadas actuales del jugador
- [ ] Respuesta integrada en el panel lateral del mapa
- [ ] CORS configurado para el dominio del Weather Explorer
- [ ] Manejo de errores cuando el cooldown engine no está disponible

---

## 📊 Resumen de Progreso

| Sprint | Nombre | US | Estado |
|--------|--------|----|--------|
| Sprint 1 | Infraestructura Kafka | US-001, US-002 | ✅ Completado |
| Sprint 2 | Productor Python | US-101, US-102, US-103 | ✅ Completado |
| Sprint 3 | Motor de Cooldown | US-201, US-202, US-203 | ✅ Completado |
| Sprint 4 | Motor de Sugerencias | US-301, US-302 | ✅ Completado |
| Sprint 5 | REST API | US-401, US-402, US-403 | ✅ Completado |
| Sprint 6 | Containerización y Despliegue | US-501 a US-504 | 🔲 Pendiente |
| Sprint 7 | Integración Weather Explorer | US-601, US-602 | 🔲 Pendiente |

**Total:** 17 User Stories · 5 completadas · 6 en backlog
