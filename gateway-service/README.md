# Microservicios y Escalabilidad
## Microservicio: `gateway-service`

El **API Gateway** actúa como **punto de entrada único** para los clientes.  
Se encarga de enrutar las solicitudes a los microservicios correspondientes (`usuario-service` y `pedidos-service`), simplificando el consumo y ocultando la complejidad interna.

## Participantes
* Luisa Fernanda Rojas
* Ivan Andres Venegas
* Andrés Felipe Garay
* Sergio Ivan Perea
* Rafael Felipe Muñoz
---

### 📌 Configuración de rutas

En el archivo `application.properties` se definen las rutas del gateway:

```properties
server.port=8080
spring.application.name=gateway-service

spring.cloud.gateway.routes[0].id=usuarios
spring.cloud.gateway.routes[0].uri=http://localhost:8081
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/usuarios/**

spring.cloud.gateway.routes[1].id=pedidos
spring.cloud.gateway.routes[1].uri=http://localhost:8082
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/pedidos/**