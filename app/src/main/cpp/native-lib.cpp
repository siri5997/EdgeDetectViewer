#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "native-lib", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "native-lib", __VA_ARGS__)

using namespace cv;

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgedetectviewer_NativeBridge_processFrameNV21(JNIEnv *env, jclass /*clazz*/,
                                                                jbyteArray nv21Array,
                                                                jint width, jint height) {
    if (nv21Array == nullptr) {
        LOGE("nv21Array is null");
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* nv21 = env->GetByteArrayElements(nv21Array, &isCopy);
    if (nv21 == nullptr) {
        LOGE("GetByteArrayElements returned null");
        return nullptr;
    }

    int yuvHeight = height + height / 2;
    try {
        Mat yuv(yuvHeight, width, CV_8UC1, reinterpret_cast<unsigned char*>(nv21));
        Mat bgr;
        cvtColor(yuv, bgr, COLOR_YUV2BGR_NV21);
        Mat gray;
        cvtColor(bgr, gray, COLOR_BGR2GRAY);
        Mat edges;
        Canny(gray, edges, 80, 200);
        Mat rgba;
        cvtColor(edges, rgba, COLOR_GRAY2RGBA);

        int outSize = static_cast<int>(rgba.total() * rgba.elemSize());
        jbyteArray outArray = env->NewByteArray(outSize);
        if (outArray == nullptr) {
            LOGE("Failed to allocate output jbyteArray");
            env->ReleaseByteArrayElements(nv21Array, nv21, 0);
            return nullptr;
        }
        env->SetByteArrayRegion(outArray, 0, outSize, reinterpret_cast<const jbyte*>(rgba.data));
        env->ReleaseByteArrayElements(nv21Array, nv21, 0);
        return outArray;
    } catch (const cv::Exception &e) {
        LOGE("OpenCV exception: %s", e.what());
        env->ReleaseByteArrayElements(nv21Array, nv21, 0);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in native processing");
        env->ReleaseByteArrayElements(nv21Array, nv21, 0);
        return nullptr;
    }
}
