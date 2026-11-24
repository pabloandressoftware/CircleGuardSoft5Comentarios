mock_provider "aws" {
  mock_resource "aws_eip" {
    defaults = { public_ip = "203.0.113.10" }
  }
  mock_resource "aws_cloudwatch_log_group" {
    defaults = { arn = "arn:aws:logs:us-east-2:123456789012:log-group:/circleguard/test/k3s" }
  }
}
mock_provider "random" {}

variables {
  config = {
    environment       = "dev"
    account_id        = "123456789012"
    region            = "us-east-2"
    availability_zone = "us-east-2a"
    cidr              = "10.50.0.0/16"
    ami_id            = "ami-0123456789abcdef0"
    key_name          = "test-only"
    admin_cidrs       = ["203.0.113.1/32"]
    api_cidrs         = ["203.0.113.2/32"]
    web_cidrs         = ["0.0.0.0/0"]
    registry_host     = "registry.example.invalid:5000"
    registry_http     = true
    instance_type     = "t3.xlarge"
    data_size_gib     = 60
    log_retention     = 7
    k3s_version       = "v1.34.13+k3s1"
  }
}

run "compose_environment" {
  command = apply
  assert {
    condition     = output.api_endpoint == "https://203.0.113.10:6443"
    error_message = "El API debe usar la IP estable incluida en el certificado k3s."
  }
  assert {
    condition     = yamldecode(module.registry.registries_yaml).mirrors["registry.example.invalid:5000"].endpoint == ["http://registry.example.invalid:5000"]
    error_message = "containerd debe alcanzar el mismo Nexus que utiliza HU-19."
  }
}

run "reject_public_admin" {
  command = plan
  variables {
    config = {
      environment   = "dev", account_id = "123456789012", region = "us-east-2", availability_zone = "us-east-2a"
      cidr          = "10.50.0.0/16", ami_id = "ami-0123456789abcdef0", key_name = "test-only"
      admin_cidrs   = ["0.0.0.0/0"], api_cidrs = ["203.0.113.2/32"], web_cidrs = ["0.0.0.0/0"]
      registry_host = "registry.example.invalid:5000", registry_http = true, instance_type = "t3.xlarge"
      data_size_gib = 60, log_retention = 7, k3s_version = "v1.34.13+k3s1"
    }
  }
  expect_failures = [var.config]
}
