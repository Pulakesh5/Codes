import android.media.*
import android.opengl.*
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceTexture
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.Queue

class MainActivity : AppCompatActivity() {
    private val TAG = "BenchmarkApp"
    private val MIME_TYPE = "video/avc" // H.264 codec
    private val WIDTH = 3840 // 4K width
    private val HEIGHT = 2160 // 4K height
    private val BITRATE = 6000000 // Bitrate for encoding
    private val FRAME_RATE = 60 // 60fps
    private val IFRAME_INTERVAL = 1 // Interval for keyframes

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val exportButton: Button = findViewById(R.id.export_button)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)

        exportButton.setOnClickListener {
            benchmarkVideoProcessing()
        }
    }

    private fun benchmarkVideoProcessing() {
        try {
            Log.d(TAG, "Starting benchmark...")
            val overallStartTime = System.currentTimeMillis()
            progressBar.visibility = ProgressBar.VISIBLE
            progressText.text = "Progress: 0%"

            val videoFrames = generateAlphabetFrames()

            val surfaceTexture = SurfaceTexture(0)
            val decodedSurface = Surface(surfaceTexture)
            val encoderSurface = createEncoderSurface()

            val decodeStartTime = System.currentTimeMillis()
            processVideo(videoFrames, decodedSurface, encoderSurface)
            val decodeEndTime = System.currentTimeMillis()
            Log.d(TAG, "Total Decoding Time: ${decodeEndTime - decodeStartTime} ms")

            validateEncodedFrames(encoderSurface)

            val overallEndTime = System.currentTimeMillis()
            Log.d(TAG, "Total Benchmark Time: ${overallEndTime - overallStartTime} ms")

            progressBar.progress = 100
            progressText.text = "Progress: 100% - Completed"
        } catch (e: Exception) {
            Log.e(TAG, "Error during benchmarking", e)
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            progressBar.visibility = ProgressBar.GONE
            progressText.text = "Error occurred during processing"
        }
    }

    private fun processVideo(videoFrames: List<ByteBuffer>, decodedSurface: Surface, encoderSurface: Surface) {
        try {
            val decoder = MediaCodec.createDecoderByType(MIME_TYPE)
            val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            val format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT)
            val encodedFormat = MediaFormat.createVideoFormat(MIME_TYPE, 1920, 1080)

            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            encodedFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            encodedFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
            encodedFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            encodedFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

            decoder.configure(format, decodedSurface, null, 0)
            encoder.configure(encodedFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val frameQueue: Queue<ByteBuffer> = LinkedList(videoFrames)
            val bufferInfo = MediaCodec.BufferInfo()
            decoder.start()
            encoder.start()

            val downscaleStartTime = System.currentTimeMillis()
            GLES20.glViewport(0, 0, 1920, 1080)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            EGLExt.eglPresentationTimeANDROID(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), System.nanoTime())
            val downscaleEndTime = System.currentTimeMillis()
            Log.d(TAG, "Total Downscaling Time: ${downscaleEndTime - downscaleStartTime} ms")

            val encodeStartTime = System.currentTimeMillis()
            var isEncoding = true
            while (isEncoding) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEncoding = false
                }
            }
            val encodeEndTime = System.currentTimeMillis()
            Log.d(TAG, "Total Encoding Time: ${encodeEndTime - encodeStartTime} ms")

            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during processing", e)
            Toast.makeText(this, "Processing Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            progressBar.visibility = ProgressBar.GONE
            progressText.text = "Error occurred during processing"
        }
    }

    private fun validateEncodedFrames(encoderSurface: Surface) {
        Log.d(TAG, "Validating encoded frames...")
        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
        while (outputIndex >= 0) {
            val encodedData = encoder.getOutputBuffer(outputIndex)
            encodedData?.let {
                if (it.hasRemaining()) {
                    val firstByte = it.get(0)
                    if (firstByte in 65..90) { // ASCII values for A-Z
                        Log.d(TAG, "Valid Encoded Frame: ${firstByte.toChar()}")
                    } else {
                        Log.w(TAG, "Invalid Encoded Frame Byte: $firstByte")
                    }
                }
            }
            encoder.releaseOutputBuffer(outputIndex, false)
            outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
        }
    }
}
