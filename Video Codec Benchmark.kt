import android.content.Context
import android.media.*
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

class VideoCodecBenchmark(private val context: Context, private val inputFile: File) {
    private val outputFilePath = "${Environment.getExternalStorageDirectory()}/output_1080p.mp4"
    
    fun benchmark() {
        val startTimeDecode = System.nanoTime()
        val rawFrames = decodeVideo(inputFile.absolutePath)
        val decodeTime = (System.nanoTime() - startTimeDecode) / 1_000_000
        
        val startTimeResize = System.nanoTime()
        val resizedFrames = resizeFrames(rawFrames, 1920, 1080)
        val resizeTime = (System.nanoTime() - startTimeResize) / 1_000_000
        
        val startTimeEncode = System.nanoTime()
        encodeVideo(resizedFrames, outputFilePath)
        val encodeTime = (System.nanoTime() - startTimeEncode) / 1_000_000
        
        Log.d("Benchmark", "Decoding: ${decodeTime}ms, Resizing: ${resizeTime}ms, Encoding: ${encodeTime}ms")
    }
    
    private fun decodeVideo(filePath: String): List<ByteBuffer> {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)
        
        val format = extractor.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        
        val frames = mutableListOf<ByteBuffer>()
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) break
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
            
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val frameData = ByteBuffer.allocate(outputBuffer.remaining())
                frameData.put(outputBuffer)
                frames.add(frameData)
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return frames
    }
    
    private fun resizeFrames(frames: List<ByteBuffer>, width: Int, height: Int): List<ByteBuffer> {
        val resizedFrames = mutableListOf<ByteBuffer>()
        for (frame in frames) {
            val resizedFrame = resizeWithOpenGLES(frame, width, height)
            resizedFrames.add(resizedFrame)
        }
        return resizedFrames
    }
    
    private fun resizeWithOpenGLES(frame: ByteBuffer, width: Int, height: Int): ByteBuffer {
        // OpenGL ES Texture Setup
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        
        // Upload frame data to OpenGL texture
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, frame
        )
        
        // Render to Framebuffer for Resizing
        val resizedBuffer = ByteBuffer.allocate(width * height * 4)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, resizedBuffer)
        
        GLES30.glDeleteTextures(1, textures, 0)
        return resizedBuffer
    }
    
    private fun encodeVideo(frames: List<ByteBuffer>, outputPath: String) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        
        val bufferInfo = MediaCodec.BufferInfo()
        val file = File(outputPath)
        val fos = file.outputStream()
        
        frames.forEach { frame ->
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)!!
                inputBuffer.put(frame)
                codec.queueInputBuffer(inputIndex, 0, frame.remaining(), 0, 0)
            }
            
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val outputData = ByteArray(bufferInfo.size)
                outputBuffer.get(outputData)
                fos.write(outputData)
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
        
        codec.stop()
        codec.release()
        fos.close()
    }
}

// Usage from MainActivity
// VideoCodecBenchmark(context, File(context.filesDir, "4k_input.mp4")).benchmark()
