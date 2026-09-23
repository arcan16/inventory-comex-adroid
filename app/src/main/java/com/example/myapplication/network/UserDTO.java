package com.example.myapplication.network;

/** Espejo de UserDTO (backend): datos del usuario autenticado. */
public class UserDTO {
    private long id;
    private String usuario;
    private String email;

    public long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getEmail() {
        return email;
    }
}
