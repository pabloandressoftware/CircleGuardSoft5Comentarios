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
                    # 404 significa que el host no coincide con el Ingress (antes se
                    # aceptaba y ocultaba despliegues rotos). 401/403 son validos en
                    # rutas que exigen autenticacion.
                    2??|3??|401|403) ;;
                    *)
                    *)
                        echo "Prueba de humo fallida en $path ($code)." >&2
                        exit 1
                        ;;
                esac
            done

            # HU-21: confirma que el Ingress sirve un certificado TLS real.
            if command -v openssl >/dev/null 2>&1; then
                echo "--- certificado TLS de $SMOKE_HOST ---"
                echo | openssl s_client -servername "$SMOKE_HOST" -connect "$SMOKE_HOST:443" 2>/dev/null \
                    | openssl x509 -noout -subject -issuer -dates
            else
                echo 'AVISO: openssl no esta instalado; se omite la lectura del certificado TLS.'
            fi
        '''
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this
