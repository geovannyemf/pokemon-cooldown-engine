# ⚙️ Getting Started — Pokémon GO Cooldown Engine

> Guía completa para instalar y correr el proyecto desde cero en una máquina nueva.
> Tiempo estimado: 15-20 minutos.

---

## Requisitos del Sistema

| Herramienta | Versión mínima | Para qué se usa |
|---|---|---|
| Git | cualquiera | Clonar el repositorio |
| Docker Desktop | 4.x | Correr Kafka, Zookeeper y Kafka UI |
| Java JDK | 17 o 21 | Compilar y correr el Consumer Spring Boot |
| Python | 3.10+ | Correr el Producer |
| Maven | incluido en el proyecto (`./mvnw`) | No requiere instalación separada |

---

## Paso 1 — Instalar las herramientas base

### Git
- **Windows:** https://git-scm.com/download/win → instalar con opciones por defecto
- **macOS:** `brew install git`
- **Linux/WSL:** `sudo apt install git -y`

Verificar:
```bash
git --version
```

---

### Docker Desktop
- **Windows / macOS:** https://www.docker.com/products/docker-desktop
  - Descargar el instalador y seguir el wizard
  - En Windows: habilitar la integración con WSL 2 si usas WSL (Docker Desktop → Settings → Resources → WSL Integration)
- **Linux:** https://docs.docker.com/engine/install/

Verificar que Docker esté corriendo:
```bash
docker --version
docker compose version
```

> **⚠️ Nota para usuarios de WSL en Windows:** El comando correcto es `docker compose` (sin guión). El comando legacy `docker-compose` no estará disponible.

---

### Java JDK 21

- **Windows / macOS / Linux:** https://adoptium.net → descargar `Temurin 21 (LTS)`
- **Linux/WSL:**
```bash
sudo apt install openjdk-21-jdk -y
```

Verificar:
```bash
java -version
# Debe mostrar: openjdk version "21.x.x"
```

---

### Python 3.10+

- **Windows:** https://www.python.org/downloads/ → marcar "Add Python to PATH" durante instalación
- **macOS:** `brew install python`
- **Linux/WSL:**
```bash
sudo apt install python3 python3-pip python3-venv -y
```

Verificar:
```bash
python3 --version
# Debe mostrar: Python 3.10.x o superior
```

---

## Paso 2 — Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/pokemon-cooldown-engine.git
cd pokemon-cooldown-engine
```

La estructura que verás:
```
pokemon-cooldown-engine/
├── docker/
│   └── docker-compose.yml
├── producer/
│   ├── requirements.txt
│   ├── destinations.json
│   └── teleport_producer.py
├── consumer/
│   └── cooldown-engine/
│       ├── pom.xml
│       ├── mvnw
│       └── src/
├── README.md
├── BACKLOG.md
├── GETTING_STARTED.md
└── DEPLOYMENT.md
```

---

## Paso 3 — Levantar la infraestructura Kafka

```bash
cd docker/
docker compose up -d
```

La primera vez descargará las imágenes de Docker (~500 MB). Espera hasta que terminen.

Verifica que los 3 servicios estén corriendo:
```bash
docker ps
```

Debes ver:
```
CONTAINER ID   IMAGE                          STATUS
xxxxxxxxxxxx   provectuslabs/kafka-ui         Up
xxxxxxxxxxxx   confluentinc/cp-kafka:7.5.0    Up
xxxxxxxxxxxx   confluentinc/cp-zookeeper:7.5.0 Up
```

Abre el panel de administración en el navegador: **http://localhost:8090**

---

## Paso 4 — Crear los topics de Kafka

> Solo necesitas hacer esto una vez. Los topics persisten mientras el volumen de Docker no se borre.

```bash
# Topic de entrada: eventos de teleport del jugador
docker exec poke-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic teleport-events --partitions 3 --replication-factor 1

# Topic de salida: resultados del motor de cooldown
docker exec poke-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic cooldown-results --partitions 3 --replication-factor 1
```

Verifica que se crearon:
```bash
docker exec poke-kafka kafka-topics --list --bootstrap-server localhost:9092
```

Resultado esperado:
```
cooldown-results
teleport-events
```

---

## Paso 5 — Levantar el Consumer (Spring Boot)

Abre una **nueva terminal** y ejecuta:

```bash
cd consumer/cooldown-engine/

# Linux / macOS / WSL
./mvnw spring-boot:run

# Windows CMD / PowerShell
mvnw.cmd spring-boot:run
```

La primera vez Maven descargará las dependencias del proyecto (~200 MB). Espera el mensaje:

```
Started CooldownEngineApplication in X.XXX seconds
```

Verifica que la API está activa:
- Desde el navegador: http://localhost:8080/api/v1/health
- Desde terminal:
```bash
# Linux/macOS/WSL
curl http://localhost:8080/api/v1/health

# Windows PowerShell
Invoke-WebRequest http://localhost:8080/api/v1/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "service": "cooldown-engine",
  "version": "1.0.0"
}
```

---

## Paso 6 — Configurar el Producer (Python)

Abre una **nueva terminal** y ejecuta:

```bash
cd producer/

# Crear entorno virtual
python3 -m venv venv

# Activar entorno virtual
source venv/bin/activate          # Linux / macOS / WSL
# venv\Scripts\activate           # Windows CMD
# venv\Scripts\Activate.ps1       # Windows PowerShell

# Instalar dependencias
pip install -r requirements.txt
```

---

## Paso 7 — Enviar tu primer evento de teleport

Con el entorno virtual activado:

```bash
python3 teleport_producer.py
```

Verás el menú:
```
🎮 Pokémon GO Cooldown Engine — Productor Python
📍 Ubicación actual: Ciudad de México
📡 Conectando a Kafka en localhost:9092...

Destinos disponibles:
  [00] Shibuya, Tokyo, Japan
  [01] Santa Monica Pier, Los Angeles, USA
  ...
  [14] Orchard Road, Singapore, Singapore

¿A qué destino quieres teleportarte?
  [A] Enviar todos (modo batch)
  [0-14] Enviar uno específico
```

Elige `0` para enviar un teleport a Shibuya, Tokyo. En la terminal del Consumer (Paso 5) verás:

```
🎮 TELEPORT: Ciudad de México → Shibuya, Tokyo
📏 Distancia: 11307.19 km | ⏱️  Cooldown: 122 min | COOLDOWN_REQUIRED
🏆 TOP 5 DESTINOS DESDE Shibuya, Tokyo:
  1. Shibuya, Tokyo (Japan)        |   0.0 km |   0 min CD | Score: 9.45 | EXCELENTE
  2. Shinjuku Gyoen, Tokyo (Japan) |  2.99 km |   3 min CD | Score: 9.13 | EXCELENTE
  3. Tsim Sha Tsui, Hong Kong      | 2880 km  | 120 min CD | Score: 6.34 | BUENO
  ...
```

---

## Paso 8 — Explorar los resultados

### Kafka UI
Abre **http://localhost:8090** y navega a:
- `pokemon-local → Topics → teleport-events → Messages` — eventos enviados por el producer
- `pokemon-local → Topics → cooldown-results → Messages` — resultados procesados por el consumer
- `pokemon-local → Consumers` — estado del consumer group `cooldown-engine-group`

### REST API
Prueba los endpoints directamente desde el navegador o desde terminal:

**Calcular cooldown entre dos coordenadas:**
```
http://localhost:8080/api/v1/cooldown/calculate?fromLat=19.4326&fromLon=-99.1332&toLat=35.6595&toLon=139.7004
```

**Top 5 destinos desde Tokyo:**
```
http://localhost:8080/api/v1/suggestions?lat=35.6595&lon=139.7004&top=5
```

---

## Resumen de Servicios

| Servicio | URL | Descripción |
|---|---|---|
| Kafka UI | http://localhost:8090 | Panel de administración de Kafka |
| REST API | http://localhost:8080 | Endpoints del motor de cooldown |
| Health Check | http://localhost:8080/api/v1/health | Estado del servicio |
| Kafka Broker | localhost:9092 | Puerto del broker (interno) |

---

## Detener el proyecto

Para detener el Consumer: `Ctrl + C` en su terminal.

Para detener Kafka:
```bash
cd docker/
docker compose down
```

> Los datos de los topics se conservan entre reinicios mientras no ejecutes `docker compose down -v` (que borra los volúmenes).

---

## Solución de Problemas Comunes

### `docker compose` no encontrado
- En Windows/WSL usa `docker compose` (sin guión), no `docker-compose`
- Verifica que Docker Desktop esté corriendo antes de ejecutar comandos

### `./mvnw: Permission denied` (Linux/WSL)
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

### `ModuleNotFoundError: No module named 'kafka.vendor.six.moves'`
`kafka-python` clásico no es compatible con Python 3.12+. Verifica que `requirements.txt` tenga `kafka-python-ng`, no `kafka-python`:
```bash
pip uninstall kafka-python -y
pip install kafka-python-ng==2.2.3
```

### El Consumer no recibe mensajes
Verifica que Kafka esté corriendo antes de iniciar el Consumer:
```bash
docker ps | grep poke-kafka
```

### `curl: Failed to connect to localhost port 8080` (desde WSL)
Spring Boot corre en Windows, no en WSL. Usa la IP de Windows en lugar de `localhost`:
```bash
cat /etc/resolv.conf | grep nameserver | awk '{print $2}'
# Usa esa IP: curl http://172.x.x.x:8080/api/v1/health
```
O accede directamente desde el navegador de Windows en `http://localhost:8080`.

### Puerto 8080 en uso
Cambia el puerto en `consumer/cooldown-engine/src/main/resources/application.properties`:
```properties
server.port=8081
```

---

## Mejoras y Siguientes Pasos

Una vez que el proyecto esté corriendo localmente, los siguientes pasos están documentados en **DEPLOYMENT.md**:

- **Containerización completa** — Dockerfiles para Producer y Consumer, `docker-compose.full.yml` que levanta todo con un solo comando incluyendo la creación automática de topics
- **Despliegue cloud** — opciones evaluadas: Railway (más rápido para MVP), AWS ECS + MSK, Azure Container Apps
- **Integración con Weather Explorer** — consumir `cooldown-results` desde el mapa React y consultar la API REST para sugerencias en tiempo real
- **Persistencia** — guardar historial de cooldowns en PostgreSQL
- **Dead Letter Topic** — manejo robusto de mensajes con error sin pérdida de datos
- **Schema Registry + Avro** — serialización tipada para el contrato entre producer y consumer
