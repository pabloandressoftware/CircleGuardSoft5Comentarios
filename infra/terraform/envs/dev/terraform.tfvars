# Proyecto y AMI verificados el 2026-11-23 en us-east-2.
# Completar llave, CIDR de operador/Jenkins y Nexus antes de plan/apply.
config = {
  environment       = "dev"
  account_id        = "001784022331"
  region            = "us-east-2"
  availability_zone = "us-east-2a"
  cidr              = "10.50.0.0/16"
  ami_id            = "ami-09cdee81def788c75"
  key_name          = "circleguard-demo-20261123"
  admin_cidrs       = ["186.86.52.230/32"]
  api_cidrs         = ["186.86.52.230/32", "18.220.203.182/32"]
  web_cidrs         = ["0.0.0.0/0"]
  registry_host     = "registry.18-220-203-182.sslip.io"
  registry_http     = false
  instance_type     = "m7i-flex.large"
  data_size_gib     = 60
  log_retention     = 7
  k3s_version       = "v1.34.13+k3s1"
}
