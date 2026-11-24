terraform {
  required_version = "~> 1.9.0"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.100.0" }
    random = { source = "hashicorp/random", version = "~> 3.7.2" }
  }
}

variable "config" {
  type = object({
    environment       = string
    account_id        = string
    region            = string
    availability_zone = string
    cidr              = string
    ami_id            = string
    key_name          = string
    admin_cidrs       = set(string)
    api_cidrs         = set(string)
    web_cidrs         = set(string)
    registry_host     = string
    registry_http     = bool
    instance_type     = string
    data_size_gib     = number
    log_retention     = number
    k3s_version       = string
    worker_count      = optional(number, 1)
  })
  validation {
    condition     = contains([0, 1], var.config.worker_count)
    error_message = "La demostracion admite cero o un worker por ambiente."
  }
  validation {
    condition     = contains(["dev", "stage", "prod"], var.config.environment) && can(regex("^[0-9]{12}$", var.config.account_id))
    error_message = "Usa dev/stage/prod y una cuenta AWS de 12 dígitos."
  }
  validation {
    condition = length(var.config.admin_cidrs) > 0 && length(var.config.api_cidrs) > 0 && alltrue([
      for cidr in setunion(var.config.admin_cidrs, var.config.api_cidrs) : can(cidrnetmask(cidr)) && try(tonumber(split("/", cidr)[1]) >= 24, false)
    ])
    error_message = "SSH y API requieren CIDR IPv4 explícitos /24 o más restrictivos (preferir /32)."
  }
  validation {
    condition     = can(cidrnetmask(var.config.cidr)) && var.config.data_size_gib >= 20 && can(regex("^v[0-9]+\\.[0-9]+\\.[0-9]+\\+k3s[0-9]+$", var.config.k3s_version))
    error_message = "Revisa CIDR, disco >=20 GiB y versión fija de k3s."
  }
}

locals { name = "circleguard-${var.config.environment}" }

module "network" {
  source            = "../network"
  name              = local.name
  cidr              = var.config.cidr
  availability_zone = var.config.availability_zone
  admin_cidrs       = var.config.admin_cidrs
  api_cidrs         = var.config.api_cidrs
  web_cidrs         = var.config.web_cidrs
  cluster_network   = var.config.worker_count > 0
}
module "registry" {
  source = "../registry"
  host   = var.config.registry_host
  http   = var.config.registry_http
}
module "storage" {
  source            = "../storage"
  name              = local.name
  availability_zone = var.config.availability_zone
  size_gib          = var.config.data_size_gib
}
module "observability" {
  source         = "../observability"
  name           = local.name
  region         = var.config.region
  retention_days = var.config.log_retention
}
module "cluster" {
  source               = "../cluster"
  name                 = local.name
  ami_id               = var.config.ami_id
  instance_type        = var.config.instance_type
  key_name             = var.config.key_name
  subnet_id            = module.network.subnet_id
  security_group_id    = module.network.security_group_id
  public_ip            = module.network.public_ip
  volume_id            = module.storage.volume_id
  k3s_version          = var.config.k3s_version
  registries_yaml      = module.registry.registries_yaml
  log_policy           = module.observability.log_policy
  agent_config         = module.observability.agent_config
  region               = var.config.region
  permissions_boundary = "arn:aws:iam::${var.config.account_id}:policy/CircleGuardNodeBoundary"
  agent_token          = var.config.worker_count > 0 ? random_password.agent[0].result : ""
}

resource "random_password" "agent" {
  count   = var.config.worker_count > 0 ? 1 : 0
  length  = 64
  special = false
}

resource "aws_instance" "worker" {
  count                  = var.config.worker_count
  ami                    = var.config.ami_id
  instance_type          = var.config.instance_type
  key_name               = var.config.key_name
  subnet_id              = module.network.subnet_id
  vpc_security_group_ids = [module.network.security_group_id]
  iam_instance_profile   = module.cluster.instance_profile
  # IP automatica para egress sin un NAT Gateway ni otra Elastic IP.
  associate_public_ip_address = true
  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/worker-cloud-init.sh.tftpl", {
    name           = "${local.name}-worker-${count.index + 1}"
    server_ip      = module.cluster.private_ip
    agent_token    = random_password.agent[0].result
    k3s_version    = var.config.k3s_version
    registries_b64 = base64encode(module.registry.registries_yaml)
  })
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }
  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }
  tags       = { Name = "${local.name}-worker-${count.index + 1}" }
  depends_on = [module.network]
}

resource "aws_eip_association" "node" {
  instance_id   = module.cluster.instance_id
  allocation_id = module.network.allocation_id
}
resource "aws_volume_attachment" "data" {
  device_name = "/dev/sdf"
  volume_id   = module.storage.volume_id
  instance_id = module.cluster.instance_id
  # Detener el nodo antes de desmontar el volumen durante destroy/reemplazo.
  stop_instance_before_detaching = true
}

output "instance_id" { value = module.cluster.instance_id }
output "public_ip" { value = module.network.public_ip }
output "registry" { value = module.registry.host }
output "log_group" { value = module.observability.log_group }
output "api_endpoint" { value = "https://${module.network.public_ip}:6443" }
