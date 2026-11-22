variable "name" { type = string }
variable "ami_id" { type = string }
variable "instance_type" { type = string }
variable "key_name" { type = string }
variable "subnet_id" { type = string }
variable "security_group_id" { type = string }
variable "public_ip" { type = string }
variable "volume_id" { type = string }
variable "k3s_version" { type = string }
variable "registries_yaml" { type = string }
variable "log_policy" { type = string }
variable "agent_config" { type = string }
variable "region" { type = string }

resource "aws_iam_role" "node" {
  name_prefix = "${var.name}-"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "logs" {
  role   = aws_iam_role.node.id
  policy = var.log_policy
}

resource "aws_iam_instance_profile" "node" {
  name_prefix = "${var.name}-"
  role        = aws_iam_role.node.name
}

resource "aws_instance" "server" {
  ami                         = var.ami_id
  instance_type               = var.instance_type
  key_name                    = var.key_name
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  iam_instance_profile        = aws_iam_instance_profile.node.name
  associate_public_ip_address = false
  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/cloud-init.sh.tftpl", {
    public_ip      = var.public_ip
    volume_serial  = replace(var.volume_id, "-", "")
    k3s_version    = var.k3s_version
    registries_b64 = base64encode(var.registries_yaml)
    agent_b64      = base64encode(var.agent_config)
    region         = var.region
  })
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }
  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }
  tags = { Name = var.name }
}

output "instance_id" { value = aws_instance.server.id }
