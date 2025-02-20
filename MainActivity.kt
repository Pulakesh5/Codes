import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var resultTextView: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        progressBar = findViewById(R.id.progressBar)
        resultTextView = findViewById(R.id.resultTextView)
        startButton = findViewById(R.id.startButton)

        startButton.setOnClickListener {
            startTranscoding()
        }
    }

    private fun startTranscoding() {
        startButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        resultTextView.text = "Starting..."

        // Run video processing in a background thread
        CoroutineScope(Dispatchers.IO).launch {
            val videoFile = File(filesDir, "4k_input.mp4")
            val benchmark = VideoCodecBenchmark(this@MainActivity, videoFile)

            // 1️⃣ DECODING
            withContext(Dispatchers.Main) { resultTextView.text = "Decoding video..." }
            val startTimeDecode = System.nanoTime()
            val rawFrames = benchmark.decodeVideo(videoFile.absolutePath)
            val decodeTime = (System.nanoTime() - startTimeDecode) / 1_000_000
            withContext(Dispatchers.Main) { resultTextView.text = "Decoding done! (${decodeTime}ms)" }

            // 2️⃣ RESIZING
            withContext(Dispatchers.Main) { resultTextView.text = "Resizing frames..." }
            val startTimeResize = System.nanoTime()
            val resizedFrames = benchmark.resizeFrames(rawFrames)
            val resizeTime = (System.nanoTime() - startTimeResize) / 1_000_000
            withContext(Dispatchers.Main) { resultTextView.text = "Resizing done! (${resizeTime}ms)" }

            // 3️⃣ ENCODING
            withContext(Dispatchers.Main) { resultTextView.text = "Encoding video..." }
            val startTimeEncode = System.nanoTime()
            val outputFile = benchmark.encodeVideo(resizedFrames)
            val encodeTime = (System.nanoTime() - startTimeEncode) / 1_000_000
            withContext(Dispatchers.Main) { resultTextView.text = "Encoding done! (${encodeTime}ms)" }

            // 4️⃣ FINAL RESULT
            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                startButton.isEnabled = true
                resultTextView.text = """
                    ✅ Transcoding Complete!
                    - Decode: ${decodeTime}ms
                    - Resize: ${resizeTime}ms
                    - Encode: ${encodeTime}ms
                    📂 Saved to: ${outputFile.absolutePath}
                """.trimIndent()
            }
        }
    }
}
