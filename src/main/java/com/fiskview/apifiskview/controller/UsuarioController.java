package com.fiskview.apifiskview.controller;

import com.fiskview.apifiskview.model.UsuarioVotante;
import com.fiskview.apifiskview.repository.UsuarioVotanteRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/votantes")
@CrossOrigin
public class UsuarioController {

    @Autowired
    private UsuarioVotanteRepository usuariovotanteRepository;

    /*@Autowired
    private PasswordEncoder passwordEncoder;*/

    @PostMapping
    public ResponseEntity<String> registrarUsuario(@RequestBody UsuarioVotante usuarios) {
        Optional<UsuarioVotante> usuarioExistente = usuariovotanteRepository.findByEmail(usuarios.getEmail());
        if (usuarioExistente.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El correo electrónico ya está en uso.");
        }

        if (!validarEmail(usuarios.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El correo electrónico no es válido.");
        }

        if (usuarios.getNombres() == null || usuarios.getNombres().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El nombre es obligatorio.");
        }

        if (usuarios.getApellidos() == null || usuarios.getApellidos().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El apellido es obligatorio.");
        }

        String password = usuarios.getPassword();
        if (!validarContrasena(password)) {
            StringBuilder mensaje = new StringBuilder("La contraseña no cumple con los requisitos mínimos. Requisitos faltantes:");
            if (password.length() < 8) {
                mensaje.append(" La contraseña debe tener al menos 8 caracteres.");
            }
            if (!contieneLetraMayuscula(password)) {
                mensaje.append(" Agregue letras mayúsculas.");
            }
            if (!contieneLetraMinuscula(password)) {
                mensaje.append(" Agregue letras minúsculas.");
            }
            if (!contieneCaracterEspecial(password)) {
                mensaje.append(" Agregue un caracter especial.");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mensaje.toString());
        }

        //usuarios.setContrasena(passwordEncoder.encode(usuarios.getContrasena()));
        UsuarioVotante usuarioGuardado = usuariovotanteRepository.save(usuarios);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Usuario registrado correctamente.");
        Gson gson = new Gson();
        String responseBody = gson.toJson(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
    }


    @PutMapping("/{id}")
    public ResponseEntity<String> actualizarUsuario(@PathVariable Long id, @RequestBody UsuarioVotante usuarioActualizado) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Optional<UsuarioVotante> usuarioExistenteOpt = usuariovotanteRepository.findById(id);
        if (!usuarioExistenteOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        UsuarioVotante usuarioExistente = usuarioExistenteOpt.get();

        // Permitir solo a los admins o al propio usuario modificar la información
        if (!usuarioExistente.getEmail().equals(authenticatedUserEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tienes permiso para modificar la información de este usuario.");
        }

        // Validaciones y actualizaciones
        if (usuarioActualizado.getEmail() != null && !usuarioActualizado.getEmail().equals(usuarioExistente.getEmail())) {
            if (!validarEmail(usuarioActualizado.getEmail())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El correo electrónico no es válido.");
            }
            Optional<UsuarioVotante> usuarioPorEmail = usuariovotanteRepository.findByEmail(usuarioActualizado.getEmail());
            if (usuarioPorEmail.isPresent() && !usuarioPorEmail.get().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El correo electrónico ya está en uso.");
            }
            usuarioExistente.setEmail(usuarioActualizado.getEmail());
        }

        if (usuarioActualizado.getPassword() != null && !usuarioActualizado.getPassword().isEmpty()) {
            if (!validarContrasena(usuarioActualizado.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La contraseña no cumple con los requisitos mínimos.");
            }
            //usuarioExistente.setPassword(passwordEncoder.encode(usuarioActualizado.getContrasena()));
        }

        if (usuarioActualizado.getNombres() != null && !usuarioActualizado.getNombres().isEmpty()) {
            usuarioExistente.setNombres(usuarioActualizado.getNombres());
        }

        if (usuarioActualizado.getApellidos() != null && !usuarioActualizado.getApellidos().isEmpty()) {
            usuarioExistente.setApellidos(usuarioActualizado.getApellidos());
        }

        usuariovotanteRepository.save(usuarioExistente);
        return ResponseEntity.ok("Usuario actualizado correctamente.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminarUsuario(@PathVariable Long id) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();
        Optional<UsuarioVotante> usuarioExistenteOpt = usuariovotanteRepository.findById(id);
        if (!usuarioExistenteOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        UsuarioVotante usuarioExistente = usuarioExistenteOpt.get();

        usuariovotanteRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Usuario eliminado correctamente.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioVotante> obtenerUsuarioPorId(@PathVariable Long id) {
        Optional<UsuarioVotante> usuario = usuariovotanteRepository.findById(id);
        return usuario.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioVotante>> obtenerTodosLosUsuarios() {
        List<UsuarioVotante> usuarios = usuariovotanteRepository.findAll();
        return ResponseEntity.ok(usuarios);
    }

    // Métodos de validación

    private boolean validarEmail(String email) {
        String regex = "^[a-zA-Z0-9_+&-]+(?:\\.[a-zA-Z0-9_+&-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    private boolean validarContrasena(String contrasena) {
        if (contrasena.length() < 8) {
            return false;
        }

        String pattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return contrasena.matches(pattern);
    }

    private boolean contieneLetraMayuscula(String contrasena) {
        return contrasena.matches(".*[A-Z].*");
    }

    private boolean contieneLetraMinuscula(String contrasena) {
        return contrasena.matches(".*[a-z].*");
    }

    private boolean contieneCaracterEspecial(String contrasena) {
        return contrasena.matches(".*[@#$%^&+=!].*");
    }

    // Método para obtener el usuario autenticado
    private String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }
}
