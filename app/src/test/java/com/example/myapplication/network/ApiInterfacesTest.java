package com.example.myapplication.network;

import org.junit.Test;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit valida las anotaciones de cada metodo hasta que se usa (p. ej. un
 * @Path despues de un @Query), asi que un error ahi no falla al compilar sino
 * que crashea la app al tocar el boton. Con validateEagerly(true), create()
 * revisa todos los metodos de golpe y lanza IllegalArgumentException aqui.
 */
public class ApiInterfacesTest {

    private static final Class<?>[] APIS = {
            DataApi.class,
            HealthApi.class,
            LoginApi.class,
            InventoriesApi.class,
            InventoryUploadApi.class,
            ProductCountsApi.class,
            ProductsApi.class,
            StockApi.class,
            UsersApi.class
    };

    @Test
    public void allApiMethodsHaveValidRetrofitAnnotations() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost/")
                .addConverterFactory(GsonConverterFactory.create())
                .validateEagerly(true)
                .build();

        for (Class<?> api : APIS) {
            retrofit.create(api);
        }
    }
}
