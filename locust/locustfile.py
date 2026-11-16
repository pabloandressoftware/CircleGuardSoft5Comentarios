"""HU-26 · Prueba de carga de CircleGuard con Locust.

Prueba dos endpoints:
  - POST /api/v1/gate/validate  (gateway-service, valida un QR)
  - POST /api/v1/surveys        (form-service, envia una encuesta de salud)

Objetivo explicito (propuesto, por confirmar con el equipo):
  - p95 < 500 ms   (LATENCY_P95_MS)
  - errores < 1 %  (MAX_ERROR_RATE)
  - con 100 usuarios simultaneos

Si no se cumple, Locust termina con codigo de salida 1 y el pipeline lo ve como fallo.

Uso (la clave del QR es la del ambiente a probar; nunca se escribe en el repositorio):

  QR_SECRET=... docker run --rm --network host -e QR_SECRET -v "$PWD/locust":/mnt/locust \
      locustio/locust:2.32.2 -f /mnt/locust/locustfile.py --headless \
      -u 100 -r 20 -t 60s --host http://127.0.0.1:8087 --csv /mnt/locust/resultado
"""
import base64
import hashlib
import hmac
import json
import os
import time
import uuid

from locust import HttpUser, between, events, task

LATENCY_P95_MS = float(os.environ.get("LATENCY_P95_MS", "500"))
MAX_ERROR_RATE = float(os.environ.get("MAX_ERROR_RATE", "0.01"))
# Cada servicio se prueba contra su propia direccion (con un proxy o Ingress es el mismo host).
GATE_HOST = os.environ.get("GATE_HOST", "")
FORM_HOST = os.environ.get("FORM_HOST", "")


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def firmar_qr(secreto: str, anonymous_id: str, vigencia_s: int = 3600) -> str:
    """Genera un token HS256 igual al que emite auth-service (subject = id anonimo)."""
    ahora = int(time.time())
    cabecera = _b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    cuerpo = _b64(json.dumps({"sub": anonymous_id, "iat": ahora, "exp": ahora + vigencia_s},
                             separators=(",", ":")).encode())
    firma = hmac.new(secreto.encode(), f"{cabecera}.{cuerpo}".encode(), hashlib.sha256).digest()
    return f"{cabecera}.{cuerpo}.{_b64(firma)}"


class UsuarioCircleGuard(HttpUser):
    wait_time = between(0.5, 1.5)

    def on_start(self):
        self.qr_secret = os.environ["QR_SECRET"]
        self.anonymous_id = str(uuid.uuid4())

    @task(3)
    def validar_qr(self):
        token = firmar_qr(self.qr_secret, self.anonymous_id)
        self.client.post(f"{GATE_HOST}/api/v1/gate/validate", json={"token": token},
                         name="POST /api/v1/gate/validate")

    @task(1)
    def enviar_encuesta(self):
        self.client.post(f"{FORM_HOST}/api/v1/surveys", name="POST /api/v1/surveys", json={
            "anonymousId": self.anonymous_id,
            "hasFever": False,
            "hasCough": False,
            "otherSymptoms": "ninguno",
        })


@events.quitting.add_listener
def verificar_objetivo(environment, **_):
    """Convierte el objetivo en un criterio automatico de aprobado o fallo, endpoint por endpoint."""
    cumple = True
    hay_datos = False
    for nombre, estadistica in sorted(environment.stats.entries.items(), key=lambda e: e[0][0]):
        if estadistica.num_requests == 0:
            continue
        hay_datos = True
        p95 = estadistica.get_response_time_percentile(0.95)
        errores = estadistica.fail_ratio
        ok = p95 < LATENCY_P95_MS and errores < MAX_ERROR_RATE
        cumple = cumple and ok
        print(f"{'CUMPLE   ' if ok else 'NO CUMPLE'} {nombre[0]}: p95={p95:.0f} ms (meta < {LATENCY_P95_MS:.0f}), "
              f"errores={errores:.2%} (meta < {MAX_ERROR_RATE:.0%}), solicitudes={estadistica.num_requests}")
    if not hay_datos:
        print("Sin solicitudes: la prueba no sirve")
    environment.process_exit_code = 0 if (hay_datos and cumple) else 1
