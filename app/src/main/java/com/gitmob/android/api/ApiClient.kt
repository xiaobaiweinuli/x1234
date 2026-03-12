package com.gitmob.android.api

import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.util.LogManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://api.github.com/"
    private const val TAG = "ApiClient"
    private lateinit var tokenStorage: TokenStorage
    private var _api: GitHubApi? = null

    val api: GitHubApi get() = _api ?: error("ApiClient not initialized")

    /** 全局 401/Token 失效事件——任何地方收到 401 都会 emit true */
    private val _tokenExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tokenExpired: SharedFlow<Unit> = _tokenExpired

    fun init(storage: TokenStorage) {
        tokenStorage = storage
        rebuild()
    }

    fun rebuild() {
        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { tokenStorage.accessToken.first() }
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()
            val response = chain.proceed(request)

            // 401 = Token 失效（OAuth App 管理员撤销 / token 过期）
            if (response.code == 401) {
                LogManager.w(TAG, "收到 401，token 已失效，清除本地授权并触发重新登录")
                runBlocking { tokenStorage.clear() }
                _tokenExpired.tryEmit(Unit)
            }
            response
        }

        val logging = HttpLoggingInterceptor { msg -> LogManager.v(TAG, msg) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        _api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }
}
