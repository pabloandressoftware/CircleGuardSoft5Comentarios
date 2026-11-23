# Sustituir la cuenta tras confirmar acceso y presupuesto. Nunca poner claves aquí.
bucket = "circleguard-tfstate-000000000000-us-east-2"
key = "circleguard/prod/terraform.tfstate"
region = "us-east-2"
dynamodb_table = "circleguard-tf-locks"
encrypt = true
allowed_account_ids = ["000000000000"]
