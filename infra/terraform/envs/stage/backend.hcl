# Proyecto verificado; crear primero el backend con infra/terraform/bootstrap.
bucket = "circleguard-tfstate-001784022331-us-east-2"
key = "circleguard/stage/terraform.tfstate"
region = "us-east-2"
dynamodb_table = "circleguard-tf-locks"
encrypt = true
allowed_account_ids = ["001784022331"]
