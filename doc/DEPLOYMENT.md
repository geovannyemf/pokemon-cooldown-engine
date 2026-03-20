# 🚀 Guía de Despliegue — Pokémon GO Cooldown Engine

> Pasos para pasar del entorno local al despliegue cloud y la integración con el Weather Explorer.

---

## Estado Actual (v1.0.0 — Local)

```
[✅] Kafka en Docker (local)
[✅] Producer Python (local)
[✅] Consumer Spring Boot (local, puerto 8080)
[✅] REST API funcional
[✅] Publicación en GitHub
[🔲] Containerización completa
[🔲] Despliegue cloud
[🔲] Integración con Weather Explorer
```

---

## Fase 1 — Subir a GitHub

### Paso 1 · `.gitignore`

Crea un `.gitignore` en la raíz del proyecto:

```gitignore
# Python
producer/venv/
producer/__pycache__/
producer/*.pyc

# Java / Maven
consumer/cooldown-engine/target/
consumer/cooldown-engine/.mvn/wrapper/maven-wrapper.jar
*.class

# IDE
.idea/
.vscode/
*.iml

# Docker volumes
docker/data/

# Secrets
.env
*.env.local
```

### Paso 2 · Inicializar repositorio

```bash
cd /mnt/c/Workspace/Python/pokemon-cooldown-engine

git init
git add .
git commit -m "feat: Pokemon GO Cooldown Engine v1.0.0

- Kafka cluster (Zookeeper + Broker + UI) en Docker
- Producer Python con 15 destinos globales
- Consumer Spring Boot con fórmula Haversine
- Motor de sugerencias con scoring compuesto
- REST API con 3 endpoints
- Pipeline completo: teleport-events -> cooldown-results"
```

### Paso 3 · Conectar con GitHub

```bash
git remote add origin https://github.com/tu-usuario/pokemon-cooldown-engine.git
git branch -M main
git push -u origin main
```

---

## Fase 2 — Containerización

### Dockerfile — Producer Python

Crea `producer/Dockerfile`:

```dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV KAFKA_BROKER=kafka:29092
ENV PLAYER_ID=player_001

CMD ["python3", "teleport_producer.py"]
```

### Dockerfile — Consumer Spring Boot

Crea `consumer/cooldown-engine/Dockerfile`:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV KAFKA_BROKER=kafka:29092
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Actualizar `application.properties` para soportar variables de entorno

```properties
spring.kafka.bootstrap-servers=${KAFKA_BROKER:localhost:9092}
server.port=${SERVER_PORT:8080}
```

### Docker Compose unificado

Crea `docker/docker-compose.full.yml`:

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: poke-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - kafka-net

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: poke-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'
    networks:
      - kafka-net

  kafka-init:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - kafka
    entrypoint: ["/bin/sh", "-c"]
    command: |
      "
      echo 'Esperando Kafka...'
      sleep 10
      kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --topic teleport-events --partitions 3 --replication-factor 1
      kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --topic cooldown-results --partitions 3 --replication-factor 1
      echo 'Topics creados.'
      "
    networks:
      - kafka-net

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: poke-kafka-ui
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: pokemon-local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    networks:
      - kafka-net

  cooldown-engine:
    build:
      context: ../consumer/cooldown-engine
      dockerfile: Dockerfile
    container_name: poke-cooldown-engine
    depends_on:
      - kafka-init
    ports:
      - "8080:8080"
    environment:
      KAFKA_BROKER: kafka:29092
      SERVER_PORT: 8080
    networks:
      - kafka-net

networks:
  kafka-net:
    driver: bridge
```

---

## Fase 3 — Opciones de Despliegue Cloud

### Opción A — Railway (Recomendado para MVP)

Railway es la opción más rápida para tener el proyecto en producción. Soporta Docker y tiene plan gratuito.

**Servicios a desplegar:**
1. Confluent Cloud (Kafka gestionado, plan gratuito disponible)
2. Railway — Consumer Spring Boot

**Pasos:**

1. Crear cuenta en [Confluent Cloud](https://confluent.cloud) y crear un cluster básico
2. Obtener las credenciales: `bootstrap.servers`, `sasl.username`, `sasl.password`
3. Actualizar `application.properties` para Confluent Cloud:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BROKER}
spring.kafka.properties.security.protocol=SASL_SSL
spring.kafka.properties.sasl.mechanism=PLAIN
spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_API_KEY}" password="${KAFKA_API_SECRET}";
```

4. Crear cuenta en [Railway](https://railway.app)
5. Conectar el repositorio de GitHub
6. Configurar variables de entorno en Railway:
   - `KAFKA_BROKER`
   - `KAFKA_API_KEY`
   - `KAFKA_API_SECRET`
7. Deploy automático desde `main`

**URL pública:** Railway asigna automáticamente un dominio `*.railway.app`

---

### Opción B — AWS ECS + MSK

Para un despliegue más cercano al mundo empresarial.

**Arquitectura:**
```
ECR (imagen Docker) → ECS Fargate (cooldown-engine) → MSK (Kafka gestionado)
                                    ↓
                          Application Load Balancer
                                    ↓
                              URL pública
```

**Pasos resumidos:**
1. Crear cluster MSK en AWS con configuración básica
2. Construir y pushear imagen a ECR: `docker build + docker push`
3. Crear task definition en ECS Fargate
4. Configurar ALB con target group apuntando al puerto 8080
5. Configurar security groups para permitir tráfico entre ECS y MSK

---

### Opción C — Azure Container Apps

Integración natural si ya tienes recursos en Azure.

**Pasos resumidos:**
1. Crear Azure Event Hubs (compatible con protocolo Kafka)
2. Construir imagen y pushear a Azure Container Registry
3. Crear Container App con la imagen
4. Configurar ingress externo en puerto 8080
5. Variables de entorno con Azure Key Vault

---

## Fase 4 — Integración con Weather Explorer

Una vez el Cooldown Engine esté desplegado con URL pública:

### Desde el Weather Explorer (React + Vite)

Agrega la URL del Cooldown Engine en las variables de entorno del Weather Explorer:

```env
VITE_COOLDOWN_ENGINE_URL=https://tu-cooldown-engine.railway.app
```

### Llamada desde el frontend

```typescript
// services/cooldownService.ts

const COOLDOWN_API = import.meta.env.VITE_COOLDOWN_ENGINE_URL;

export async function getSuggestions(lat: number, lon: number, top = 5) {
  const response = await fetch(
    `${COOLDOWN_API}/api/v1/suggestions?lat=${lat}&lon=${lon}&top=${top}`
  );
  return response.json();
}

export async function calculateCooldown(
  fromLat: number, fromLon: number,
  toLat: number,   toLon: number
) {
  const response = await fetch(
    `${COOLDOWN_API}/api/v1/cooldown/calculate?fromLat=${fromLat}&fromLon=${fromLon}&toLat=${toLat}&toLon=${toLon}`
  );
  return response.json();
}
```

### Integración con el mapa Leaflet

```typescript
// Al hacer click en un pin de ciudad en el mapa:
const handleCityClick = async (city: City) => {
  const suggestions = await getSuggestions(city.lat, city.lon);
  // Renderizar sugerencias en el panel lateral
  setSuggestions(suggestions.suggestions);
};
```

---

## Checklist de Despliegue

```
Fase 1 — GitHub
[ ] .gitignore creado
[ ] Primer commit con mensaje semántico
[ ] Repositorio público en GitHub
[ ] README visible con documentación completa

Fase 2 — Containerización
[ ] Dockerfile Producer Python
[ ] Dockerfile Consumer Spring Boot (multi-stage)
[ ] application.properties con variables de entorno
[ ] docker-compose.full.yml con todos los servicios
[ ] docker compose up funciona con un solo comando
[ ] Topics creados automáticamente por kafka-init

Fase 3 — Despliegue Cloud
[ ] Kafka en Confluent Cloud (o MSK / Event Hubs)
[ ] Consumer desplegado y accesible
[ ] GET /api/v1/health responde desde URL pública
[ ] Variables sensibles en secrets del cloud provider
[ ] URL documentada en README

Fase 4 — Integración Weather Explorer
[ ] VITE_COOLDOWN_ENGINE_URL configurada
[ ] cooldownService.ts implementado
[ ] Panel de sugerencias en el mapa funcional
[ ] CORS validado entre ambas aplicaciones
```
