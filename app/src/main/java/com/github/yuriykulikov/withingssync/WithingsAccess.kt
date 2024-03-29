package com.github.yuriykulikov.withingssync

import android.net.Uri
import androidx.datastore.core.DataStore
import com.github.yuriykulikov.withingssync.common.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.math.BigInteger
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

data class WithingsMeasure(
    val date: Date,
    val weight: Int?,
    val fatRatio: Int?,
)

@Serializable
data class WithingsTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresOn: Long = 0,
)

/**
 * Access to Withings public API.
 *
 * TODO internalize tokens
 */
class WithingsAccess(
    val tokenStore: DataStore<WithingsTokens>,
    val logger: Logger,
) {
  companion object {
    /**
     * Authorisation request for Withings.
     *
     * [OAuth 2.0 - Get your authorization code](https://developer.withings.com/api-reference/#operation/oauth2-authorize)
     *
     * The authorization code is valid for 30 seconds
     */
    val authorizationRequest: AuthorizationRequest by lazy {
      AuthorizationRequest.Builder(
              AuthorizationServiceConfiguration(
                  Uri.parse("https://account.withings.com/oauth2_user/authorize2"),
                  Uri.parse("https://wbsapi.withings.net/v2/oauth2"),
              ),
              BuildConfig.CLIENT_ID,
              ResponseTypeValues.CODE,
              /* redirectUri */ Uri.parse("com.github.yuriykulikov.withingssync://oauth2redirect"))
          .setScope("user.metrics")
          .build()
    }
  }

  private val secret = BuildConfig.CLIENT_SECRET
  private val clientId = BuildConfig.CLIENT_ID
  private val redirectUrl = "com.github.yuriykulikov.withingssync://oauth2redirect"
  private val secretKeySpec = SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256")
  private val ktor =
      HttpClient(CIO) {
        install(ContentNegotiation) {
          json(
              Json {
                coerceInputValues = true
                ignoreUnknownKeys = true
              })
        }
      }

  /**
   * [Learn how to sign requests](https://developer.withings.com/developer-guide/v3/get-access/sign-your-requests/)
   */
  private fun List<String>.sign(): String {
    return Mac.getInstance("HmacSHA256")
        .apply { init(secretKeySpec) }
        .doFinal(joinToString(",").encodeToByteArray())
        .let { "%064x".format(BigInteger(1, it)) }
  }

  /**
   * [Signature v2 - Getnonce](https://developer.withings.com/api-reference#operation/signaturev2-getnonce)
   */
  suspend fun getNonce(): String {
    val timestamp = Instant.now().epochSecond.toString()
    return ktor
        .get("https://wbsapi.withings.net/v2/signature") {
          parameter("action", "getnonce")
          parameter("client_id", clientId)
          parameter("timestamp", timestamp)
          parameter("signature", listOf("getnonce", clientId, timestamp).sign())
        }
        .body<NonceResponse>()
        .body
        .nonce
  }

  /**
   * [OAuth 2.0 - Get your access_token](https://developer.withings.com/api-reference/#operation/oauth2-authorize)
   */
  suspend fun requestToken(authorizationCode: String) {
    tokenStore.updateData {
      ktor
          .submitForm(
              url = "https://wbsapi.withings.net/v2/oauth2",
              formParameters =
                  Parameters.build {
                    append("action", "requesttoken")
                    append("grant_type", "authorization_code")
                    append("client_id", clientId)
                    append("client_secret", secret)
                    append("code", authorizationCode)
                    append("redirect_uri", redirectUrl)
                  },
              encodeInQuery = false,
          )
          .body<RequestTokenResponse>()
          .body
          .let { tokens ->
            WithingsTokens(
                accessToken = tokens.access_token,
                refreshToken = tokens.refresh_token,
                expiresOn = Instant.now().epochSecond + tokens.expires_in,
            )
          }
    }
  }

  /**
   * [OAuth 2.0 - Refresh your access_token](https://developer.withings.com/api-reference/#operation/oauth2-refreshaccesstoken)
   */
  suspend fun refreshToken(): WithingsTokens {
    logger.debug { "Refreshing tokens" }
    return tokenStore.updateData { prev ->
      ktor
          .submitForm(
              url = "https://wbsapi.withings.net/v2/oauth2",
              formParameters =
                  Parameters.build {
                    append("action", "requesttoken")
                    append("grant_type", "refresh_token")
                    append("client_id", clientId)
                    append("client_secret", secret)
                    append("refresh_token", prev.refreshToken)
                  },
              encodeInQuery = false,
          )
          .body<RequestTokenResponse>()
          .body
          .let { tokens ->
            WithingsTokens(
                accessToken = tokens.access_token,
                refreshToken = tokens.refresh_token,
                expiresOn = Instant.now().epochSecond + tokens.expires_in,
            )
          }
    }
  }

  suspend fun getMeasures(lastupdate: Instant? = null): List<WithingsMeasure> {
    val accessToken = freshToken()
    return ktor
        .submitForm(
            url = "https://wbsapi.withings.net/measure",
            formParameters =
                Parameters.build {
                  append("action", "getmeas")
                  append("meastypes", "1,6")
                  append("category", "1")
                  append(
                      "startdate",
                      Calendar.getInstance()
                          .apply { set(Calendar.YEAR, 2020) }
                          .toInstant()
                          .epochSecond
                          .toString())
                  if (lastupdate != null && lastupdate.epochSecond > 0) {
                    append("lastupdate", lastupdate.epochSecond.toString())
                  }
                },
            encodeInQuery = false,
            block = { header("Authorization", "Bearer $accessToken") },
        )
        .body<WithingsMeasuresResponse>()
        .let { requireNotNull(it.body?.measuregrps) { "Error response: $it" } }
        .map { group ->
          WithingsMeasure(
              date = Date.from(Instant.ofEpochSecond(group.date)),
              weight = group.measures.firstOrNull { it.type == 1 }?.value,
              fatRatio = group.measures.firstOrNull { it.type == 6 }?.value,
          )
        }
  }

  private suspend fun freshToken(): String {
    val tokens = tokenStore.data.first()
    return if (Instant.ofEpochSecond(tokens.expiresOn).isBefore(Instant.now())) {
      refreshToken().accessToken
    } else {
      tokens.accessToken
    }
  }
}

@Serializable
private data class WithingsMeasuresResponse(
    val status: Int,
    val body: Body? = null,
    val error: String? = null,
) {

  @Serializable
  data class Body(
      val updatetime: Long,
      val timezone: String,
      val measuregrps: List<MeasureGroup>,
  )

  @Serializable
  data class MeasureGroup(
      val grpid: Long,
      val attrib: Int,
      val date: Long,
      val created: Long,
      val modified: Long,
      val category: Int,
      val deviceid: String? = null,
      val hash_deviceid: String? = null,
      val measures: List<Measure>,
      val comment: String? = null,
  )

  @Serializable
  data class Measure(
      val value: Int,
      val type: Int,
      val unit: Int,
      val algo: Int? = null,
      val fm: Int? = null,
  )
}

@Serializable
private data class RequestTokenResponse(
    val status: Int,
    val body: Body,
) {
  @Serializable
  data class Body(
      val userid: Long,
      val access_token: String,
      val refresh_token: String,
      val expires_in: Int,
      val scope: String,
      val csrf_token: String? = null,
      val token_type: String,
  )
}

@Serializable
private data class NonceResponse(
    val status: Int,
    val body: Body,
) {

  @Serializable
  data class Body(
      val nonce: String,
  )
}
