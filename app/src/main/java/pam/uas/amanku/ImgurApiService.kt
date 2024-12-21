package pam.uas.amanku

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ImgurApiService {
    @Multipart
    @POST("3/image")
    @Headers("Authorization: Client-ID 648b5a18e8748f1")
    fun uploadImage(
        @Part image: MultipartBody.Part
    ): Call<ResponseBody>
}