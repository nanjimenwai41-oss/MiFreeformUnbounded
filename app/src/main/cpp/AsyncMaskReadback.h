#pragma once

#include "GpuDepthCapture.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>

namespace gpu_depth {
/** Owns one PBO in a shared context; the frame-pair experiment can apply bounded backpressure. */
class AsyncMaskReadback : public std::enable_shared_from_this<AsyncMaskReadback> {
public:
    static std::shared_ptr<AsyncMaskReadback> create(size_t bytes, Frame publish);
    bool reserve(bool requireFrame = false);
    GLuint buffer() const { return pbo_; }
    void submit(GLsync fence, int width, int height, int64_t capturedNanos);
    void stop();
    bool failed() const { return failed_.load(); }

private:
    void run();
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    GLuint pbo_ = 0;
    size_t bytes_ = 0;
    Frame publish_ = nullptr;
    std::atomic_bool available_{false}, stopped_{false}, failed_{false};
    std::mutex mutex_;
    std::condition_variable condition_;
    GLsync fence_ = nullptr;
    int width_ = 0, height_ = 0;
    int64_t capturedNanos_ = 0;
};
}
