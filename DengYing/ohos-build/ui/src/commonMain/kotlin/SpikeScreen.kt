package com.dengying.player.ui.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dengying.player.platform.Log

/**
 * CMP→ohos spike 屏（docs/04 §16.40）。
 *
 * 只做一件事：证明 material3 + runtime 状态机能编到 ohos_x64 并在模拟器渲染。
 */
@Composable
fun SpikeScreen() {
    var taps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { Log.w("DY/Spike", "SpikeScreen composed, first composition ran") }
    Log.w("DY/Spike", "SpikeScreen recomposed taps=$taps")
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "DengYing CMP spike", style = MaterialTheme.typography.headlineSmall)
            Text(text = "taps = $taps")
            Button(onClick = { taps++ }) { Text("tap me") }
        }
    }
}
