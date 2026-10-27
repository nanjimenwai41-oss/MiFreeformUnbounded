#include "GpuDepthCapture.h"
#include "VerifiedRendererHook.h"

#include <android/log.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <cmath>
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
    // 诊断快照只在 ROOT 设置了真实宿主 cache 路径时保存一次；不进入可见景深链。
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
        // 跳过初始空帧，完整保留同一时刻的 RGBA 与原始浮点深度。
        if (std::any_of(frame.rawDepth.begin(), frame.rawDepth.end(), [](uint8_t b) { return b != 0; })) {
            const int dimensions[] = {frame.width, frame.height};
            probeSaved = save(".f32", frame.rawDepth.data(), frame.rawDepth.size()) &&
                save(".size", dimensions, sizeof(dimensions));
            __android_log_print(ANDROID_LOG_INFO, kTag, "CI63 paired probe saved=%d prefix=%s", probeSaved, probe);
        }
    }
    // 保留零背景；正深度用对数映射保存整个 24 位逆向 Z 的有效范围。
    // 解码在工作线程执行，渲染线程只复制 GPU 已完成的小尺寸 PBO。
    const size_t count = frame.rawDepth.size() / 4;
    std::vector<uint16_t> depth(count);
    float minimum = 1, maximum = 0;
    size_t geometry = 0, invalid = 0;
    for (size_t i = 0; i < count; ++i) {
        const uint8_t* p = frame.rawDepth.data() + i * 4;
        const uint32_t bits = uint32_t(p[0]) | uint32_t(p[1]) << 8 |
            uint32_t(p[2]) << 16 | uint32_t(p[3]) << 24;
        float value;
        std::memcpy(&value, &bits, sizeof(value));
        if (!std::isfinite(value) || value < 0 || value > 1) { ++invalid; continue; }
        if (value <= 0) continue;
        minimum = std::min(minimum, value);
        maximum = std::max(maximum, value);
        ++geometry;
        depth[i] = static_cast<uint16_t>(std::clamp(
            (1.0f + std::log2(value) / 24.0f) * 65535.0f, 1.0f, 65535.0f));
    }
    static unsigned frames = 0;
    const unsigned sample = ++frames;
    if (sample <= 5 || sample % 90 == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "CI62 pre-discard depth frame=%u size=%dx%d min=%g max=%g geometry=%zu/%zu invalid=%zu",
            sample, frame.width, frame.height, minimum, maximum, geometry, count, invalid);
    }
    // Sky 是写深度的网格；由 Kotlin 根据本帧实测深度簇分离，不能要求背景为零。
    if (invalid || !geometry) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jobject depthBuffer = env->NewDirectByteBuffer(depth.data(), depth.size() * sizeof(uint16_t));
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

void swap(const char*, EGLDisplay, EGLSurface) { gpu_depth::atSwap(); }

jboolean install(JNIEnv*, jclass) {
    std::call_once(worker, [] {
        gpu_depth::initialize(queueFrame);
        std::thread([] {
            for (;;) {
                std::unique_ptr<Frame> frame;
                {
                    std::unique_lock<std::mutex> lock(mutex);
                    condition.wait(lock, [] { return pending != nullptr; });
                    frame = std::move(pending);
                }
                publish(*frame);
                gpu_depth::publisherFinished();
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
    frameMethod = env->GetStaticMethodID(bridge, "onNativeDepthMaskFrame",
        "(IIJLjava/nio/ByteBuffer;)Z");
    static const JNINativeMethod methods[] = {
        {"installNativeHooks", "()Z", reinterpret_cast<void*>(install)},
        {"installFrameworkLoaderProbeNative", "()Z", reinterpret_cast<void*>(loaderProbe)},
        {"setEnabled", "(Z)V", reinterpret_cast<void*>(setEnabled)},
        {"resetNativeReadback", "()V", reinterpret_cast<void*>(reset)},
    };
    if (!frameMethod || env->RegisterNatives(bridge, methods, 4) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
