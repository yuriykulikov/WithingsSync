package com.github.yuriykulikov.withingssync

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.yuriykulikov.withingssync.common.BugReporter
import com.github.yuriykulikov.withingssync.common.Logger
import com.github.yuriykulikov.withingssync.common.LoggerFactory
import com.github.yuriykulikov.withingssync.ui.theme.WithingsSyncTheme
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
  private val authService by lazy { AuthorizationService(this) }
  private val authIntent by lazy {
    authService.getAuthorizationRequestIntent(WithingsAccess.authorizationRequest)
  }
  private val withings: WithingsAccess by inject()
  private val logger: Logger = get<LoggerFactory>().createLogger("MainActivity")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      WithingsSyncTheme {
        val scaffoldState = rememberScaffoldState()
        Scaffold(
            scaffoldState = scaffoldState,
            topBar = {
              TopAppBar(
                  title = { Text(stringResource(R.string.app_name)) },
              )
            },
            snackbarHost = { SnackbarHost(it) { data -> Snackbar(snackbarData = data) } },
        ) {
          Column(
              Modifier.fillMaxHeight().fillMaxWidth().padding(it),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            AppContent()
          }
        }
      }
    }
  }

  @Composable
  private fun AppContent() {
    val scope = rememberCoroutineScope()
    val hasPermission = remember { mutableStateOf(isActivityRecognitionPermissionApproved(this)) }
    val googleSignedIn = remember { mutableStateOf(hasGooglePermission(this)) }

    val authLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = {
              val intent = requireNotNull(it.data)
              val resp = AuthorizationResponse.fromIntent(intent)
              val ex = AuthorizationException.fromIntent(intent)
              if (ex != null) {
                logger.error(ex) { "AuthorizationException: $ex" }
              } else {
                val authorizationCode = requireNotNull(resp?.authorizationCode)
                scope.launch { withings.requestToken(authorizationCode) }
              }
            })

    Button(onClick = { authLauncher.launch(authIntent) }) { Text(text = "Connect with withings") }

    Button(onClick = { scope.launch { withings.refreshToken() } }) { Text(text = "Refresh token") }

    Button(
        onClick = {
          scope.launch {
            val measures = withings.getMeasures()
            measures.forEach { logger.debug { " -> $it" } }
          }
        }) { Text(text = "Download data") }

    val permLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { hasPermission.value = it })
    // TODO if (ActivityCompat.shouldShowRequestPermissionRationale(this,
    // Manifest.permission.ACTIVITY_RECOGNITION)) show snack
    Button(
        onClick = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
          }
        },
        enabled = !hasPermission.value,
    ) {
      Text(text = "Request permissions")
    }

    val googleSignInLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
          val connected = it.resultCode == Activity.RESULT_OK
          googleSignedIn.value = connected
        }

    Button(
        onClick = { googleSignInLauncher.launch(googleSingInIntent(this)) },
        enabled = hasPermission.value && !googleSignedIn.value) { Text(text = "Google sign in") }

    Button(
        onClick = { enqueueSyncJob(this) },
        enabled = hasPermission.value && googleSignedIn.value,
    ) {
      Text(text = "Sync!")
    }

    Button(
        onClick = { get<BugReporter>().sendUserReport() },
    ) {
      Text(text = "Send bugreport!")
    }
  }
}
