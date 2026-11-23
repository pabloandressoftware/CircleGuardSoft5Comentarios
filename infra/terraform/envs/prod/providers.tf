terraform {
  required_version = "~> 1.9.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.100.0" }
  }
  backend "s3" {}
}
provider "aws" {
  region              = var.config.region
  allowed_account_ids = [var.config.account_id]
  default_tags {
    tags = { Project = "CircleGuard", Environment = var.config.environment, ManagedBy = "Terraform", Story = "HU-17" }
  }
}
