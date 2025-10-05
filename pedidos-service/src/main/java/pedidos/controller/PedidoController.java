package pedidos.controller;

import pedidos.model.Pedidos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final WebClient webClient = WebClient.create("http://localhost:8080/api");

    private final List<Pedidos> pedidos = List.of(
            new Pedidos(1, "Computador"),
            new Pedidos(2, "Teléfono")
    );

    @GetMapping("/{idUsuario}")
    public Map<String, Object> getPedidos(@PathVariable int idUsuario) {
        List<Map> usuarios = webClient.get()
                .uri("/usuarios")
                .retrieve()
                .bodyToMono(List.class)
                .block();

        Map usuario = usuarios.stream()
                .filter(u -> ((Integer) u.get("id")) == idUsuario)
                .findFirst()
                .orElse(null);

        if (usuario == null) {
            return Map.of("error", "Usuario no encontrado");
        }

        List<Pedidos> pedidosUsuario = pedidos.stream()
                .filter(p -> p.getIdUsuario() == idUsuario)
                .toList();

        return Map.of("usuario", usuario, "pedidos", pedidosUsuario);
    }
}
