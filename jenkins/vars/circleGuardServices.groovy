def call() {
    return [
        [name: 'circleguard-auth-service',         port: 8180],
        [name: 'circleguard-notification-service', port: 8082],
        [name: 'circleguard-identity-service',     port: 8083],
        [name: 'circleguard-dashboard-service',    port: 8084],
        [name: 'circleguard-file-service',         port: 8085],
        [name: 'circleguard-form-service',         port: 8086],
        [name: 'circleguard-gateway-service',      port: 8087],
        [name: 'circleguard-promotion-service',    port: 8088]
    ]
}
