variable "name" { type = string }
variable "availability_zone" { type = string }
variable "size_gib" { type = number }

# local-path de k3s queda sobre este disco. Sobrevive stop/start, no destroy.
resource "aws_ebs_volume" "k3s" {
  availability_zone = var.availability_zone
  size              = var.size_gib
  type              = "gp3"
  encrypted         = true
  tags              = { Name = "${var.name}-k3s-data" }
}

output "volume_id" { value = aws_ebs_volume.k3s.id }
