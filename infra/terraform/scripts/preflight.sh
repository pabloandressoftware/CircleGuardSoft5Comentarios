#!/usr/bin/env bash
# Solo lecturas; requiere AWS CLI. No crea ni apaga recursos.
set -euo pipefail
account="${1:?Uso: preflight.sh <cuenta-autorizada> [region]}"
region="${2:-us-east-2}"
[[ "$account" =~ ^[0-9]{12}$ && "$account" != 000000000000 ]] || { echo 'Cuenta inválida/plantilla' >&2; exit 1; }
actual=$(aws sts get-caller-identity --query Account --output text)
[[ "$actual" == "$account" ]] || { echo "Cuenta activa $actual distinta de la autorizada $account" >&2; exit 1; }
export AWS_REGION="$region" AWS_DEFAULT_REGION="$region" AWS_PAGER=''
aws sts get-caller-identity
aws service-quotas get-service-quota --service-code ec2 --quota-code L-1216C47A \
  --query 'Quota.{Nombre:QuotaName,vCPU:Value}' --output table
aws ec2 describe-instances --filters Name=instance-state-name,Values=running,pending \
  --query 'Reservations[].Instances[].{ID:InstanceId,Tipo:InstanceType,Estado:State.Name}' --output table
echo 'Revisar además cuotas de VPC, EIP, gp3, Billing/plan/créditos y presupuesto antes de apply.'
