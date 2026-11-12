#!/usr/bin/env bash
# =============================================================================
# Verificacion del rol D (HU-07, HU-09, HU-12) en una maquina con Docker
# (la VM de CI sirve). Solo necesita Docker y python3: Java, Gradle y Node
# corren en contenedores con las mismas versiones del pipeline.
#
#   bash scripts/verificar-rol-d.sh                         # pruebas y cobertura
#   bash scripts/verificar-rol-d.sh /ruta/env-dev ...       # ademas revisa archivos de ambiente
#
# Deja la evidencia en evidencias/rol-d/.
# =============================================================================
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
root=$(pwd)
out="$root/evidencias/rol-d"
mkdir -p "$out"
docker_cmd=(docker)
docker info >/dev/null 2>&1 || docker_cmd=(sudo docker)
status=0

echo "== 1/4 Pruebas JUnit y cobertura JaCoCo de los 8 servicios"
# El socket de Docker permite que las pruebas con Testcontainers (promotion) creen sus bases
if "${docker_cmd[@]}" run --rm -v "$root":/workspace -w /workspace \
    -v /var/run/docker.sock:/var/run/docker.sock --add-host host.docker.internal:host-gateway \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    -v circleguard-gradle-cache:/home/gradle/.gradle \
    gradle:8.14.5-jdk21 gradle --no-daemon --continue test jacocoTestReport > "$out/01-gradle-test.log" 2>&1; then
  echo "  OK  gradle test (log: evidencias/rol-d/01-gradle-test.log)"
else
  echo "  FALLA gradle test: revisa evidencias/rol-d/01-gradle-test.log"; status=1
fi

python3 - "$root" > "$out/02-resumen-pruebas-y-cobertura.md" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
root = sys.argv[1]
print("| Servicio | Pruebas | Fallidas | Omitidas | Cobertura de lineas |")
print("| --- | --- | --- | --- | --- |")
for svc in sorted(glob.glob(f"{root}/services/circleguard-*-service")):
    name = os.path.basename(svc).replace("circleguard-", "").replace("-service", "")
    tests = failures = skipped = 0
    for f in glob.glob(f"{svc}/build/test-results/test/*.xml"):
        s = ET.parse(f).getroot()
        tests += int(s.get("tests", 0)); failures += int(s.get("failures", 0)) + int(s.get("errors", 0)); skipped += int(s.get("skipped", 0))
    cov = "sin reporte"
    report = f"{svc}/build/reports/jacoco/test/jacocoTestReport.xml"
    if os.path.exists(report):
        for c in ET.parse(report).getroot().findall("counter"):
            if c.get("type") == "LINE":
                missed, covered = int(c.get("missed")), int(c.get("covered"))
                cov = f"{100 * covered / max(1, missed + covered):.1f} %"
    print(f"| {name} | {tests} | {failures} | {skipped} | {cov} |")
PY
cat "$out/02-resumen-pruebas-y-cobertura.md"

echo "== 2/4 Pruebas clave de HU-12"
for f in services/circleguard-gateway-service/build/test-results/test/TEST-com.circleguard.gateway.service.QrValidationServiceTest.xml \
         services/circleguard-auth-service/build/test-results/test/TEST-com.circleguard.auth.service.QrTokenServiceTest.xml; do
  if [[ -f $f ]] && python3 -c "import sys,xml.etree.ElementTree as E; r=E.parse(sys.argv[1]).getroot(); sys.exit(0 if int(r.get('failures'))+int(r.get('errors'))==0 and int(r.get('tests'))>0 else 1)" "$f"; then
    echo "  OK  $(basename "$f" .xml | sed 's/TEST-//')"
  else
    echo "  FALLA $(basename "$f" .xml | sed 's/TEST-//')"; status=1
  fi
done

echo "== 3/4 Pruebas del cliente web (Jest)"
if "${docker_cmd[@]}" run --rm -v "$root/mobile":/app -w /app -e CI=true node:22-bookworm-slim \
    sh -c 'npm ci --no-audit --no-fund >/dev/null && npx jest --ci' > "$out/03-jest.log" 2>&1; then
  echo "  OK  $(grep -E '^Tests:' "$out/03-jest.log")"
else
  echo "  FALLA jest: revisa evidencias/rol-d/03-jest.log"; status=1
fi

echo "== 4/4 Archivos de ambiente (opcional)"
if [[ $# -eq 0 ]]; then
  echo "  (sin archivos: pasa las rutas de circleguard-env-dev/stage/prod para revisarlas)"
fi
for env_file in "$@"; do
  if bash deploy/check-env.sh "$env_file" > "$out/04-check-$(basename "$env_file").txt" 2>&1; then
    echo "  OK  $(basename "$env_file")"
  else
    echo "  FALLA $(basename "$env_file"): revisa evidencias/rol-d/04-check-$(basename "$env_file").txt"; status=1
  fi
done

if [[ $status == 0 ]]; then echo "RESULTADO: todo correcto. Evidencia en evidencias/rol-d/"; else echo "RESULTADO: hay fallos"; fi
exit "$status"
