package com.example.myapplication.network;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Respaldo de todos los datos del backend (DataTransferController). La app no
 * interpreta el archivo: lo descarga y lo sube tal cual (ver BackupFileInfo).
 */
public interface DataApi {
    @GET("data/export")
    Call<ResponseBody> exportData();

    /** Combina el respaldo con los datos del servidor; nunca borra. */
    @POST("data/import")
    Call<ImportResultDTO> importData(@Body RequestBody backupJson);
}
