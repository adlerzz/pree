package org.adlerzz.pree

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.TransformType
import kotlin.math.*

class Engine {
    companion object {
        private val ffto = FastFourierTransformer(DftNormalization.STANDARD)

        fun distortion(data: ShortArray, kamp: Double, limit: Double): ShortArray {
            return data.toList().map{
                val amp = it*kamp
                when {
                    (amp > limit * Short.MAX_VALUE) -> ( limit * Short.MAX_VALUE).toInt().toShort()
                    (amp < limit * Short.MIN_VALUE) -> ( limit * Short.MIN_VALUE).toInt().toShort()
                    else -> amp.toInt().toShort()
                }
            }.toShortArray()
        }

        fun cFrequenciesCut(data: List<Complex>,  cut_l: Double, cut_h: Double): List<Complex> {
            val s = data.size / 4
            return data.mapIndexed{ i, it ->

                if ((cut_l < cut_h) xor( (i < cut_l*s) xor (i > cut_h*s))){
                    it.multiply(0.001)
                } else {
                    it
                }
            }
        }

        fun cPitch(data: List<Complex>, pitch: Double): List<Complex> {
            val offset = (pitch * data.size / 2).toInt()

            return data.mapIndexed{ i, _ ->

                if ( (i + offset < 0) or (i + offset >= data.size)){
                    Complex.ZERO
                } else {
                    data[i + offset]
                }
            }
        }

        fun quantize(data: ShortArray, step: Double): ShortArray {
            if(step < 0.01) {
                return data
            }
            val s = step * Short.MAX_VALUE

            return data.toList().map {
                val d = (it / s).roundToInt()
                (d * s).toInt().toShort()
            }.toShortArray()
        }

        /**
         * Returns the first integer being power of 2 that less than given
         */
        fun r2d(x: Int): Int {
            return 2.0.pow(floor(log2(x * 1.0) )).toInt()
        }

        /**
         * Returns the first integer being power of 2 that more than given
         */
        fun r2u(x: Int): Int {
            return 2.0.pow(ceil(log2(x * 1.0) )).toInt()
        }

        fun getAbs(x: List<Complex>): DoubleArray {
            return x.map { i -> i.abs() }
                .subList(0, x.size / 2)
                .toDoubleArray()
        }

        private fun <T> expand(list: List<T>, newSize: Int, filler: T): List<T> {
            val l = list.toMutableList()
            l.addAll( List<T>(newSize - list.size){filler})
            return l.toList()
        }

        fun fft(x: ShortArray, exp: Int): List<Complex> {
            val x0 = expand(x.toList(), exp, 0)
                .map { i -> i.toDouble() }
                .toDoubleArray()
            return ffto.transform(x0, TransformType.FORWARD)
                .toList()
        }

        fun ifft(x: List<Complex>, trc: Int): ShortArray {
            return ffto.transform(x.toTypedArray(), TransformType.INVERSE)
                .toList()
                .subList(0, trc)
                .map{ i: Complex -> i.real.toInt().toShort()}
                .toShortArray()
        }

    }

}