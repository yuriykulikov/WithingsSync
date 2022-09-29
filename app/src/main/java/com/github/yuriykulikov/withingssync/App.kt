package com.github.yuriykulikov.withingssync

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.util.FileSize
import com.github.yuriykulikov.withingssync.common.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.slf4j.ILoggerFactory

fun Scope.logger(tag: String): Logger {
  return get<LoggerFactory>().createLogger(tag)
}

class App : Application() {
  override fun onCreate() {
    super.onCreate()

    startKoin {
      modules(loggerModule())
      modules(
          module {
            single { applicationContext }

            single {
              WithingsAccess(
                  tokenStore =
                      DataStoreFactory.create(
                          serializer = TokensSerializer,
                          produceFile = { dataStoreFile("withings_tokens") },
                      ),
                  logger = logger("WithingsAccess"),
              )
            }

            single { BugReporter(logger("BugReporter"), applicationContext) }

            single {
              GoogleFit(
                  historyClient = get(),
              )
            }

            single { createHistoryClient(applicationContext) }

            single {
              Syncer(
                  lastSyncStore =
                      DataStoreFactory.create(
                          serializer = LongSerializer,
                          produceFile = { dataStoreFile("last_sync_epoch_seconds") },
                      ),
                  googleFit = get(),
                  withingsAccess = get(),
                  logger = logger("Syncer"))
            }

            single(named("lastSeenNotification")) {
              DataStoreFactory.create(
                  serializer = LongSerializer,
                  produceFile = { dataStoreFile("lastSeenNotification") },
              )
            }
          })
    }

    get<BugReporter>().attachToMainThread(this)
  }
}

/**
 * Creates a module which exports a [LoggerFactory] to create loggers. These [Logger]s are backed by
 * a [RollingFileAppender] and a [LogcatAppender].
 */
fun loggerModule() = module {
  single<LoggerFactory> {
    object : LoggerFactory {
      val logback: ILoggerFactory = get()
      override fun createLogger(tag: String): Logger {
        return Logger(logback.getLogger(tag))
      }
    }
  }

  single<ILoggerFactory> {
    logback {
      val logDir = get<Context>().filesDir.absolutePath

      addAppender(LogcatAppender()) { //
        encoder = patternLayoutEncoder("[%thread] - %msg%n")
      }

      addAppender(RollingFileAppender(), async = true) {
        isAppend = true
        rollingPolicy = timeBasedRollingPolicy {
          fileNamePattern = "$logDir/rolling-%d{yyyy-MM-dd}.log"
          maxHistory = 3
          isCleanHistoryOnStart = true
          setTotalSizeCap(FileSize.valueOf("800KB"))
        }

        encoder =
            patternLayoutEncoder(
                "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n")
      }
    }
  }
}

object LongSerializer : Serializer<Long> {
  override val defaultValue: Long = 0

  override suspend fun readFrom(input: InputStream): Long = input.readPacketExact(8).readLong()

  override suspend fun writeTo(t: Long, output: OutputStream) {
    output.writePacket { writeLong(t) }
  }
}

@OptIn(ExperimentalSerializationApi::class)
object TokensSerializer : Serializer<WithingsTokens> {
  override val defaultValue: WithingsTokens = WithingsTokens("", "")

  override suspend fun readFrom(input: InputStream): WithingsTokens =
      Json.decodeFromStream(WithingsTokens.serializer(), input)

  override suspend fun writeTo(t: WithingsTokens, output: OutputStream) {
    Json.encodeToStream(WithingsTokens.serializer(), t, output)
  }
}
