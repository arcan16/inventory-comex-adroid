package com.example.myapplication.network;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface InventoryUploadApi {
    @Multipart
    @POST("inventories/upload")
    Call<InventoryUploadResultDTO> upload(@Part MultipartBody.Part file);
}
