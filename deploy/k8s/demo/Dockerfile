# Imagen sintética: permite probar Helm sin servicios Java, middleware ni AWS.
FROM busybox:1.37.0
ARG APP_VERSION
RUN mkdir /www && printf 'CircleGuard rollback demo %s\n' "$APP_VERSION" > /www/index.html
USER 10001:10001
EXPOSE 8080
CMD ["httpd", "-f", "-p", "8080", "-h", "/www"]
