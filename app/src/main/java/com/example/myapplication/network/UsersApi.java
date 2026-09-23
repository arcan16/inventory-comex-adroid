package com.example.myapplication.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

public interface UsersApi {
    @GET("users/me")
    Call<UserDTO> getCurrentUser();

    @PUT("users")
    Call<UserDTO> updateUser(@Body UpdateUserRequest request);
}
