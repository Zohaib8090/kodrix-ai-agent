package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Request URLs can carry credentials, so only log them in debug builds.
    private val httpLogLevel: HttpLoggingInterceptor.Level
        get() = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createGitHubService(tokenProvider: () -> String): GitHubApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenProvider()
            val requestBuilder = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = httpLogLevel
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApiService::class.java)
    }

    fun createGitHubOAuthApi(): GitHubOAuthApi {
        val okHttpClient = createGenericOkHttpClient(20)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(GitHubOAuthApi::class.java)
    }

    fun createGenericOkHttpClient(timeoutSeconds: Long = 10): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = httpLogLevel
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Shared client for AI provider calls. Generating a whole project can take several minutes,
     * so reads get a long timeout; sharing one instance reuses its connection pool and threads
     * instead of creating a new client for every request.
     */
    val aiHttpClient: OkHttpClient by lazy { createAiHttpClient(AI_READ_TIMEOUT_SECONDS) }

    private const val AI_READ_TIMEOUT_SECONDS = 300L

    fun createAiHttpClient(timeoutSeconds: Long = 60): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = httpLogLevel
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
