package com.example.data.remote

import com.example.data.model.OkxApiResponse
import com.example.data.model.OkxFundingRateDto
import com.example.data.model.OkxOrderBookDto
import com.example.data.model.OkxTickerDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface OkxApiService {

    @GET("api/v5/market/tickers")
    suspend fun getMarketTickers(
        @Query("instType") instType: String = "SPOT"
    ): OkxApiResponse<OkxTickerDto>

    @GET("api/v5/market/ticker")
    suspend fun getSingleTicker(
        @Query("instId") instId: String
    ): OkxApiResponse<OkxTickerDto>

    @GET("api/v5/market/candles")
    suspend fun getMarketCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String = "1H",
        @Query("limit") limit: Int = 100
    ): OkxApiResponse<List<String>>

    @GET("api/v5/market/books")
    suspend fun getOrderBook(
        @Query("instId") instId: String,
        @Query("sz") size: Int = 50
    ): OkxApiResponse<OkxOrderBookDto>

    @GET("api/v5/public/funding-rate")
    suspend fun getFundingRate(
        @Query("instId") instId: String
    ): OkxApiResponse<OkxFundingRateDto>

    companion object {
        private const val PRIMARY_BASE_URL = "https://www.okx.com/"
        private val BACKUP_HOSTS = listOf("www.okx.com", "aws.okx.com")

        fun create(): OkxApiService {
            val hostFailoverInterceptor = Interceptor { chain ->
                val originalRequest = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "OKXKurdAIPro/2.0 Android")
                    .build()

                var lastException: IOException? = null
                for (host in BACKUP_HOSTS) {
                    val newUrl = originalRequest.url.newBuilder()
                        .host(host)
                        .build()
                    val req = originalRequest.newBuilder().url(newUrl).build()
                    try {
                        val response = chain.proceed(req)
                        if (response.isSuccessful) {
                            return@Interceptor response
                        } else {
                            response.close()
                        }
                    } catch (e: IOException) {
                        lastException = e
                    }
                }
                chain.proceed(originalRequest)
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS)
                .addInterceptor(hostFailoverInterceptor)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(PRIMARY_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OkxApiService::class.java)
        }
    }
}
