#include "GpuDepthCapture.h"
#include "VerifiedRendererHook.h"

#include <android/log.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <cstdio>
#include <string>
#include <memory>
#include <mutex>
#include <thread>

namespace {
constexpr char kTag[] = "FreeformUnbounded";
JavaVM* vm = nullptr;
jclass bridge = nullptr;
jmethodID frameMethod = nullptr;
jmethodID beforeSwapMethod = nullptr;
std::mutex mutex;
std::condition_variable condition;
std::once_flag worker;
struct Frame {
    int width, height;
    int64_t capturedNanos;
    std::vector<uint8_t> rawDepth;
};
std::unique_ptr<Frame> pending;

void publish(Frame& frame) {
    const size_t stride = (static_cast<size_t>(frame.width) + 3) & ~size_t(3);
    const size_t count = static_cast<size_t>(frame.width) * frame.height;
    if (!count || frame.rawDepth.size() != stride * frame.height) return;
    std::vector<uint8_t> alpha(count);
    for (int y = 0; y < frame.height; ++y) {
        std::memcpy(alpha.data() + static_cast<size_t>(y) * frame.width,
            frame.rawDepth.data() + static_cast<size_t>(frame.height - 1 - y) * stride, frame.width);
    }
    const size_t selected = std::count(alpha.begin(), alpha.end(), uint8_t(255));
    // Preserve the existing foreground-coverage rejection at full resolution.
    if (!selected || selected * 100 >= count * 95) return;
    // A root-enabled diagnostic captures the actual alpha, never wallpaper color.
    static bool probeSaved = false;
    char probe[PROP_VALUE_MAX] = {};
    __system_property_get("debug.freeform.depth_probe", probe);
    if (!probeSaved && probe[0] == '/') {
        auto save = [&](const char* suffix, const void* data, size_t size) {
            FILE* file = std::fopen((std::string(probe) + suffix).c_str(), "wb");
            if (!file) return false;
            const bool ok = std::fwrite(data, 1, size, file) == size;
            return std::fclose(file) == 0 && ok;
        };
        const int dimensions[] = {frame.width, frame.height};
        probeSaved = save(".alpha", alpha.data(), alpha.size()) && save(".size", dimensions, sizeof(dimensions));
        __android_log_print(ANDROID_LOG_INFO, kTag, "CI87 alpha probe saved=%d prefix=%s", probeSaved, probe);
    }
    static unsigned frames = 0;
    const unsigned sample = ++frames;
    if (sample <= 5 || sample % 90 == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "CI87 GPU mask frame=%u size=%dx%d selected=%zu/%zu ageUs=%lld",
            sample, frame.width, frame.height, selected, count,
            static_cast<long long>((std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count() - frame.capturedNanos) / 1000));
    }
    JNIEnv* env = nullptr;
    bool attached = false;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jobject depthBuffer = env->NewDirectByteBuffer(alpha.data(), alpha.size());
    if (depthBuffer && bridge && frameMethod) {
        env->CallStaticBooleanMethod(bridge, frameMethod, frame.width, frame.height,
            static_cast<jlong>(frame.capturedNanos), depthBuffer);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (depthBuffer) env->DeleteLocalRef(depthBuffer);
    if (attached) vm->DetachCurrentThread();
}

void queueFrame(int width, int height, int64_t capturedNanos, std::vector<uint8_t>&& depth) {
    auto frame = std::make_unique<Frame>(Frame{width, height, capturedNanos, std::move(depth)});
    {
        std::lock_guard<std::mutex> lock(mutex);
        pending = std::move(frame);
    }
    condition.notify_one();
}

void swap(const char*, EGLDisplay, EGLSurface) {
    gpu_depth::atSwap();
    JNIEnv* env = nullptr;
    bool attached = false;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    env->CallStaticVoidMethod(bridge, beforeSwapMethod, static_cast<jlong>(gpu_depth::takeSwapCapture()));
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
    if (attached) vm->DetachCurrentThread();
}

jboolean install(JNIEnv*, jclass) {
    std::call_once(worker, [] {
        gpu_depth::initialize(queueFrame);
        std::thread([] {
            JNIEnv* env = nullptr;
            if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            for (;;) {
                std::unique_ptr<Frame> frame;
                {
                    std::unique_lock<std::mutex> lock(mutex);
                    condition.wait(lock, [] { return pending != nullptr; });
                    frame = std::move(pending);
                }
                publish(*frame);
            }
        }).detach();
    });
    const bool installed = verified_renderer::install(swap, gpu_depth::beforeDepthDiscard);
    gpu_depth::setEnabled(installed);
    __android_log_print(ANDROID_LOG_INFO, kTag, "CI62 verified pre-discard capture installed=%d", installed);
    return installed ? JNI_TRUE : JNI_FALSE;
}

// 保留现有 Java ABI；不再修改或 hook loader，也不保留失效的 fallback。
jboolean loaderProbe(JNIEnv*, jclass) { return JNI_FALSE; }
void setEnabled(JNIEnv*, jclass, jboolean enabled) { gpu_depth::setEnabled(enabled == JNI_TRUE); }
void reset(JNIEnv*, jclass) { gpu_depth::requestReset(); }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* javaVm, void*) {
    vm = javaVm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("com/freeform/unbounded/NativeDepthBridge");
    if (!local) return JNI_ERR;
    bridge = reinterpret_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    frameMethod = env->GetStaticMethodID(bridge, "onNativeClockMaskFrame",
        "(IIJLjava/nio/ByteBuffer;)Z");
    beforeSwapMethod = env->GetStaticMethodID(bridge, "onNativeBeforeSwap", "(J)V");
    static const JNINativeMethod methods[] = {
        {"installNativeHooks", "()Z", reinterpret_cast<void*>(install)},
        {"installFrameworkLoaderProbeNative", "()Z", reinterpret_cast<void*>(loaderProbe)},
        {"setEnabled", "(Z)V", reinterpret_cast<void*>(setEnabled)},
        {"resetNativeReadback", "()V", reinterpret_cast<void*>(reset)},
    };
    if (!frameMethod || !beforeSwapMethod || env->RegisterNatives(bridge, methods, 4) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
