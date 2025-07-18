//Файл Screens.kt

package com.yourname.mdlbapp

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object Mommy : Screen("mommy_screen")
    object Baby : Screen("baby_screen")
}