/*
 * https://stackoverflow.com/a/60633395/461982
 * Copyright (c) 2024 AbandonedCart.  All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ani.himitsu.framecapture

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import bit.himitsu.content.toPx
import java.lang.RuntimeException

class AV_FrameCapture {
    private val mGLThread: HandlerThread = HandlerThread("AV_FrameCapture")
    private val mGLHandler: Handler = Handler(mGLThread.looper)
    private val mGLHelper: AV_GLHelper = AV_GLHelper()

    private val mDefaultTextureID = 10001

    private var mWidth = 176.toPx
    private var mHeight = 96.toPx

    private var mPath: String? = null

    fun setDataSource(path: String?) {
        mPath = path
    }

    fun setTargetSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
    }

    fun init() {
        mGLHandler.post(object : Runnable {
            override fun run() {
                val st = SurfaceTexture(mDefaultTextureID)
                st.setDefaultBufferSize(mWidth, mHeight)
                mGLHelper.init(st)
            }
        })
    }

    fun release() {
        mGLHandler.post(object : Runnable {
            override fun run() {
                mGLHelper.release()
                mGLThread.quit()
            }
        })
    }

    private val mWaitBitmap = Any()
    private var mBitmap: Bitmap? = null

    init {
        mGLThread.start()
    }

    fun getFrameAtTime(frameTime: Long): Bitmap? {
        if (null == mPath || mPath!!.isEmpty()) {
            throw RuntimeException("Illegal State")
        }

        mGLHandler.post(Runnable { getFrameAtTimeImpl(frameTime) })

        synchronized(mWaitBitmap) {
            try {
                (mWaitBitmap as Object).wait()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        return mBitmap
    }

    @SuppressLint("SdCardPath")
    fun getFrameAtTimeImpl(frameTime: Long) {
        val textureID = mGLHelper.createOESTexture()
        val st = SurfaceTexture(textureID)
        val surface = Surface(st)
        val vd = AV_VideoDecoder(mPath!!, surface)
        st.setOnFrameAvailableListener(object : OnFrameAvailableListener {
            override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                Log.i(TAG, "onFrameAvailable")
                mGLHelper.drawFrame(st, textureID)
                mBitmap = mGLHelper.readPixels(mWidth, mHeight)
                synchronized(mWaitBitmap) {
                    (mWaitBitmap as Object).notify()
                }

                vd.release()
                st.release()
                surface.release()
            }
        })

        if (!vd.prepare(frameTime)) {
            mBitmap = null
            synchronized(mWaitBitmap) {
                (mWaitBitmap as Object).notify()
            }
        }
    }

    companion object {
        const val TAG: String = "AV_FrameCapture"
    }
}