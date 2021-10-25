package org.adlerzz.pree

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.SeekBar
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
    var buffer = ShortArray(0) { 0 }
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

        bindText(R.id.tr_pitch, R.id.lab_pitch, R.string.pitch)
        bindText(R.id.tr_kamp, R.id.lab_kamp, R.string.kamp)
        bindText(R.id.tr_lim,  R.id.lab_lim, R.string.lim)
        bindText(R.id.tr_cut_l,  R.id.lab_cut_l, R.string.cut_l)
        bindText(R.id.tr_cut_h,  R.id.lab_cut_h, R.string.cut_h)
        bindText(R.id.tr_qu, R.id.lab_qu, R.string.qu)
    }


    private fun addLog(s: String) {
        this.logs = this.logs + s
        val tx = findViewById<TextView>(R.id.textView1)
        tx.text = this.logs
        tx.scrollTo(0, tx.height)
    }

    fun activate() {
        this.buffer = ShortArray(buffSize) {0}

        this.audioRecord = createAudioRecord(buffSize)
        this.audioRecord?.startRecording()

        this.audioTrack = createAudioTrack(buffSize)
        this.audioTrack?.play()

        val grph = findViewById<GraphView>(R.id.graph1)

        this.timer.schedule(object: TimerTask(){
            override fun run() {
                val self = this@MainActivity
                self.audioRecord?.read(self.buffer, 0, self.buffSize)

                val cutLow = getDoubleValue(findViewById(R.id.tr_cut_l))
                val cutHigh = getDoubleValue(findViewById(R.id.tr_cut_h))
                val kamp = getDoubleValue(findViewById(R.id.tr_kamp))
                val limit = getDoubleValue(findViewById(R.id.tr_lim))
                val pitch = getDoubleValue(findViewById(R.id.tr_pitch))
                val qu = getDoubleValue(findViewById(R.id.tr_qu))

                val dstr = Engine.distortion(self.buffer, kamp, limit)
                val frequencies = Engine.fft(dstr, self.cstSize)
                val cutted = Engine.cFrequenciesCut(frequencies, cutLow, cutHigh)
                val pitched = Engine.cPitch(cutted, pitch)
                val dat = Engine.ifft(pitched, self.buffSize)
                val qued = Engine.quantize(dat, qu)
                val fdat = Engine.getAbs(pitched)

                graphRender(grph, fdat)

                self.audioTrack?.write(qued, 0, self.buffSize)
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

    private fun bindText(seekBarId: Int, textViewId: Int, stringId: Int) {
        val bar = findViewById<SeekBar>(seekBarId)
        val txt = findViewById<TextView>(textViewId)
        val lab = getString(stringId, getStringValue(bar))
        bar.setOnSeekBarChangeListener( createSeekBarListener(txt, stringId))
        txt.text = lab
    }

    private fun createSeekBarListener(textView: TextView, stringId: Int): SeekBar.OnSeekBarChangeListener{
        return object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textView.text = getString(stringId, getStringValue(seekBar!!))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    fun getDoubleValue(seekbar: SeekBar): Double = seekbar.progress / 1000.0
    fun getStringValue(seekbar: SeekBar): String = String.format("%.3f", getDoubleValue(seekbar))

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