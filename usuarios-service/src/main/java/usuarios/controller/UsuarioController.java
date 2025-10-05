package usuarios.controller;

import usuarios.model.Usuario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final List<Usuario> usuarios = List.of(
            new Usuario(1, "Ana"),
            new Usuario(2, "Luis")
    );

    @GetMapping
    public List<Usuario> getUsuarios() {
        return usuarios;
    }
}
