# Comunicación por eventos (Kafka)

## Propósito

Cuando algo importante cambia (alguien envía una encuesta, se valida un certificado, cambia
un estado de salud), el servicio que lo sabe **publica un evento** en Kafka y sigue con su
trabajo. Los servicios interesados lo **consumen** cuando pueden. Quien publica no sabe quién
escucha, así que se pueden agregar consumidores nuevos sin tocar al productor, y una caída
temporal del consumidor no pierde el evento: Kafka lo guarda hasta que se procese.

## Dónde está

| Tema (topic) | Lo publica | Lo consume | Para qué |
| --- | --- | --- | --- |
| `survey.submitted` | form-service · `HealthSurveyService` | promotion-service · `SurveyListener` | Una encuesta con síntomas puede cambiar el estado de salud |
| `certificate.validated` | form-service · `HealthSurveyService` | promotion-service · `SurveyListener` | Un certificado médico validado libera o confirma un estado |
| `promotion.status.changed` | promotion-service · `HealthStatusService`, `StatusLifecycleService` | notification-service · `ExposureNotificationListener` | Avisar a las personas expuestas |
| `circle.fenced` | promotion-service · `HealthStatusService` | notification-service · `CircleFencedListener` | Avisar que un círculo quedó en cerco |
| `alert.priority` | promotion-service · `HealthStatusService` | notification-service · `PriorityAlertListener` | Caso confirmado o brote grande: alerta prioritaria |
| `proximity.detected` | promotion-service · `LocationResolutionService` | — | Registro de proximidad detectada |
| `audit.identity.accessed` | identity-service · `IdentityVaultController` | — | Auditoría de accesos a la bóveda de identidades |

Rutas: `services/<servicio>/src/main/java/com/circleguard/<servicio>/...`. Por ejemplo,
`services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java`
publica con `kafkaTemplate.send(TOPIC_SURVEY_SUBMITTED, ...)`, y
`services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java`
lo recibe con `@KafkaListener(topics = "survey.submitted")`.

Los eventos usan como clave el `anonymousId`, así Kafka mantiene en orden los eventos de una
misma persona dentro de su partición.

## Trade-offs

| Se gana | Se paga |
| --- | --- |
| Desacople: form no necesita saber que existen promotion ni notification | Kafka y ZooKeeper son dos piezas más que operar y monitorear |
| Tolerancia a caídas del consumidor: el evento espera en Kafka | Consistencia eventual: la notificación llega después, no en la misma petición |
| Agregar un consumidor nuevo no cambia al productor | Depurar un flujo exige seguirlo por varios servicios y temas |
| Absorbe picos de carga | Un evento puede procesarse más de una vez; los consumidores deben tolerar duplicados |

## Evidencia

- `services/circleguard-form-service/src/test/java/com/circleguard/form/integration/SurveyEventKafkaIntegrationTest.java`:
  publica una encuesta y verifica que el evento llega a Kafka.
- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/listener/SurveyListenerTest.java`:
  el consumidor de promotion procesa el evento.
- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/ExposureNotificationListenerTest.java`:
  notification reacciona a `promotion.status.changed`.
