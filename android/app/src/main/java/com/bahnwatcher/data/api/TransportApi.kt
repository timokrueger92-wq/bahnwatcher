package com.bahnwatcher.data.api

import com.bahnwatcher.data.model.Departure
import com.bahnwatcher.data.model.JourneysResponse
import com.bahnwatcher.data.model.NearbyStop
import com.bahnwatcher.data.model.StopLocation
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

interface TransportApiService {
    @GET("locations")
    suspend fun searchLocations(
        @Query("query") query: String,
        @Query("results") results: Int = 10
    ): List<StopLocation>

    @GET("journeys")
    suspend fun getJourneys(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("departure") departure: String? = null,
        @Query("arrival") arrival: String? = null,
        @Query("results") results: Int = 5,
        @Query("stopovers") stopovers: Boolean = false,
        // Product filters – passed as flat boolean params (transport.rest v6 style)
        @Query("nationalExpress") nationalExpress: Boolean? = null,
        @Query("national") national: Boolean? = null
    ): JourneysResponse

    @GET("stops/{id}/departures")
    suspend fun getDepartures(
        @Path("id") stopId: String,
        @Query("when") `when`: String? = null,
        @Query("duration") duration: Int = 60,
        @Query("results") results: Int = 20
    ): DeparturesWrapper

    @GET("stops/nearby")
    suspend fun getNearbyStops(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("results") results: Int = 10,
        @Query("distance") distance: Int = 1500
    ): List<NearbyStop>
}

// The v6 API returns departures as a top-level object with a "departures" key
data class DeparturesWrapper(
    val departures: List<Departure>?
)

object TransportApiClient {
    private const val BASE_URL = "https://v6.db.transport.rest/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: TransportApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TransportApiService::class.java)
}

