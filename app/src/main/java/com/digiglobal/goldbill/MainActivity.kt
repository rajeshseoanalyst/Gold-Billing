package com.digiglobal.goldbill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.digiglobal.goldbill.ui.AppRoot
import com.digiglobal.goldbill.ui.theme.GoldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldTheme { AppRoot() } }
    }
}
