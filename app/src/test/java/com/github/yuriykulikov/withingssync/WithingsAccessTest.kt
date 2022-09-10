package com.github.yuriykulikov.withingssync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

class WithingsAccessTest {
  private val withingsAccess = WithingsAccess()

  @Test
  fun `get nonce from withings`() =
      runBlocking<Unit> {
        withTimeout(1500) {
          val nonce = withingsAccess.getNonce()
          println(nonce)
        }
      }

  @Test
  fun `refresh token`() =
      runBlocking<Unit> {
        withTimeout(2500) {
          println(withingsAccess.refreshToken("383def910d379255bf92824ca6586f986d34b399"))
        }
      }

  @Test
  fun `Download data`() =
      runBlocking<Unit> {
        withTimeout(2500) {
          val accessToken =
              withingsAccess.refreshToken("383def910d379255bf92824ca6586f986d34b399").access_token
          println(withingsAccess.getMeasures(accessToken))
        }
      }
}
