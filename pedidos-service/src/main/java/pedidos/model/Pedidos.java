package pedidos.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Pedidos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String producto;

    private int idUsuario;

    public Pedidos(int idUsuario, String producto) {
        this.idUsuario = idUsuario;
        this.producto = producto;
    }

    public int getIdUsuario() { return idUsuario; }
    public String getProducto() { return producto; }

}
