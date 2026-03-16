package com.gitmob.android.api

import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.util.LogManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object ApiClient {

    private const val BASE_URL = "https://api.github.com/"
    private const val TAG = "ApiClient"
    private lateinit var tokenStorage: TokenStorage
    private var _api: GitHubApi? = null
    private var _okHttpClient: OkHttpClient? = null

    val api: GitHubApi get() = _api ?: error("ApiClient not initialized")

    /** 全局 401/Token 失效事件——任何地方收到 401 都会 emit true */
    private val _tokenExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tokenExpired: SharedFlow<Unit> = _tokenExpired

    fun init(storage: TokenStorage) {
        tokenStorage = storage
        rebuild()
    }

    /**
     * 清理连接池，在网络切换时调用
     */
    fun clearConnectionPool() {
        _okHttpClient?.connectionPool?.evictAll()
        LogManager.i(TAG, "连接池已清理")
    }

    /**
     * 自定义重试拦截器
     * 自动重试网络连接失败的请求（最多3次）
     */
    private class RetryInterceptor : Interceptor {
        companion object {
            private const val MAX_RETRIES = 3
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response: Response? = null
            var exception: IOException? = null
            var retryCount = 0

            while (retryCount < MAX_RETRIES) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return response
                    }
                    // 如果是服务器错误（5xx），可以重试
                    if (response.code >= 500) {
                        response.close()
                        retryCount++
                        LogManager.w(TAG, "服务器错误 ${response.code}，正在重试 ($retryCount/$MAX_RETRIES)")
                        Thread.sleep(1000L * retryCount) // 指数退避
                        continue
                    }
                    return response
                } catch (e: IOException) {
                    exception = e
                    // 只重试网络相关的异常
                    if (e is SocketTimeoutException || 
                        e is SSLException || 
                        e.message?.contains("Connection reset") == true ||
                        e.message?.contains("Connection closed") == true ||
                        e.message?.contains("Required SETTINGS preface not received") == true) {
                        retryCount++
                        LogManager.w(TAG, "网络异常：${e.message}，正在重试 ($retryCount/$MAX_RETRIES)")
                        if (retryCount < MAX_RETRIES) {
                            Thread.sleep(1000L * retryCount) // 指数退避
                            continue
                        }
                    }
                    break
                }
            }

            // 如果重试完还是失败，抛出最后一个异常
            if (exception != null) {
                throw exception
            }
            return response!!
        }
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

        _okHttpClient = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

        _api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(_okHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }
}
