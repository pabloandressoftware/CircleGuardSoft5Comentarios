variable "name" { type = string }
variable "cidr" { type = string }
variable "availability_zone" { type = string }
variable "admin_cidrs" { type = set(string) }
variable "api_cidrs" { type = set(string) }
variable "web_cidrs" { type = set(string) }
variable "cluster_network" {
  type    = bool
  default = false
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = var.name }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.cidr, 8, 1)
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false
  tags                    = { Name = "${var.name}-public" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "node" {
  domain     = "vpc"
  depends_on = [aws_internet_gateway.this]
  tags       = { Name = var.name }
}

resource "aws_security_group" "node" {
  name_prefix = "${var.name}-"
  description = "k3s: SSH and API restricted to operators; HTTP/HTTPS for Ingress"
  vpc_id      = aws_vpc.this.id
}

locals {
  ingress = merge(
    { for cidr in var.admin_cidrs : "ssh-${cidr}" => { port = 22, cidr = cidr } },
    { for cidr in var.api_cidrs : "api-${cidr}" => { port = 6443, cidr = cidr } },
    { for cidr in var.web_cidrs : "http-${cidr}" => { port = 80, cidr = cidr } },
    { for cidr in var.web_cidrs : "https-${cidr}" => { port = 443, cidr = cidr } }
  )
}

resource "aws_vpc_security_group_ingress_rule" "node" {
  for_each          = local.ingress
  security_group_id = aws_security_group.node.id
  description       = each.key
  ip_protocol       = "tcp"
  from_port         = each.value.port
  to_port           = each.value.port
  cidr_ipv4         = each.value.cidr
}

resource "aws_vpc_security_group_egress_rule" "node" {
  security_group_id = aws_security_group.node.id
  description       = "Pull images, OS updates, DNS and AWS APIs"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# Trafico entre nodos del mismo cluster; nunca se publica VXLAN en Internet.
resource "aws_vpc_security_group_ingress_rule" "cluster_tcp" {
  for_each                     = var.cluster_network ? toset(["6443", "10250"]) : toset([])
  security_group_id            = aws_security_group.node.id
  referenced_security_group_id = aws_security_group.node.id
  description                  = "k3s internal ${each.key}"
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
}
resource "aws_vpc_security_group_ingress_rule" "cluster_vxlan" {
  count                        = var.cluster_network ? 1 : 0
  security_group_id            = aws_security_group.node.id
  referenced_security_group_id = aws_security_group.node.id
  description                  = "Flannel VXLAN internal only"
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

output "subnet_id" { value = aws_subnet.public.id }
output "security_group_id" { value = aws_security_group.node.id }
output "public_ip" { value = aws_eip.node.public_ip }
output "allocation_id" { value = aws_eip.node.id }
