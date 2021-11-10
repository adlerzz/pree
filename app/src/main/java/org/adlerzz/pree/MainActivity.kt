package org.adlerzz.pree

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.*

const val SAMPLE_RATE = 22050
const val FRAME_DURATION = 185L

const val MAX = 4_000_000.0

class MainActivity : AppCompatActivity() {

    var audioRecord: AudioRecord? = null
    var audioTrack: AudioTrack? = null
    var logs: String = ""
    var timer: Timer = Timer()

    var buffSize = 0
    var cstSize = 0
    var inBuffer = ShortArray(0) { 0 }
    var outBuffer = ShortArray(0) { 0 }
    var displayedData = DoubleArray(0) { 0.0 }
    private var active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.buffSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        this.buffSize = Math.max(this.buffSize, Math.ceil(SAMPLE_RATE * FRAME_DURATION / 2000.0).toInt() * 2 )
        this.cstSize = Engine.r2u(this.buffSize)
        this.addLog("buff: ${this.buffSize};\n")
        this.addLog("c2st: ${this.cstSize};\n")

        findViewById<Button>(R.id.button).setOnClickListener {
            if (this.active) {
                (it as Button).text = getString(R.string.start)
                this.deactivate()
            } else {
                (it as Button).text = getString(R.string.stop)
                this.activate()
            }
        }

        findViewById<TextView>(R.id.textView1).movementMethod = ScrollingMovementMethod()

        graphSetup(R.id.graph1)
    }


    private fun addLog(s: String) {
        this.logs = this.logs + s
        val tx = findViewById<TextView>(R.id.textView1)
        tx.text = this.logs
        tx.scrollTo(0, tx.height)
    }

    private fun doTransforms() {
        val cutLow = findViewById<Ruchka>(R.id.r_cut_l).value
        val cutHigh = findViewById<Ruchka>(R.id.r_cut_h).value
        val kamp = findViewById<Ruchka>(R.id.r_kamp).value
        val limit = findViewById<Ruchka>(R.id.r_lim).value
        val pitch = findViewById<Ruchka>(R.id.r_pitch).value
        val qu = findViewById<Ruchka>(R.id.r_qu).value

        val dstr = Engine.distortion(this.inBuffer, kamp, limit)
        val frequencies = Engine.fft(dstr, this.cstSize)
        val cutted = Engine.cFrequenciesCut(frequencies, cutLow, cutHigh)
        val pitched = Engine.cPitch(cutted, pitch)
        val dat = Engine.ifft(pitched, this.buffSize)
        val qued = Engine.quantize(dat, qu)
        val fdat = Engine.getAbs(pitched)

        this.displayedData = DoubleArray(this.cstSize / 2) {i -> fdat[i]}
        this.outBuffer = ShortArray(this.buffSize) {i -> qued[i]}
    }

    fun activate() {
        this.inBuffer = ShortArray(buffSize) {0}

        this.audioRecord = createAudioRecord(buffSize)
        this.audioRecord?.startRecording()

        this.audioTrack = createAudioTrack(buffSize)
        this.audioTrack?.play()

        val grph = findViewById<GraphView>(R.id.graph1)

        this.timer.schedule(object: TimerTask(){
            override fun run() {
                val self = this@MainActivity
                self.audioRecord?.read(self.inBuffer, 0, self.buffSize)

                self.doTransforms() // f( inBuffer ) -> outBuffer, displayedData

                graphRender(grph, self.displayedData)
                self.audioTrack?.write(self.outBuffer, 0, self.buffSize)
            }
        }, 0, FRAME_DURATION)
        this.active = true
    }

    fun deactivate() {
        this.audioRecord?.stop()
        this.audioTrack?.stop()
        this.timer.cancel()
        this.timer = Timer()
        this.active = false
    }

    private fun graphSetup(graphId: Int) {
        val grph = findViewById<GraphView>(graphId)
        grph.viewport.isXAxisBoundsManual = true
        grph.viewport.isYAxisBoundsManual = true

        grph.gridLabelRenderer.isVerticalLabelsVisible = false

        this.addLog("MAX: ${MAX};\n")
        grph.viewport.setMaxY(MAX)
        grph.viewport.setMinY(0.0)
        grph.viewport.setMaxX(this.cstSize / 4.0)
        grph.viewport.setMinX(0.0)
    }

    private fun graphRender(graph: GraphView, data: DoubleArray) {
        val series = LineGraphSeries(
            data.mapIndexed{ i, v ->
                DataPoint(i.toDouble(), v)
            }.toTypedArray()
        )
        series.color = Color.BLUE

        graph.removeAllSeries()
        graph.addSeries(series)
    }

    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        val ch = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (ch != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 201)
            return null
        }

        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun createAudioTrack(bufferSize: Int): AudioTrack {
        return AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
    }

}