package com.example.myapplication.network;

/**
 * Cuerpo de PUT /users (UpdateUserDTO en el backend). El backend solo
 * actualiza los campos que no sean null/blank, asi que "password" se manda
 * null cuando el usuario no quiere cambiarla.
 */
public class UpdateUserRequest {
    private final long id;
    private final String usuario;
    private final String password;
    private final String email;

    public UpdateUserRequest(long id, String usuario, String password, String email) {
        this.id = id;
        this.usuario = usuario;
        this.password = password;
        this.email = email;
    }
}
