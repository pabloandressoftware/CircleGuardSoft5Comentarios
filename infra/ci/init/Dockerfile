FROM alpine:3.23
RUN apk add --no-cache curl jq
COPY configure.sh /usr/local/bin/configure-ci
ENTRYPOINT ["sh", "/usr/local/bin/configure-ci"]
