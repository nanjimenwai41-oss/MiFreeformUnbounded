#include "GpuDepthCapture.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>

namespace gpu_depth {
namespace {
constexpr char kTag[] = "FreeformUnbounded";
constexpr int kOutput = 512;
// 掩码管线不再复制颜色，采样跟随实际 renderer，最高 120 Hz。
// 每次只有一份待完成 GPU 读回和一份 CPU 发布，慢帧不排队。
constexpr int64_t kInterval = 8'333'333;
using Clock = std::chrono::steady_clock;
int64_t now() { return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now().time_since_epoch()).count(); }
Frame publish = nullptr;
std::atomic_bool enabled{false}, reset{false}, busy{false};
bool diagnosticEnabled = true;
int64_t nextPropertyCheck = 0, lastCapture = 0;
unsigned swaps = 0, samples = 0, failures = 0;
int64_t queueTotal = 0, queueMax = 0, mapTotal = 0, mapMax = 0;

struct Resources {
    EGLContext context = EGL_NO_CONTEXT;
    int sourceWidth = 0, sourceHeight = 0, width = 0, height = 0;
    GLuint depthTexture = 0, depthFbo = 0;
    GLuint packedTexture = 0, packedFbo = 0;
    GLuint program = 0, vao = 0, pbo = 0;
    GLsync fence = nullptr;
    size_t bytes = 0;
    int64_t capturedNanos = 0;
} res;

// Filament 会缓存 GL 状态；任何触及的状态均须恢复，不能只恢复 framebuffer。
struct State {
    GLint draw, read, viewport[4], program, vao, active, texture, sampler;
    GLint pack, unpack, alignment, rowLength, skipRows, skipPixels;
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
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
};

void release() {
    if (res.context == eglGetCurrentContext()) {
        if (res.fence) glDeleteSync(res.fence);
        glDeleteBuffers(1, &res.pbo);
        glDeleteVertexArrays(1, &res.vao);
        glDeleteProgram(res.program);
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
    const double scale = std::min(1.0, static_cast<double>(kOutput) / std::max(width, height));
    res.width = std::max(1, static_cast<int>(width * scale + 0.5));
    res.height = std::max(1, static_cast<int>(height * scale + 0.5));
    res.bytes = static_cast<size_t>(res.width) * res.height * 4;
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    // depth blit 必须保持源尺寸与格式；缩小发生在后面的采样 shader。
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
    // Preserve the verified full-size depth target; the pack shader performs
    // the downsample into the smaller export texture.
    if (!textureFbo(res.depthTexture, res.depthFbo, width, height, internal, attachment, external, type) ||
        !textureFbo(res.packedTexture, res.packedFbo, res.width, res.height, GL_RGBA8,
                    GL_COLOR_ATTACHMENT0, GL_RGBA, GL_UNSIGNED_BYTE)) return false;
    const char* vertex = R"(#version 300 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
})";
    const char* fragment = R"(#version 300 es
precision highp float;
precision highp int;
uniform highp sampler2D sourceDepth;
uniform vec2 outputSize;
layout(location=0) out vec4 packedDepth;
void main() {
    float d = texture(sourceDepth, gl_FragCoord.xy / outputSize).r;
    uint bits = floatBitsToUint(d);
    packedDepth = vec4(uvec4(bits, bits >> 8u, bits >> 16u, bits >> 24u) & 255u) / 255.0;
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
    glGenBuffers(1, &res.pbo);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, res.pbo);
    glBufferData(GL_PIXEL_PACK_BUFFER, static_cast<GLsizeiptr>(res.bytes), nullptr, GL_STREAM_READ);
    const GLenum error = glGetError();
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "CI62 GPU depth setup source=%dx%d output=%dx%d bits=%d stencil=%d format=0x%x bytes=%zu error=0x%x",
        width, height, res.width, res.height, depthBits, stencilBits, internal, res.bytes, error);
    return error == GL_NO_ERROR;
}

void poll() {
    if (!res.fence || res.context != eglGetCurrentContext()) return;
    const GLenum result = glClientWaitSync(res.fence, 0, 0);
    if (result == GL_TIMEOUT_EXPIRED) return;
    glDeleteSync(res.fence);
    res.fence = nullptr;
    if (result == GL_WAIT_FAILED) { reset.store(true); return; }
    const auto started = now();
    GLint previous = 0;
    glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &previous);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, res.pbo);
    void* mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, res.bytes, GL_MAP_READ_BIT);
    std::vector<uint8_t> depth;
    if (mapped) {
        const auto* bytes = static_cast<uint8_t*>(mapped);
        depth.assign(bytes, bytes + res.bytes);
        if (!glUnmapBuffer(GL_PIXEL_PACK_BUFFER)) depth.clear();
    }
    glBindBuffer(GL_PIXEL_PACK_BUFFER, previous);
    const auto elapsed = now() - started;
    mapTotal += elapsed; mapMax = std::max(mapMax, elapsed);
    if (!depth.empty() && publish && enabled.load()) {
        busy.store(true);
        publish(res.width, res.height, res.capturedNanos, std::move(depth));
    }
}
}

void initialize(Frame frame) { publish = frame; }
void setEnabled(bool value) { enabled.store(value); reset.store(true); }
void requestReset() { reset.store(true); }
void publisherFinished() { busy.store(false); }

void beforeDepthDiscard() {
    const auto started = now();
    if (started > nextPropertyCheck) {
        char value[PROP_VALUE_MAX] = {};
        __system_property_get("debug.freeform.depth_capture", value);
        diagnosticEnabled = std::strcmp(value, "0") != 0;
        nextPropertyCheck = started + 1'000'000'000;
    }
    if (!enabled.load() || !diagnosticEnabled || busy.load() || failures >= 3) return;
    // 上一轮 swap 已提交 GPU 命令。在下一次 discard 前零等待取回已完成的 PBO，
    // CPU 发布与下一轮 GPU 采样重叠，避免单 PBO 把更新率固定减半。
    poll();
    if (res.fence || started - lastCapture < kInterval) return;
    lastCapture = started;
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
    if (reset.exchange(false)) { release(); failures = 0; }
    bool ok = setup(viewport[2], viewport[3], bits, stencil);
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
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, res.packedFbo);
        glViewport(0, 0, res.width, res.height);
        glUseProgram(res.program);
        glBindVertexArray(res.vao);
        glBindTexture(GL_TEXTURE_2D, res.depthTexture);
        glUniform1i(glGetUniformLocation(res.program, "sourceDepth"), 0);
        glUniform2f(glGetUniformLocation(res.program, "outputSize"), res.width, res.height);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, res.pbo);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, res.packedFbo);
        glReadPixels(0, 0, res.width, res.height, GL_RGBA, GL_UNSIGNED_BYTE,
                     nullptr);
        const GLenum error = glGetError();
        if (error == GL_NO_ERROR) res.fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        ok = error == GL_NO_ERROR && res.fence;
        if (ok) res.capturedNanos = started;
    }
    if (!ok) {
        ++failures;
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "CI74 depth-only capture rejected failure=%u depthBlit=0x%x error=0x%x samples=%d",
            failures, depthError, glGetError(), sampleBuffers);
        release();
    } else ++samples;
    const auto elapsed = now() - started;
    queueTotal += elapsed; queueMax = std::max(queueMax, elapsed);
}

void atSwap() {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;
    if (res.context != EGL_NO_CONTEXT && res.context != eglGetCurrentContext()) release();
    if (reset.exchange(false)) {
        State state;
        release();
        failures = 0;
    }
    poll();
    if (++swaps % 180 == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "CI62 depth timing swaps=%u samples=%u enabled=%d queueTotalUs=%lld queueMaxUs=%lld mapTotalUs=%lld mapMaxUs=%lld",
            swaps, samples, diagnosticEnabled, static_cast<long long>(queueTotal / 1000),
            static_cast<long long>(queueMax / 1000), static_cast<long long>(mapTotal / 1000),
            static_cast<long long>(mapMax / 1000));
    }
}
}
