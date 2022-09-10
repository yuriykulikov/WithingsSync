package com.github.yuriykulikov.withingssync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.yuriykulikov.withingssync.ui.theme.WithingsSyncTheme
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

class MainActivity : ComponentActivity() {
  private val authService by lazy { AuthorizationService(this) }
  private val authIntent by lazy {
    authService.getAuthorizationRequestIntent(WithingsAccess.authorizationRequest)
  }
  private val withings: WithingsAccess = WithingsAccess()
  private var tokens: Tokens2? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      WithingsSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
          Column(
              Modifier.fillMaxHeight(),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            val scope = rememberCoroutineScope()
            val authLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = {
                      val intent = requireNotNull(it.data)
                      val resp = AuthorizationResponse.fromIntent(intent)
                      val ex = AuthorizationException.fromIntent(intent)
                      if (ex != null) {
                        Log.e("MainActivity", "AuthorizationException: $ex", ex)
                      } else {
                        val authorizationCode = requireNotNull(resp?.authorizationCode)
                        Log.v("MainActivity", "authorizationCode = $authorizationCode")
                        scope.launch {
                          tokens = withings.requestToken(authorizationCode)
                          Log.v("MainActivity", "Tokens = $tokens")
                        }
                      }
                    })

            Button(onClick = { authLauncher.launch(authIntent) }) {
              Text(text = "Connect with withings")
            }

            Button(
                onClick = {
                  tokens?.run {
                    scope.launch {
                      tokens = withings.refreshToken(refresh_token)
                      Log.v("MainActivity", "Tokens = $tokens")
                    }
                  }
                }) { Text(text = "Refresh token") }

            Button(
                onClick = {
                  tokens?.run {
                    scope.launch {
                      val measures = withings.getMeasures(access_token)
                      measures.forEach { Log.v("MainActivity", " -> $it") }
                    }
                  }
                }) { Text(text = "Download data") }
          }
        }
      }
    }
  }
}
