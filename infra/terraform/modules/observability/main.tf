variable "name" { type = string }
variable "region" { type = string }
variable "retention_days" { type = number }

resource "aws_cloudwatch_log_group" "k3s" {
  name              = "/circleguard/${var.name}/k3s"
  retention_in_days = var.retention_days
}

output "log_policy" {
  value = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
      Resource = "${aws_cloudwatch_log_group.k3s.arn}:*"
    }]
  })
}

output "agent_config" {
  value = jsonencode({
    agent = { region = var.region }
    logs = {
      logs_collected = {
        files = {
          collect_list = [for file in ["/var/log/cloud-init-output.log", "/var/log/k3s.log"] : {
            file_path       = file
            log_group_name  = aws_cloudwatch_log_group.k3s.name
            log_stream_name = "{instance_id}/${basename(file)}"
            timezone        = "UTC"
          }]
        }
      }
    }
  })
}
output "log_group" { value = aws_cloudwatch_log_group.k3s.name }
