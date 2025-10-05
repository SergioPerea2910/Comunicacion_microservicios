# Microservicios y Escalabilidad
## Microservicio: `pedidos-service`

Este servicio maneja **pedidos de productos** asociados a usuarios.  
Se comunica con el `usuario-service` para enriquecer los datos y mostrar la información completa de un usuario junto con sus pedidos.

## Participantes
* Luisa Fernanda Rojas
* Ivan Andres Venegas
* Andrés Felipe Garay
* Sergio Ivan Perea
* Rafael Felipe Muñoz
---
### 📌 Endpoints

- **GET `/pedidos/{idUsuario}`**  
  Retorna el detalle de un usuario (consultado vía `usuario-service`) junto con la lista de pedidos asociados a ese usuario.

**Ejemplo de respuesta:**
```json
{
  "usuario": {
    "id": 1,
    "nombre": "Ana"
  },
  "pedidos": [
    { "id": 101, "producto": "Computador", "idUsuario": 1 },
    { "id": 102, "producto": "Teclado", "idUsuario": 1 }
  ]
}
