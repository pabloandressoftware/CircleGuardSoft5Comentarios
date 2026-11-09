/**
 * HU-10 · Prueba de humo de un ambiente ya desplegado.
 *
 *   circleGuardSmoke('ec2-....compute.amazonaws.com')
 *
 * Acepta 2xx/3xx y los 401/403/404/405 de las rutas que no son publicas; solo
 * falla con 5xx o sin respuesta. Se ejecuta desde Jenkins contra HTTPS (CA
 * interna de Caddy), por eso curl usa -k.
 */
def call(String host) {
    if (!host || !(host ==~ /^[A-Za-z0-9][A-Za-z0-9.-]*$/)) {
        error("Host invalido para la prueba de humo: ${host}")
    }
    withEnv(["SMOKE_HOST=${host}"]) {
        sh '''#!/bin/bash
            set -e
            for path in / /api/v1/health; do
                code=$(curl -ksS --max-time 15 -o /dev/null -w '%{http_code}' \
                    "https://$SMOKE_HOST$path" || true)
                echo "humo $path -> $code"
                case "$code" in
                    2??|3??|401|403|404|405) ;;
                    *)
                        echo "Prueba de humo fallida en $path ($code)." >&2
                        exit 1
                        ;;
                esac
            done
        '''
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this
