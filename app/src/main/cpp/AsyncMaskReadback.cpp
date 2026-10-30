#include "AsyncMaskReadback.h"
#include "VerifiedRendererHook.h"
#include <EGL/eglext.h>
#include <GLES3/gl32.h>
#include <android/log.h>
#include <chrono>
#include <cstring>
#include <thread>

namespace gpu_depth {
namespace {
constexpr char kTag[] = "FreeformUnbounded";
int64_t now() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
}

std::shared_ptr<AsyncMaskReadback> AsyncMaskReadback::create(size_t bytes, Frame publish) {
    auto reader = std::shared_ptr<AsyncMaskReadback>(new AsyncMaskReadback);
    reader->display_ = eglGetCurrentDisplay();
    const EGLContext host = eglGetCurrentContext();
    EGLint configId = 0, count = 0;
    if (reader->display_ == EGL_NO_DISPLAY || host == EGL_NO_CONTEXT ||
        !eglQueryContext(reader->display_, host, EGL_CONFIG_ID, &configId)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI89 async context identity failed EGL=0x%x", eglGetError());
        return nullptr;
    }
    const char* extensions = eglQueryString(reader->display_, EGL_EXTENSIONS);
    const EGLint filter[] = {EGL_CONFIG_ID, configId, EGL_NONE};
    EGLConfig config = nullptr;
    // Filament may create an EGL_KHR_no_config_context host, whose config ID is
    // zero. Zero is not a selectable EGLConfig; preserve that explicit contract.
    if (configId != 0) {
        if (!eglChooseConfig(reader->display_, filter, &config, 1, &count) || count != 1) {
            __android_log_print(ANDROID_LOG_ERROR, kTag, "CI89 async config lookup failed id=%d EGL=0x%x", configId, eglGetError());
            return nullptr;
        }
    } else if (!extensions || !std::strstr(extensions, "EGL_KHR_no_config_context")) return nullptr;
    // On this device GL reports 3.2 / flags=0, but the verified live Framework
    // vector requested client-version=2 / no-error=true. Those GL queries cannot
    // reconstruct EGL sharing parameters. Copy the identity-checked actual list.
    std::vector<EGLint> attributes;
    if (!verified_renderer::copyCurrentContextAttributes(attributes)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI91 verified EGL attribute copy rejected");
        return nullptr;
    }
    reader->context_ = eglCreateContext(reader->display_, config, host, attributes.data());
    if (reader->context_ == EGL_NO_CONTEXT) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI91 shared context create failed config=%d EGL=0x%x",
            configId, eglGetError());
        return nullptr;
    }
    // Prefer a tiny pbuffer; otherwise require the advertised surfaceless contract.
    EGLint surfaceType = 0;
    if (config) eglGetConfigAttrib(reader->display_, config, EGL_SURFACE_TYPE, &surfaceType);
    if (surfaceType & EGL_PBUFFER_BIT) {
        const EGLint size[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        reader->surface_ = eglCreatePbufferSurface(reader->display_, config, size);
    }
    if (reader->surface_ == EGL_NO_SURFACE &&
        (!extensions || !std::strstr(extensions, "EGL_KHR_surfaceless_context"))) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI89 async context has no compatible surface config=%d", configId);
        eglDestroyContext(reader->display_, reader->context_);
        return nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "CI91 shared context created config=%d verifiedAttributes=%zu surfaceless=%d",
        configId, attributes.size(), reader->surface_ == EGL_NO_SURFACE);
    reader->bytes_ = bytes;
    reader->publish_ = publish;
    std::thread([reader] { reader->run(); }).detach();
    return reader;
}

bool AsyncMaskReadback::reserve(bool requireFrame) {
    if (requireFrame && !available_.load()) {
        std::unique_lock<std::mutex> lock(mutex_);
        condition_.wait_for(lock, std::chrono::milliseconds(100), [&] {
            return available_.load() || stopped_.load() || failed_.load();
        });
    }
    bool available = true;
    return !stopped_.load() && available_.compare_exchange_strong(available, false);
}

void AsyncMaskReadback::submit(GLsync fence, int width, int height, int64_t capturedNanos) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        fence_ = fence;
        width_ = width;
        height_ = height;
        capturedNanos_ = capturedNanos;
    }
    condition_.notify_one();
}

void AsyncMaskReadback::stop() {
    stopped_.store(true);
    condition_.notify_one();
}

void AsyncMaskReadback::run() {
    if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI88 async mask context failed EGL=0x%x", eglGetError());
        failed_.store(true);
    } else {
        glGenBuffers(1, &pbo_);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_);
        glBufferData(GL_PIXEL_PACK_BUFFER, static_cast<GLsizeiptr>(bytes_), nullptr, GL_STREAM_READ);
        // One-time allocation completes on this worker before sharing the buffer.
        // This never blocks the renderer, including during resource recreation.
        glFinish();
        if (glGetError() != GL_NO_ERROR || !pbo_) failed_.store(true);
        __android_log_print(ANDROID_LOG_INFO, kTag, "CI88 async mask context ready=%d bytes=%zu", !failed_.load(), bytes_);
        available_.store(!failed_.load());
        condition_.notify_all();
        while (!failed_.load()) {
            GLsync fence = nullptr;
            int width = 0, height = 0;
            int64_t capturedNanos = 0;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                condition_.wait(lock, [&] { return fence_ || stopped_.load(); });
                fence = fence_;
                fence_ = nullptr;
                width = width_;
                height = height_;
                capturedNanos = capturedNanos_;
            }
            if (stopped_.load()) {
                if (fence) glDeleteSync(fence);
                break;
            }
            GLenum status = GL_TIMEOUT_EXPIRED;
            // Only this worker waits. A slow GPU never stalls Filament's render thread.
            while (!stopped_.load() && now() - capturedNanos < 150'000'000 && status == GL_TIMEOUT_EXPIRED) {
                status = glClientWaitSync(fence, 0, 2'000'000);
            }
            glDeleteSync(fence);
            std::vector<uint8_t> alpha;
            if (!stopped_.load() && (status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED)) {
                // Rebind after synchronization to observe shared-object updates.
                glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_);
                const auto* mapped = static_cast<const uint8_t*>(
                    glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, bytes_, GL_MAP_READ_BIT));
                if (mapped) {
                    alpha.assign(mapped, mapped + bytes_);
                    if (!glUnmapBuffer(GL_PIXEL_PACK_BUFFER)) alpha.clear();
                }
                glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                if (alpha.empty()) {
                    __android_log_print(ANDROID_LOG_ERROR, kTag, "CI88 async mask mapping failed GL=0x%x", glGetError());
                    failed_.store(true);
                }
            } else if (!stopped_.load()) {
                __android_log_print(ANDROID_LOG_WARN, kTag, "CI88 async mask fence rejected status=0x%x ageUs=%lld",
                    status, static_cast<long long>((now() - capturedNanos) / 1000));
                failed_.store(true);
            }
            if (failed_.load() || stopped_.load()) break;
            available_.store(true);
            condition_.notify_all();
            if (publish_ && !alpha.empty()) publish_(width, height, capturedNanos, std::move(alpha));
        }
        glDeleteBuffers(1, &pbo_);
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
    eglDestroyContext(display_, context_);
    condition_.notify_all();
}
}
