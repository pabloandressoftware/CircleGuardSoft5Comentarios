terraform {
  required_version = "~> 1.9.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.100.0" }
  }
  backend "s3" {}
}

variable "config" {
  type = object({
    account_id        = string
    region            = string
    availability_zone = string
    ami_id            = string
    instance_type     = string
    operator_cidr     = string
    key_name          = string
    ssh_public_key    = string
    root_size_gib     = number
  })
  validation {
    condition     = var.config.account_id == "001784022331" && var.config.region == "us-east-2" && can(cidrnetmask(var.config.operator_cidr)) && endswith(var.config.operator_cidr, "/32")
    error_message = "CI requiere el proyecto autorizado, Ohio y la IPv4 /32 del operador."
  }
}

provider "aws" {
  region              = var.config.region
  allowed_account_ids = [var.config.account_id]
  default_tags {
    tags = { Project = "CircleGuard", Environment = "ci", ManagedBy = "Terraform", DemoEnd = "2026-11-25" }
  }
}

module "network" {
  source            = "../../modules/network"
  name              = "circleguard-ci"
  cidr              = "10.40.0.0/16"
  availability_zone = var.config.availability_zone
  admin_cidrs       = [var.config.operator_cidr]
  api_cidrs         = []
  web_cidrs         = ["0.0.0.0/0"]
}

resource "aws_key_pair" "demo" {
  key_name   = var.config.key_name
  public_key = var.config.ssh_public_key
}

resource "aws_iam_role" "ci" {
  name = "CircleGuardCiTerraformRole"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ci.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ci" {
  name = "CircleGuardCiTerraformProfile"
  role = aws_iam_role.ci.name
}

data "aws_iam_policy" "ssm_core" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Impide que el rol de despliegue amplie los privilegios de los nodos al
# modificar sus politicas inline. Solo permite SSM y sus logs de k3s.
resource "aws_iam_policy" "node_boundary" {
  name = "CircleGuardNodeBoundary"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(jsondecode(data.aws_iam_policy.ssm_core.policy).Statement, [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
      Resource = "arn:aws:logs:us-east-2:${var.config.account_id}:log-group:/circleguard/circleguard-*/k3s:*"
    }])
  })
}

# Generado a partir de los planes de los ambientes antes de habilitar el CI.
# El primer bootstrap instala únicamente el rol de administración SSM.
resource "aws_iam_role_policy" "terraform" {
  count  = fileexists("${path.module}/../../policies/ci-terraform.json") ? 1 : 0
  name   = "CircleGuardEnvironments"
  role   = aws_iam_role.ci.id
  policy = try(file("${path.module}/../../policies/ci-terraform.json"), jsonencode({ Version = "2012-10-17", Statement = [] }))
}

resource "aws_instance" "ci" {
  lifecycle {
    # DescribeInstances devuelve true al asociar una EIP aunque el lanzamiento
    # no solicito IP automatica. La asociacion la administra aws_eip_association.
    ignore_changes  = [associate_public_ip_address]
    prevent_destroy = true
  }
  ami                         = var.config.ami_id
  instance_type               = var.config.instance_type
  subnet_id                   = module.network.subnet_id
  vpc_security_group_ids      = [module.network.security_group_id]
  associate_public_ip_address = false
  key_name                    = aws_key_pair.demo.key_name
  iam_instance_profile        = aws_iam_instance_profile.ci.name
  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/../../../bootstrap/ci-user-data.sh.tftpl", {
    docker_bootstrap = file("${path.module}/../../../bootstrap/docker-user-data.sh")
  })
  metadata_options {
    http_tokens = "required"
    # Jenkins usa el rol de instancia desde su contenedor, sin access keys.
    http_put_response_hop_limit = 2
  }
  root_block_device {
    volume_size           = var.config.root_size_gib
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }
  tags = { Name = "circleguard-ci" }
}

resource "aws_eip_association" "ci" {
  instance_id   = aws_instance.ci.id
  allocation_id = module.network.allocation_id
}

locals { public_domain = "${replace(module.network.public_ip, ".", "-")}.sslip.io" }
output "instance_id" { value = aws_instance.ci.id }
output "public_ip" { value = module.network.public_ip }
output "jenkins_url" { value = "https://jenkins.${local.public_domain}" }
output "registry_host" { value = "registry.${local.public_domain}" }
output "public_domain" { value = local.public_domain }
output "instance_role_arn" { value = aws_iam_role.ci.arn }
