package org.adlerzz.pree

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TableLayout
import org.adlerzz.pree.databinding.LayoutRuchkaBinding

/**
 * TODO: document your custom view class.
 */
class Ruchka : TableLayout {

    private lateinit var binding: LayoutRuchkaBinding
    private var text: String? = ""
    private var default: Double = 0.0

    var value: Double = 0.0
        private set

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.Ruchka, 0, 0
        )

        default = a.getFloat(R.styleable.Ruchka_defaultValue, 0.0f).toDouble()
        text = a.getString(R.styleable.Ruchka_labelText)


        // inflate binding and add as view
        binding = LayoutRuchkaBinding.inflate(LayoutInflater.from(context))
        addView(binding.root)

        binding.rLabel.text = relToStr(text, default)
        val max = relToAbs(a.getFloat(R.styleable.Ruchka_maxValue, 1.0f).toDouble())
        val min = relToAbs(a.getFloat(R.styleable.Ruchka_minValue, -1.0f).toDouble())
        val def = relToAbs(default)

        Log.d("Ruchka", "max: $max; min: $min; def: $def")

        binding.rInput.max = max - min
        binding.rInput.min = 0
        binding.rInput.progress = def - min

        binding.rInput.setOnSeekBarChangeListener( object : SeekBar.OnSeekBarChangeListener{

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = absToRel((seekBar?.progress ?: 0) + min)
                val ftext = relToStr(text, v)
                binding.rLabel.text = ftext
                this@Ruchka.value = v
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.rReset.setOnClickListener {
            binding.rInput.progress = relToAbs(default) - min
            binding.rLabel.text = relToStr(text, default)
            this.value = default
        }

        a.recycle()

    }

    companion object {
        fun absToRel(value: Int?): Double = value?.div(1000.0) ?: 0.0

        fun relToAbs(value: Double): Int = (value * 1000.0).toInt()

        fun relToStr(template: String?, value: Double): String = template?.format(String.format("%.3f", value)) ?: ""
    }


}