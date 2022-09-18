package com.github.yuriykulikov.withingssync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.HistoryClient
import com.google.android.gms.fitness.data.DataType

val fitnessOptions: FitnessOptions =
    FitnessOptions.builder()
        .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_BODY_FAT_PERCENTAGE, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_BODY_FAT_PERCENTAGE, FitnessOptions.ACCESS_WRITE)
        .build()

// TODO perhaps create a class wrapping the context for testability?
fun hasGooglePermission(context: Context): Boolean {
  return GoogleSignIn.hasPermissions(
      GoogleSignIn.getAccountForExtension(context, fitnessOptions), fitnessOptions)
}

fun googleSingInIntent(context: Context): Intent {
  return GoogleSignIn.getClient(
          context,
          GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
              .addExtension(fitnessOptions)
              .build())
      .signInIntent
}

fun createHistoryClient(context: Context): HistoryClient {
  return Fitness.getHistoryClient(
      context, GoogleSignIn.getAccountForExtension(context, fitnessOptions))
}
