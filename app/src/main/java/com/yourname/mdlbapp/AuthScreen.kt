//файл AuthScreen.kt

package com.yourname.mdlbapp

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

@Composable
fun AuthScreen(
    navController: NavHostController,
    preselectedRole: String
) {
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf(preselectedRole) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(context, result, selectedRole, navController)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Подтверди вход как $selectedRole", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            val signInClient = getGoogleSignInClient(context)

            signInClient.signOut().addOnCompleteListener {
                val intent = signInClient.signInIntent
                launcher.launch(intent)
            }
        }) {
            Text("Войти через Google как $selectedRole")
        }
    }
}

fun getGoogleSignInClient(context: Context): GoogleSignInClient {
    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("447458623377-1q3tl6v3mejtuspevb2pbjns376dpr3a.apps.googleusercontent.com")
        .requestEmail()
        .build()
    return GoogleSignIn.getClient(context, options)
}

fun handleSignInResult(
    context: Context,
    result: ActivityResult,
    selectedRole: String?,
    navController: NavHostController
) {
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    try {
        val account = task.getResult(ApiException::class.java)
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { authResult ->
                if (authResult.isSuccessful && selectedRole != null) {
                    val user = authResult.result?.user
                    if (user != null) {
                        saveUserToFirestore(user.uid, selectedRole)
                        checkPairingAndNavigate(user.uid, selectedRole, navController)
                    }
                } else {
                    Toast.makeText(context, "Ошибка входа", Toast.LENGTH_SHORT).show()
                }
            }
    } catch (e: Exception) {
        Log.e("AuthError", "Google sign-in failed", e)
        Toast.makeText(context, "Ошибка входа через Google", Toast.LENGTH_SHORT).show()
    }
}

fun checkPairingAndNavigate(
    uid: String,
    role: String,
    navController: NavHostController
) {
    val db = Firebase.firestore
    db.collection("users").document(uid).get()
        .addOnSuccessListener { document ->
            val pairedWith = document.getString("pairedWith")
            if (pairedWith.isNullOrEmpty()) {
                if (role == "Mommy") {
                    navController.navigate("pair_mommy")
                } else {
                    navController.navigate("pair_baby")
                }
            } else {
                if (role == "Mommy") {
                    navController.navigate(Screen.Mommy.route)
                } else {
                    navController.navigate(Screen.Baby.route)
                }
            }
        }
}

fun saveUserToFirestore(uid: String, role: String) {
    val db = Firebase.firestore
    val data = mapOf(
        "role"       to role,
        "pairedWith" to null,
        "timezone"   to ZoneId.systemDefault().id
    )
    db.collection("users").document(uid).set(data)
}