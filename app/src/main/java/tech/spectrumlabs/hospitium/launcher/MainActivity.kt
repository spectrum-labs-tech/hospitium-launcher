// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import tech.spectrumlabs.hospitium.launcher.ui.LauncherApp
import tech.spectrumlabs.hospitium.launcher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
