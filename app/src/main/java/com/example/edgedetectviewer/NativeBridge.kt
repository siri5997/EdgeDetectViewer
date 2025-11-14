package com.example.edgedetectviewer

object NativeBridge {
    init { System.loadLibrary("native-lib") }
    external fun processFrameNV21(nv21: ByteArray, width: Int, height: Int): ByteArray?
}
