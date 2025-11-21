package com.haishinkit.codec

import androidx.core.util.Pools
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

internal class AudioCodecBuffer {
    var sampleRate: Int = 48000
    var presentationTimestamp: Long = DEFAULT_PRESENTATION_TIMESTAMP
        private set
    private var pool = Pools.SynchronizedPool<ByteBuffer>(CAPACITY * 2)
    private var buffers = LinkedBlockingDeque<ByteBuffer>(CAPACITY)
    private var currentBuffer: ByteBuffer? = null

    fun append(byteBuffer: ByteBuffer) {
        val buffer = pool.acquire() ?: ByteBuffer.allocateDirect(byteBuffer.capacity())
        buffer.rewind()
        buffer.put(byteBuffer)
        if (buffers.size < CAPACITY) {
            buffers.add(buffer)
        } else {
            buffers.pop()
            buffers.add(buffer)
        }
    }

    private fun timestamp(sampleCount: Int): Long {
        return ((sampleCount.toFloat() * 1000000f) / sampleRate.toFloat()).toLong()
    }

    fun render(byteBuffer: ByteBuffer): Int {
        if (buffers.isEmpty() && currentBuffer == null) {
            return 0
        }

        if (currentBuffer == null) {
            currentBuffer = buffers.take()
            currentBuffer?.rewind()
        }

        return currentBuffer?.let { source ->
            val length = minOf(byteBuffer.capacity(), source.remaining())

            source.limit(source.position() + length)
            byteBuffer.put(source)
            source.limit(source.capacity())

            if (!source.hasRemaining()) {
                pool.release(source)
                currentBuffer = null
            }

            if (presentationTimestamp == DEFAULT_PRESENTATION_TIMESTAMP) {
                presentationTimestamp = System.nanoTime() / 1000
            } else {
                presentationTimestamp += timestamp(length / 2)
            }

            return length
        } ?: 0
    }

    fun clear() {
        buffers.clear()
        presentationTimestamp = DEFAULT_PRESENTATION_TIMESTAMP
    }

    companion object {
        const val CAPACITY = 4
        const val DEFAULT_PRESENTATION_TIMESTAMP = 0L
    }
}