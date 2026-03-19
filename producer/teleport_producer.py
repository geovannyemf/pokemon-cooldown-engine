"""
Pokémon GO Cooldown Engine
Productor Python — Simula eventos de teleport del jugador
Publica en el topic Kafka: teleport-events
"""

import json
import time
import uuid
from datetime import datetime, timezone
from kafka import KafkaProducer

# ─────────────────────────────────────────────────────
# CONFIGURACIÓN
# ─────────────────────────────────────────────────────
KAFKA_BROKER = "localhost:9092"
TOPIC = "teleport-events"
PLAYER_ID = "player_geovanny_001"

# Ubicación actual simulada del jugador (Ciudad de México)
CURRENT_LOCATION = {
    "lat": 19.4326,
    "lon": -99.1332,
    "name": "Ciudad de México"
}


# ─────────────────────────────────────────────────────
# INICIALIZAR PRODUCTOR KAFKA
# value_serializer: convierte el dict Python → bytes JSON
# ─────────────────────────────────────────────────────
producer = KafkaProducer(
    bootstrap_servers=KAFKA_BROKER,
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    key_serializer=lambda k: k.encode("utf-8"),
    acks="all"  # Espera confirmación del broker antes de continuar
)


def load_destinations(filepath="destinations.json"):
    """Carga los destinos desde el JSON hardcodeado."""
    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data["destinations"]


def build_teleport_event(destination: dict) -> dict:
    """
    Construye el evento de teleport que se publicará en Kafka.
    Este es el contrato del mensaje — el consumer Java lo esperará exactamente así.
    """
    return {
        "event_id": str(uuid.uuid4()),
        "player_id": PLAYER_ID,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "action_type": "TELEPORT",
        "from_location": {
            "lat": CURRENT_LOCATION["lat"],
            "lon": CURRENT_LOCATION["lon"],
            "name": CURRENT_LOCATION["name"]
        },
        "to_location": {
            "id": destination["id"],
            "lat": destination["lat"],
            "lon": destination["lon"],
            "name": destination["name"],
            "country": destination["country"]
        }
    }


def publish_event(destination: dict):
    """Publica un evento de teleport en el topic Kafka."""
    event = build_teleport_event(destination)

    # La KEY del mensaje es el player_id.
    # Kafka usa la key para decidir a qué partition enviar el mensaje.
    # Todos los eventos del mismo jugador irán a la misma partition → orden garantizado.
    future = producer.send(
        topic=TOPIC,
        key=PLAYER_ID,
        value=event
    )

    # Esperar confirmación del broker
    record_metadata = future.get(timeout=10)

    print(f"\n✅ Evento publicado:")
    print(f"   Destino  : {destination['name']}, {destination['country']}")
    print(f"   Topic    : {record_metadata.topic}")
    print(f"   Partition: {record_metadata.partition}")  # 🧠 Verás siempre la misma — por la key
    print(f"   Offset   : {record_metadata.offset}")     # 🧠 Número incremental del mensaje
    print(f"   Event ID : {event['event_id']}")


def main():
    print("🎮 Pokémon GO Cooldown Engine — Productor Python")
    print(f"📍 Ubicación actual: {CURRENT_LOCATION['name']}")
    print(f"📡 Conectando a Kafka en {KAFKA_BROKER}...\n")

    destinations = load_destinations()

    print("Destinos disponibles:")
    for i, dest in enumerate(destinations):
        print(f"  [{i:02d}] {dest['name']}, {dest['country']}")

    print("\n¿A qué destino quieres teleportarte?")
    print("  [A] Enviar todos (modo batch)")
    print("  [0-14] Enviar uno específico")

    choice = input("\nTu elección: ").strip().upper()

    if choice == "A":
        print(f"\n📤 Enviando {len(destinations)} eventos...\n")
        for dest in destinations:
            publish_event(dest)
            time.sleep(0.5)  # pequeño delay entre mensajes
        print(f"\n🏁 Todos los eventos publicados.")
    elif choice.isdigit() and 0 <= int(choice) <= len(destinations) - 1:
        publish_event(destinations[int(choice)])
    else:
        print("❌ Opción inválida.")

    producer.flush()
    producer.close()
    print("\n📡 Conexión cerrada.")


if __name__ == "__main__":
    main()