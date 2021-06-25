package ru.mirea.recognitionapp.adapter

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max


class HTTPRequest {

    private val executionDispatcherExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val executionContextExecutor: ExecutorService = Executors.newFixedThreadPool(
            max(Runtime.getRuntime().availableProcessors() * 2 - 1, 2)
    )

    private val isRunning: AtomicBoolean = AtomicBoolean(false)

    private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { (HttpLoggingInterceptor.Level.BODY) })
            .build()

    //TODO change to server url
    private val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("http://151.248.113.161")
            .validateEagerly(true)
            .client(client)
            .build()

    private val api: Api = retrofit.create(Api::class.java)

    private val queue: BlockingQueue<Bitmap> = LinkedBlockingQueue()

    fun offerToSendQueue(bitmap: Bitmap) {
        queue.offer(bitmap)
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            executionDispatcherExecutor.execute {
                while (isRunning.get()) {
                    try {
                        val bmp = queue.poll(5, TimeUnit.SECONDS)
                        Log.d("HTTPRequest", "bitmap polled successfully")
                        if (bmp != null) {
                            executionContextExecutor.execute {
                                try {
                                    val response = sendBase64(bmp)
                                    Log.d("HTTPRequest", "response code: ${response.code()}")
                                    Log.d("HTTPRequest", "response body: ${response.body()?.string()?.toString()}")
                                    Log.d("HTTPRequest", "response error body: ${response.errorBody()?.string()?.toString()}")
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
    }

    private fun sendOctetStream(bmp: Bitmap): Response<ResponseBody> = api.sendOctetStream("jpeg", bmp.toOctetStream())
            .execute()

    private fun sendMultipart(bmp: Bitmap): Response<ResponseBody> = api.sendMultipart("jpeg".toPart(), bmp.toPart())
            .execute()

    private fun sendBase64(bmp: Bitmap): Response<ResponseBody> = api.sendBase64(bmp.toBase64(), "${UUID.randomUUID()}.jpeg")
            .execute()

    private fun String.toPart(): RequestBody = RequestBody.create(MediaType.parse("text/plain"), this)

    private fun Bitmap.toOctetStream(): RequestBody {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, baos)
        return RequestBody.create(
                MediaType.parse("application/octet-stream"),
                baos.toByteArray()
        )
    }

    private fun Bitmap.toPart(): RequestBody {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, baos)
        return RequestBody.create(
                MediaType.parse("image/jpeg"),
                baos.toByteArray()
        )
    }

    private fun Bitmap.toBase64(): String {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    private interface Api {

        @POST("/some/endpoint")
        fun sendOctetStream(
                @Query("extension") extension: String,
                @Body octetStream: RequestBody
        ): Call<ResponseBody>

        @Multipart
        @POST("/some/endpoint")
        fun sendMultipart(
                @Part("extension") extension: RequestBody,
                @Part("image") image: RequestBody
        ): Call<ResponseBody>

        @POST("/test.php")
        @FormUrlEncoded
        fun sendBase64(@Field("image") imageBase64: String, @Field("name") name: String): Call<ResponseBody>
    }
}