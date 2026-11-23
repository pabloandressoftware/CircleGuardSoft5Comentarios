mock_provider "aws" {}
variables { account_id = "123456789012" }
run "protected_backend" {
  command = plan
  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "El estado debe conservar versiones."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.state.block_public_acls && aws_s3_bucket_public_access_block.state.block_public_policy && aws_s3_bucket_public_access_block.state.ignore_public_acls && aws_s3_bucket_public_access_block.state.restrict_public_buckets
    error_message = "El bucket de estado debe ser privado."
  }
  assert {
    condition     = aws_dynamodb_table.locks.hash_key == "LockID" && aws_dynamodb_table.locks.billing_mode == "PAY_PER_REQUEST"
    error_message = "DynamoDB requiere LockID y pago por uso."
  }
}
