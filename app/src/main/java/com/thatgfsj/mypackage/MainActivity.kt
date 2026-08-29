package com.thatgfsj.mypackage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thatgfsj.mypackage.ui.MyPackageApp
import com.thatgfsj.mypackage.ui.theme.MyPackageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPackageTheme {
                MyPackageApp()
            }
        }
    }
}
