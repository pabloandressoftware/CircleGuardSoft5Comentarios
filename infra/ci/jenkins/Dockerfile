FROM docker:28.5.2-cli AS docker_cli
FROM docker/buildx-bin:0.29.1 AS buildx
FROM gradle:8.14.5-jdk21 AS gradle
FROM node:22-bookworm-slim AS node

FROM jenkins/jenkins:lts-jdk21
COPY --from=docker_cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker_cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
COPY --from=gradle /opt/gradle /opt/gradle
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
ENV PATH="/opt/gradle/bin:${PATH}"
USER root
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# HU-19: herramientas de infraestructura y Kubernetes para el pipeline.
# Se fijan versiones para que un rebuild no cambie el comportamiento del job.
ARG TERRAFORM_VERSION=1.9.8
ARG HELM_VERSION=3.16.4
ARG KUBECTL_VERSION=1.34.13
ARG AWSCLI_VERSION=2.19.1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl unzip; \
    tmp=$(mktemp -d); \
    curl -fsSL -o "$tmp/terraform.zip" \
        "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip"; \
    unzip -q "$tmp/terraform.zip" -d "$tmp/terraform"; \
    install -m 0755 "$tmp/terraform/terraform" /usr/local/bin/terraform; \
    curl -fsSL -o "$tmp/helm.tar.gz" \
        "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz"; \
    tar -xzf "$tmp/helm.tar.gz" -C "$tmp"; \
    install -m 0755 "$tmp/linux-amd64/helm" /usr/local/bin/helm; \
    curl -fsSL -o "$tmp/kubectl" \
        "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl"; \
    install -m 0755 "$tmp/kubectl" /usr/local/bin/kubectl; \
    curl -fsSL -o "$tmp/awscliv2.zip" \
        "https://awscli.amazonaws.com/awscli-exe-linux-x86_64-${AWSCLI_VERSION}.zip"; \
    unzip -q "$tmp/awscliv2.zip" -d "$tmp"; \
    "$tmp/aws/install" --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli; \
    rm -rf "$tmp" /var/lib/apt/lists/*; \
    terraform version; helm version --short; kubectl version --client; aws --version
USER jenkins
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
ENV JENKINS_UC_DOWNLOAD=https://mirror.twds.com.tw/jenkins
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY --chown=jenkins:jenkins casc.yml /opt/jenkins/casc.yml

USER root
ARG PLAYWRIGHT_VERSION=1.61.1
RUN npx --yes --package="playwright@${PLAYWRIGHT_VERSION}" playwright install-deps chromium && \
    npm cache clean --force && rm -rf /var/lib/apt/lists/*
USER jenkins

# BuildKit: los Dockerfile de los servicios usan RUN --mount, que el constructor
# clasico no entiende ('the --mount option requires BuildKit'). Va al final
# para no rehacer las capas lentas de arriba.
COPY --from=buildx /buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
