package com.example.data.remote

import com.example.data.model.OkxApiResponse
import com.example.data.model.OkxFundingRateDto
import com.example.data.model.OkxOrderBookDto
import com.example.data.model.OkxTickerDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
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
        private const val BASE_URL = "https://www.okx.com/"

        fun create(): OkxApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("User-Agent", "OKXKurdAIPro/1.0 Android")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OkxApiService::class.java)
        }
    }
}
