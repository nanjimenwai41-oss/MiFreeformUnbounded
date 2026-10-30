#include "GpuDepthCapture.h"
#include "CaptureRecoveryPolicy.h"
#include "CaptureCadencePolicy.h"
#include "AsyncMaskReadback.h"

#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <android/log.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>

namespace gpu_depth {
namespace {
constexpr char kTag[] = "FreeformUnbounded";
constexpr int kHistogramSize = 512;
constexpr size_t kMaxPixels = 4'000'000;
constexpr size_t kHistogramBytes = 259 * sizeof(uint32_t);
// 掩码管线不再复制颜色，采样跟随实际 renderer，最高 120 Hz。
// 单份 GPU 读回与 CPU worker/pending 最新帧独立重叠，慢帧不排队。
using Clock = std::chrono::steady_clock;
int64_t now() { return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now().time_since_epoch()).count(); }
Frame publish = nullptr;
std::atomic_bool enabled{false}, reset{false};
bool diagnosticEnabled = true;
bool framePairEnabled = false;
int64_t nextPropertyCheck = 0;
unsigned swaps = 0, samples = 0;
CaptureRecoveryPolicy recovery;
CaptureCadencePolicy cadence;
int64_t queueTotal = 0, queueMax = 0;
int64_t swapCapture = 0;

struct Resources {
    EGLContext context = EGL_NO_CONTEXT;
    int sourceWidth = 0, sourceHeight = 0, width = 0, height = 0;
    GLuint depthTexture = 0, depthFbo = 0;
    GLuint packedTexture = 0, packedFbo = 0;
    GLuint program = 0, histogramProgram = 0, thresholdProgram = 0;
    GLuint histogramBuffer = 0, vao = 0;
    std::shared_ptr<AsyncMaskReadback> readback;
    size_t bytes = 0;
} res;

// Filament 会缓存 GL 状态；任何触及的状态均须恢复，不能只恢复 framebuffer。
struct State {
    GLint draw, read, viewport[4], program, vao, active, texture, sampler;
    GLint pack, unpack, alignment, rowLength, skipRows, skipPixels;
    GLint storage, indexedStorage;
    GLint64 storageStart, storageSize;
    GLboolean mask[4];
    const GLenum caps[7] = {GL_SCISSOR_TEST, GL_BLEND, GL_DEPTH_TEST, GL_STENCIL_TEST,
                          GL_CULL_FACE, GL_RASTERIZER_DISCARD, GL_DITHER};
    GLboolean capability[7];
    State() {
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_CURRENT_PROGRAM, &program);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);
        glGetIntegerv(GL_ACTIVE_TEXTURE, &active);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &texture);
        glGetIntegerv(GL_SAMPLER_BINDING, &sampler);
        glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &pack);
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &unpack);
        glGetIntegerv(GL_PACK_ALIGNMENT, &alignment);
        glGetIntegerv(GL_PACK_ROW_LENGTH, &rowLength);
        glGetIntegerv(GL_PACK_SKIP_ROWS, &skipRows);
        glGetIntegerv(GL_PACK_SKIP_PIXELS, &skipPixels);
        glGetIntegerv(GL_SHADER_STORAGE_BUFFER_BINDING, &storage);
        glGetIntegeri_v(GL_SHADER_STORAGE_BUFFER_BINDING, 0, &indexedStorage);
        glGetInteger64i_v(GL_SHADER_STORAGE_BUFFER_START, 0, &storageStart);
        glGetInteger64i_v(GL_SHADER_STORAGE_BUFFER_SIZE, 0, &storageSize);
        glGetBooleanv(GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < 7; ++i) capability[i] = glIsEnabled(caps[i]);
    }
    ~State() {
        for (int i = 0; i < 7; ++i) {
            if (capability[i]) glEnable(caps[i]); else glDisable(caps[i]);
        }
        glColorMask(mask[0], mask[1], mask[2], mask[3]);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindSampler(0, sampler);
        glActiveTexture(active);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pack);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
        glPixelStorei(GL_PACK_ALIGNMENT, alignment);
        glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
        glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
        glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
        if (indexedStorage && storageSize > 0) {
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, indexedStorage,
                static_cast<GLintptr>(storageStart), static_cast<GLsizeiptr>(storageSize));
        } else glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, indexedStorage);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, storage);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
};

void release() {
    swapCapture = 0;
    if (res.readback) res.readback->stop();
    if (res.context == eglGetCurrentContext()) {
        glDeleteVertexArrays(1, &res.vao);
        glDeleteProgram(res.program);
        glDeleteProgram(res.histogramProgram);
        glDeleteProgram(res.thresholdProgram);
        glDeleteBuffers(1, &res.histogramBuffer);
        const GLuint textures[] = {res.depthTexture, res.packedTexture};
        const GLuint fbos[] = {res.depthFbo, res.packedFbo};
        glDeleteTextures(2, textures);
        glDeleteFramebuffers(2, fbos);
    }
    // 切换 context 时旧对象由旧 context 管理，禁止在新 context 删除同名对象。
    res = Resources{};
}

GLuint shader(GLenum type, const char* source) {
    const GLuint object = glCreateShader(type);
    glShaderSource(object, 1, &source, nullptr);
    glCompileShader(object);
    GLint ok = 0;
    glGetShaderiv(object, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char message[512] = {};
        glGetShaderInfoLog(object, sizeof(message), nullptr, message);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "CI62 depth shader failed: %s", message);
        glDeleteShader(object);
        return 0;
    }
    return object;
}

GLuint computeProgram(const char* source) {
    const GLuint compiled = shader(GL_COMPUTE_SHADER, source);
    if (!compiled) return 0;
    const GLuint program = glCreateProgram();
    glAttachShader(program, compiled);
    glLinkProgram(program);
    glDeleteShader(compiled);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) { glDeleteProgram(program); return 0; }
    return program;
}

bool textureFbo(GLuint& texture, GLuint& fbo, int width, int height, GLenum format,
        GLenum attachment, GLenum externalFormat, GLenum type) {
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, externalFormat, type, nullptr);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture, 0);
    const GLenum buffer = attachment == GL_COLOR_ATTACHMENT0 ? GL_COLOR_ATTACHMENT0 : GL_NONE;
    glDrawBuffers(1, &buffer);
    glReadBuffer(buffer);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

bool setup(int width, int height, GLint depthBits, GLint stencilBits) {
    if (res.context == eglGetCurrentContext() && res.sourceWidth == width &&
            res.sourceHeight == height && res.program) return true;
    release();
    res.context = eglGetCurrentContext();
    res.sourceWidth = width;
    res.sourceHeight = height;
    if (width > 4096 || height > 4096 || static_cast<size_t>(width) * height > kMaxPixels) return false;
    // Four full-resolution alpha pixels per RGBA texel, with at most three
    // padding bytes on each row. No downsampled contour is enlarged by SystemUI.
    res.width = (width + 3) / 4;
    res.height = height;
    res.bytes = static_cast<size_t>(res.width) * res.height * 4;
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    // depth blit 必须保持源尺寸与格式；最终 alpha 不缩小。
    GLenum internal = depthBits == 16 ? GL_DEPTH_COMPONENT16 : GL_DEPTH_COMPONENT24;
    GLenum external = GL_DEPTH_COMPONENT;
    GLenum type = depthBits == 16 ? GL_UNSIGNED_SHORT : GL_UNSIGNED_INT;
    GLenum attachment = GL_DEPTH_ATTACHMENT;
    if (stencilBits == 8 && depthBits == 24) {
        internal = GL_DEPTH24_STENCIL8;
        external = GL_DEPTH_STENCIL;
        type = GL_UNSIGNED_INT_24_8;
        attachment = GL_DEPTH_STENCIL_ATTACHMENT;
    } else if (depthBits != 16 && depthBits != 24) return false;
    // GLES depth/stencil blits require matching source and destination extents.
    // Preserve the verified full-size depth target; RGBA packs four alpha
    // pixels across the width without reducing contour resolution.
    if (!textureFbo(res.depthTexture, res.depthFbo, width, height, internal, attachment, external, type) ||
        !textureFbo(res.packedTexture, res.packedFbo, res.width, res.height, GL_RGBA8,
                    GL_COLOR_ATTACHMENT0, GL_RGBA, GL_UNSIGNED_BYTE)) return false;
    const char* histogram = R"(#version 310 es
precision highp float;
precision highp int;
layout(local_size_x=16, local_size_y=16) in;
layout(std430, binding=0) buffer Histogram { uint bins[256]; float threshold; uint valid; uint invalid; };
uniform highp sampler2D sourceDepth;
uniform ivec2 sampleSize;
shared uint localBins[256];
void main() {
    uint slot = gl_LocalInvocationIndex;
    localBins[slot] = 0u;
    barrier();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (all(lessThan(p, sampleSize))) {
        float d = textureLod(sourceDepth, (vec2(p) + 0.5) / vec2(sampleSize), 0.0).r;
        if (isnan(d) || d < 0.0 || d > 1.0) atomicAdd(invalid, 1u);
        else if (d > 0.0) {
            uint encoded = uint(clamp((1.0 + log2(d) / 24.0) * 65535.0, 1.0, 65535.0));
            atomicAdd(localBins[encoded >> 8u], 1u);
        }
    }
    barrier();
    if (localBins[slot] > 0u) atomicAdd(bins[slot], localBins[slot]);
})";
    const char* reduction = R"(#version 310 es
precision highp float;
precision highp int;
layout(local_size_x=1) in;
layout(std430, binding=0) buffer Histogram { uint bins[256]; float threshold; uint valid; uint invalid; };
uniform uint minimumCluster;
void main() {
    uint total = 0u;
    for (int i=0; i<256; ++i) total += bins[i];
    uint below = 0u;
    int previous = -1, widestGap = 1, split = -1;
    for (int i=0; i<256; ++i) {
        if (bins[i] == 0u) continue;
        int gap = i - previous - 1;
        if (previous >= 0 && gap > widestGap && below >= minimumCluster && total-below >= minimumCluster) {
            widestGap = gap;
            split = (previous + i) * 128;
        }
        below += bins[i];
        previous = i;
    }
    threshold = exp2((float(split + 1) / 65535.0 - 1.0) * 24.0);
    valid = split >= 0 && invalid == 0u ? 1u : 0u;
})";
    res.histogramProgram = computeProgram(histogram);
    res.thresholdProgram = computeProgram(reduction);
    if (!res.histogramProgram || !res.thresholdProgram) return false;
    glGenBuffers(1, &res.histogramBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, res.histogramBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, kHistogramBytes, nullptr, GL_DYNAMIC_DRAW);
    const char* vertex = R"(#version 310 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
})";
    const char* fragment = R"(#version 310 es
precision highp float;
precision highp int;
uniform highp sampler2D sourceDepth;
uniform ivec2 sourceSize;
layout(std430, binding=0) readonly buffer Histogram { uint bins[256]; float threshold; uint valid; uint invalid; };
layout(location=0) out vec4 packedAlpha;
void main() {
    vec4 alpha = vec4(0.0);
    int first = int(gl_FragCoord.x) * 4;
    for (int i=0; i<4; ++i) {
        if (valid == 0u || first+i >= sourceSize.x) continue;
        vec2 uv = vec2(float(first+i)+0.5, gl_FragCoord.y) / vec2(sourceSize);
        float d = texture(sourceDepth, uv).r;
        alpha[i] = d > threshold && d <= 1.0 ? 1.0 : 0.0;
    }
    packedAlpha = alpha;
})";
    GLuint vs = shader(GL_VERTEX_SHADER, vertex), fs = shader(GL_FRAGMENT_SHADER, fragment);
    if (!vs || !fs) {
        glDeleteShader(vs); glDeleteShader(fs);
        return false;
    }
    res.program = glCreateProgram();
    glAttachShader(res.program, vs); glAttachShader(res.program, fs);
    glLinkProgram(res.program);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint linked = 0;
    glGetProgramiv(res.program, GL_LINK_STATUS, &linked);
    if (!linked) return false;
    glGenVertexArrays(1, &res.vao);
    res.readback = AsyncMaskReadback::create(res.bytes, [](int width, int height, int64_t stamp, std::vector<uint8_t>&& bytes) {
        if (enabled.load() && publish) publish(width, height, stamp, std::move(bytes));
    });
    const GLenum error = glGetError();
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "CI87 GPU alpha setup source=%dx%d packed=%dx%d bits=%d stencil=%d format=0x%x bytes=%zu error=0x%x",
        width, height, res.width, res.height, depthBits, stencilBits, internal, res.bytes, error);
    return error == GL_NO_ERROR && res.readback != nullptr;
}
}

void initialize(Frame frame) { publish = frame; }
void setEnabled(bool value) { enabled.store(value); reset.store(true); }
void requestReset() { reset.store(true); }

void beforeDepthDiscard() {
    const auto started = now();
    if (started > nextPropertyCheck) {
        char value[PROP_VALUE_MAX] = {};
        __system_property_get("debug.freeform.depth_capture", value);
        diagnosticEnabled = std::strcmp(value, "0") != 0;
        __system_property_get("debug.freeform.frame_sync", value);
        framePairEnabled = std::strcmp(value, "1") == 0;
        nextPropertyCheck = started + 1'000'000'000;
    }
    if (!enabled.load() || !diagnosticEnabled || eglGetCurrentContext() == EGL_NO_CONTEXT) return;
    // 必须先处理 reset/context 变化，再检查退避和旧 fence；不能被失败计数短路。
    if (reset.exchange(false) || (res.context != EGL_NO_CONTEXT && res.context != eglGetCurrentContext())) {
        State state;
        release();
        recovery.reset();
        cadence.reset();
    }
    if (!recovery.canCapture(started)) return;
    if (res.readback && res.readback->failed()) {
        State state;
        release();
        recovery.failed(started);
        return;
    }
    // Every buffer intercepted by BLAST needs its own alpha. In the opt-in
    // experiment, skipping a busy PBO would leave the final animation frame
    // without a matching mask. The normal asynchronous path remains nonblocking.
    if (!framePairEnabled && !cadence.accept(started)) return;
    GLint draw = 0, viewport[4] = {}, bits = 0, stencil = 0, sampleBuffers = 0;
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw);
    glGetIntegerv(GL_VIEWPORT, viewport);
    glGetIntegerv(GL_DEPTH_BITS, &bits);
    glGetIntegerv(GL_STENCIL_BITS, &stencil);
    glGetIntegerv(GL_SAMPLE_BUFFERS, &sampleBuffers);
    // 当前设备已确认是 default framebuffer；其他渲染目标不能套用同一份契约。
    if (draw != 0 || bits <= 0 || viewport[0] != 0 || viewport[1] != 0 ||
        viewport[2] <= 0 || viewport[3] <= 0 || sampleBuffers != 0) return;
    State state;
    for (GLenum cap : state.caps) glDisable(cap);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glBindSampler(0, 0);
    bool ok = setup(viewport[2], viewport[3], bits, stencil);
    // The shared-context worker can finish this frame before the next renderer
    // callback. No PBO polling or mapping is performed on the renderer thread.
    if (ok && !res.readback->reserve(framePairEnabled)) {
        if (framePairEnabled) __android_log_print(ANDROID_LOG_WARN, kTag, "CI92 frame-pair readback slot deadline");
        return;
    }
    GLenum depthError = 0;
    if (ok) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, state.draw);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, res.depthFbo);
        glBlitFramebuffer(0, 0, viewport[2], viewport[3], 0, 0, viewport[2], viewport[3],
                          GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        depthError = glGetError();
        ok = depthError == GL_NO_ERROR;
    }
    if (ok) {
        // Match DepthClusterPolicy's verified 256-bin split, then classify the
        // original depth pixels on GPU before the renderer discards them.
        const double scale = std::min(1.0, static_cast<double>(kHistogramSize) / std::max(res.sourceWidth, res.sourceHeight));
        const int sampleWidth = std::max(1, static_cast<int>(res.sourceWidth * scale + 0.5));
        const int sampleHeight = std::max(1, static_cast<int>(res.sourceHeight * scale + 0.5));
        const uint32_t empty[259] = {};
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, res.histogramBuffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, kHistogramBytes, empty);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, res.histogramBuffer);
        glBindTexture(GL_TEXTURE_2D, res.depthTexture);
        glUseProgram(res.histogramProgram);
        glUniform1i(glGetUniformLocation(res.histogramProgram, "sourceDepth"), 0);
        glUniform2i(glGetUniformLocation(res.histogramProgram, "sampleSize"), sampleWidth, sampleHeight);
        glDispatchCompute((sampleWidth + 15) / 16, (sampleHeight + 15) / 16, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(res.thresholdProgram);
        glUniform1ui(glGetUniformLocation(res.thresholdProgram, "minimumCluster"), std::max(1, sampleWidth * sampleHeight / 50));
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, res.packedFbo);
        glViewport(0, 0, res.width, res.height);
        glUseProgram(res.program);
        glBindVertexArray(res.vao);
        glBindTexture(GL_TEXTURE_2D, res.depthTexture);
        glUniform1i(glGetUniformLocation(res.program, "sourceDepth"), 0);
        glUniform2i(glGetUniformLocation(res.program, "sourceSize"), res.sourceWidth, res.sourceHeight);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, res.readback->buffer());
        glBindFramebuffer(GL_READ_FRAMEBUFFER, res.packedFbo);
        glReadPixels(0, 0, res.width, res.height, GL_RGBA, GL_UNSIGNED_BYTE,
                     nullptr);
        const GLenum error = glGetError();
        GLsync fence = error == GL_NO_ERROR ? glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0) : nullptr;
        ok = error == GL_NO_ERROR && fence;
        if (ok) {
            glFlush();
            res.readback->submit(fence, res.sourceWidth, res.sourceHeight, started);
            swapCapture = started;
        }
    }
    if (!ok) {
        recovery.failed(started);
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "CI74 depth-only capture rejected failure=%u depthBlit=0x%x error=0x%x samples=%d",
            recovery.failures, depthError, glGetError(), sampleBuffers);
        release();
    } else {
        recovery.reset();
        ++samples;
    }
    const auto elapsed = now() - started;
    queueTotal += elapsed; queueMax = std::max(queueMax, elapsed);
}

void atSwap() {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;
    if (reset.exchange(false) || (res.context != EGL_NO_CONTEXT && res.context != eglGetCurrentContext())) {
        State state;
        release();
        recovery.reset();
        cadence.reset();
    }
    if (++swaps % 180 == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "CI88 depth timing swaps=%u samples=%u enabled=%d queueTotalUs=%lld queueMaxUs=%lld asyncReadback=true",
            swaps, samples, diagnosticEnabled, static_cast<long long>(queueTotal / 1000),
            static_cast<long long>(queueMax / 1000));
    }
}

int64_t takeSwapCapture() {
    const int64_t stamp = swapCapture;
    swapCapture = 0;
    return stamp;
}
}
