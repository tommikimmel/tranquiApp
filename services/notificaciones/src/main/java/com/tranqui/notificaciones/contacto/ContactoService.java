package com.tranqui.notificaciones.contacto;

import com.tranqui.notificaciones.notificacion.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ContactoService {

    private final ContactoRepository contactos;
    private final NotificacionRepository notificaciones;

    public ContactoService(ContactoRepository contactos, NotificacionRepository notificaciones) {
        this.contactos = contactos;
        this.notificaciones = notificaciones;
    }

    /** Busca el contacto o tira {@link ContactoNoEncontradoException} para que el evento se reintente. */
    @Transactional(readOnly = true)
    public Contacto obtener(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El evento no trae el id del usuario");
        }
        return contactos.findById(id).orElseThrow(() -> new ContactoNoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public Optional<Contacto> buscarPorEmail(String email) {
        return contactos.buscarPorEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Contacto> administradores() {
        return contactos.findByRol("ADMIN");
    }

    /**
     * Alta o actualización. Los campos nulos no pisan lo que ya había: un evento viejo de un
     * productor que todavía no manda {@code rol} o {@code emailHabilitado} no los borra.
     */
    @Transactional
    public Contacto guardar(Long id, String email, String nombre, String apellido, String rol, Boolean emailHabilitado) {
        Contacto contacto = contactos.findById(id).orElseGet(() -> new Contacto(id, email));
        if (email != null) contacto.setEmail(email);
        if (nombre != null) contacto.setNombre(nombre);
        if (apellido != null) contacto.setApellido(apellido);
        if (rol != null) contacto.setRol(rol);
        if (emailHabilitado != null) contacto.setEmailHabilitado(emailHabilitado);
        contacto.tocar();
        return contactos.save(contacto);
    }

    /** Cambio de nombre desde el perfil profesional. Si todavía no está la copia, se reintenta. */
    @Transactional
    public void actualizarNombre(Long id, String nombre, String apellido) {
        Contacto contacto = obtener(id);
        if (nombre != null) contacto.setNombre(nombre);
        if (apellido != null) contacto.setApellido(apellido);
        contacto.tocar();
    }

    /** Baja de la cuenta (Ley 25.326): se borra la copia y sus notificaciones. */
    @Transactional
    public void eliminar(Long id) {
        notificaciones.deleteByUsuarioId(id);
        contactos.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long cantidad() {
        return contactos.count();
    }
}
