package com.example.soundmeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import com.example.soundmeter.ui.theme.SoundMeterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                    SoundMeterScreen()
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SoundMeterTheme {
        Greeting("Android")
    }
}


//
//Use the microphone to measure sound levels and display a visual sound meter.
//•	Convert the audio amplitude to decibel (dB) values.
//•	Display a visual indicator (e.g., a progress bar or a colored sound level meter).
//•	Alert the user if the noise level exceeds a threshold.
//
//HINT: Use AudioRecord

// viewmodel for recorder logic?
class SoundMeterViewModel : ViewModel() {

    // function to start recording and continuously lsiten to audio
    @Suppress("MissingPermission") // because we do get permission manually
    fun start(
        context : Context,
        onAmplitude : (Float) -> Unit
    ) : AudioRecord? {

        // check permissions here too...
        val granted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            return null
        }


        val SAMPLE_RATE = 44100 //8000 // for emulator?
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val buffer = ShortArray(bufferSize)

        // thread to listen continuously so its not jsut recording but like constant
        Thread {
            recorder.startRecording()
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { // hwile recording
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) { // if there's content
                    // amplitude
                    val amplitude = kotlin.math.sqrt(
                        buffer.take(read).map {
                            it.toDouble() * it.toDouble()
                        }.average()
                    ).toFloat()

                    onAmplitude(amplitude) // onAmplitude passed in
                }
            }
        }.start()

        return recorder
    }

    // convert amplitude to decibel
    fun ampToDb(amp : Float) : Float {
        if (amp <= 0f) {
            return 0f
        } else {
            return 20f * kotlin.math.log10(amp.toDouble()).toFloat() + 10f
        }
    }
}


// actual ui/screen for sound meter
@Composable
fun SoundMeterScreen() {
    var db by rememberSaveable { mutableStateOf(0f) }
    var recorder by remember { mutableStateOf<AudioRecord?>(null) }
    val viewModel = SoundMeterViewModel()
    var hasPermission by rememberSaveable { mutableStateOf(false) }

    // data flow
    val context = LocalContext.current

    // get permission (mediarecorder needs permission?)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(true) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            hasPermission = true
        }
    }

    // now we can start recording
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            recorder = viewModel.start(context) {
                db = viewModel.ampToDb(it)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.stop()
            recorder?.release()
        }
    }

    // actual ui
    val normalized = (db / 100f).coerceIn(0f, 1f)// for progress bar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text("sound meter")
        Text("decibels: $db")
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(
            progress = normalized,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            color = if (db > 90f) {Color.Red} else {Color.Green}
        )
        Text(text = if (db > 90f) {"too loud!"} else {""})
    }
}