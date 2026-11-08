FROM docker:28.5.2-cli AS docker_cli
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
