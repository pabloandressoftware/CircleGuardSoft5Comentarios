#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 ]]; then
  echo "Uso: AWS_REGION=... EC2_KEY_NAME=... EC2_AMI_ID=... SSH_CIDR=... WEB_CIDR=... $0 <dev|stage|prod|ci>" >&2
  exit 2
fi

environment=$1
case "$environment" in
  dev) network_number=10 ;;
  stage) network_number=20 ;;
  prod) network_number=30 ;;
  ci) network_number=40 ;;
  *) echo "Ambiente inválido: $environment" >&2; exit 2 ;;
esac

: "${AWS_REGION:?Indica la región AWS que acordó el equipo}"
: "${EC2_KEY_NAME:?Indica una llave EC2 existente en esa región}"
: "${EC2_AMI_ID:?Indica la AMI Ubuntu 24.04 amd64 elegida para esa región}"
: "${SSH_CIDR:?Indica el CIDR IPv4 de Jenkins y los operadores}"
: "${WEB_CIDR:?Indica el CIDR IPv4 autorizado para HTTPS}"
jenkins_cidr=${JENKINS_CIDR:-}
if [[ $SSH_CIDR == 0.0.0.0/0 || $jenkins_cidr == 0.0.0.0/0 ]]; then
  echo 'Limita SSH al CIDR de Jenkins y los operadores.' >&2
  exit 2
fi

bootstrap_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
stack="circleguard-$environment"
aws cloudformation deploy --region "$AWS_REGION" --stack-name "$stack" \
  --template-file "$bootstrap_dir/ec2-environment.yml" --no-fail-on-empty-changeset \
  --parameter-overrides \
  "Environment=$environment" "NetworkNumber=$network_number" \
  "KeyName=$EC2_KEY_NAME" "UbuntuAmi=$EC2_AMI_ID" "SshCidr=$SSH_CIDR" "WebCidr=$WEB_CIDR" \
  "JenkinsCidr=$jenkins_cidr" \
  "InstanceType=${EC2_INSTANCE_TYPE:-m7i-flex.large}" "DiskSize=${EC2_DISK_GIB:-40}" \
  "DockerBootstrap=$(cat "$bootstrap_dir/docker-user-data.sh")"

aws cloudformation describe-stacks --region "$AWS_REGION" --stack-name "$stack" \
  --query 'Stacks[0].Outputs[].{Name:OutputKey,Value:OutputValue}' --output table
