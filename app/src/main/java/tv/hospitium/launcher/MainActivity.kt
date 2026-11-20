// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import tv.hospitium.launcher.protocol.EXTRA_WAKE
import tv.hospitium.launcher.ui.LauncherApp
import tv.hospitium.launcher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(vm) }
        if (savedInstanceState == null) noteWake(intent)
    }

    // singleTask: an agent starting the launcher while it runs arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteWake(intent)
    }

    private fun noteWake(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) vm.woke()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
