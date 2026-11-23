# Plantilla: los valores TEST-NET, example.invalid, cuenta y AMI son intencionalmente inoperables.
# Editar únicamente tras confirmar la cuenta, sus cuotas y el presupuesto.
config = {
  environment       = "prod"
  account_id        = "000000000000"
  region            = "us-east-2"
  availability_zone = "us-east-2a"
  cidr              = "10.70.0.0/16"
  ami_id            = "ami-00000000000000000"
  key_name          = "REEMPLAZAR"
  admin_cidrs       = ["192.0.2.1/32"]
  api_cidrs         = ["192.0.2.2/32"]
  web_cidrs         = ["0.0.0.0/0"]
  registry_host     = "registry.example.invalid:5000"
  registry_http     = false
  instance_type     = "t3.xlarge"
  data_size_gib     = 100
  log_retention     = 30
  k3s_version       = "v1.34.13+k3s1"
}
