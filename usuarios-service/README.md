# Microservicios y Escalabilidad
## Microservicio: `usuario-service`

Este servicio expone un conjunto de **usuarios de ejemplo** y cumple la función de proveedor de datos para otros microservicios, como `pedidos-service`.
## Participantes
* Luisa Fernanda Rojas
* Ivan Andres Venegas
* Andrés Felipe Garay
* Sergio Ivan Perea
* Rafael Felipe Muñoz
---

### 📌 Endpoints

- **GET `/usuarios`**  
  Retorna una lista de usuarios registrados en memoria. 
  
  **Ejemplo de respuesta:**
```json
[
  { "id": 1, "nombre": "Ana" },
  { "id": 2, "nombre": "Luis" }
]