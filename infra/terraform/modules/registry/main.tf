# Integra Nexus existente: HU-19 publica y autentica las imágenes allí.
# Este módulo configura containerd; no crea otro registro ni guarda contraseñas.
variable "host" {
  type = string
  validation {
    condition     = can(regex("^[A-Za-z0-9][A-Za-z0-9.-]+(:[0-9]+)?$", var.host)) && !can(regex("^(localhost|127\\.|0\\.0\\.0\\.0)", var.host))
    error_message = "Nexus debe ser un hostname/IP alcanzable por k3s, sin esquema ni ruta; no localhost."
  }
}
variable "http" { type = bool }

output "host" { value = var.host }
output "registries_yaml" {
  value = yamlencode({
    mirrors = {
      (var.host) = { endpoint = ["${var.http ? "http" : "https"}://${var.host}"] }
    }
  })
}
