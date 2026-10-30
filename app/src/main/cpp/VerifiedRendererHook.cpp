#include "VerifiedRendererHook.h"
#include "RendererDispatchPolicy.h"

#include <android/log.h>
#include <android/trace.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>

namespace verified_renderer {
namespace {
constexpr char kTag[] = "FreeformUnbounded";
// 仅适用于设备拉取并反汇编的 ELF；不以包名、库名或版本字符串判断 ABI。
constexpr uint8_t kBuildId[] = {
    0xe4, 0x01, 0x4a, 0xed, 0x25, 0x93, 0xc4, 0x26, 0x9e, 0x29,
    0x60, 0xbc, 0x4c, 0x58, 0x8c, 0xac, 0xe2, 0x2d, 0x57, 0xff,
};
// 同一设备 APK 内 libbytehook.so 的 GNU build ID；与模块自带副本不同。
constexpr uint8_t kVendorHookBuildId[] = {
    0xb6, 0x3e, 0x9d, 0xbe, 0xa8, 0x27, 0x27, 0x7a, 0x7a, 0x26,
    0xac, 0xe0, 0xb2, 0x11, 0x6b, 0x46, 0x13, 0x6e, 0xf6, 0xb2,
};
constexpr uintptr_t kEngineGlobal = 0x4223c0; // _ZN2MI7GEngineE
constexpr uintptr_t kSwapSlot = 0x420590; // R_AARCH64_JUMP_SLOT eglSwapBuffers
constexpr uintptr_t kInvalidateSlot = 0x41c958; // R_AARCH64_GLOB_DAT glInvalidateFramebuffer
// 同一 ELF 的 0x36c190..0x36c1a4 设置 OpenGLDriver vtable；0x3698d0 从
// driver+0xe98 读取缓存入口，随后 0x3698dc blr。仅改 GOT 会漏掉先创建的 driver。
constexpr uintptr_t kDriverVtable = 0x4142a0;
constexpr uintptr_t kDriverInvalidate = 0xe98;
using Swap = EGLBoolean (*)(EGLDisplay, EGLSurface);
using Invalidate = void (*)(GLenum, GLsizei, const GLenum*);
Invalidate originalInvalidate = nullptr;
SwapObserver observeSwap = nullptr;
EndPassObserver observeEndPass = nullptr;
uintptr_t endDiscardReturn = 0;
using Hooked = void (*)(void*, int, const char*, const char*, void*, void*, void*);
using HookSingle = void* (*)(const char*, const char*, const char*, void*, Hooked, void*);
using GetPrevious = void* (*)(void*);
using PopStack = void (*)(void*);
GetPrevious vendorPrevious = nullptr;
PopStack vendorPopStack = nullptr;
std::string frameworkName;
std::mutex installMutex;
std::atomic_bool installed{false};
std::atomic_uint discardSamples{0};
std::atomic_uint swapSamples{0};

bool matchesBuild(const dl_phdr_info* info, const uint8_t* expected) {
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i) {
        const auto& phdr = info->dlpi_phdr[i];
        if (phdr.p_type != PT_NOTE) continue;
        const auto* bytes = reinterpret_cast<const uint8_t*>(info->dlpi_addr + phdr.p_vaddr);
        size_t cursor = 0;
        while (cursor + sizeof(ElfW(Nhdr)) <= phdr.p_memsz) {
            ElfW(Nhdr) note;
            std::memcpy(&note, bytes + cursor, sizeof(note));
            cursor += sizeof(note);
            const size_t nameBytes = (static_cast<size_t>(note.n_namesz) + 3) & ~size_t(3);
            const size_t descBytes = (static_cast<size_t>(note.n_descsz) + 3) & ~size_t(3);
            if (nameBytes > phdr.p_memsz - cursor ||
                descBytes > phdr.p_memsz - cursor - nameBytes) break;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 &&
                note.n_descsz == sizeof(kBuildId) &&
                std::memcmp(bytes + cursor, "GNU", 4) == 0 &&
                std::memcmp(bytes + cursor + nameBytes, expected, sizeof(kBuildId)) == 0) {
                return true;
            }
            cursor += nameBytes + descBytes;
        }
    }
    return false;
}

int findModule(dl_phdr_info* info, size_t, void* opaque) {
    if (!matchesBuild(info, kBuildId)) return 0;
    *static_cast<uintptr_t*>(opaque) = info->dlpi_addr;
    frameworkName = info->dlpi_name;
    return 1;
}

int findVendorHook(dl_phdr_info* info, size_t, void* opaque) {
    if (!matchesBuild(info, kVendorHookBuildId)) return 0;
    *static_cast<uintptr_t*>(opaque) = info->dlpi_addr;
    return 1;
}

void captureSwap(EGLDisplay display, EGLSurface surface) {
    if (swapSamples.fetch_add(1) == 0 && eglGetCurrentContext() != EGL_NO_CONTEXT) {
        const auto* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
        GLint function = 0, alignment = 0, rowLength = 0;
        GLfloat clear = 0, range[2] = {};
        glGetIntegerv(GL_DEPTH_FUNC, &function);
        glGetIntegerv(GL_PACK_ALIGNMENT, &alignment);
        glGetIntegerv(GL_PACK_ROW_LENGTH, &rowLength);
        glGetFloatv(GL_DEPTH_CLEAR_VALUE, &clear);
        glGetFloatv(GL_DEPTH_RANGE, range);
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "CI60 renderer readback contract NV_read_depth=%d depthFunc=0x%x clear=%g "
            "range=%g,%g packAlignment=%d packRowLength=%d",
            extensions != nullptr && std::strstr(extensions, "GL_NV_read_depth ") != nullptr,
            function, clear, range[0], range[1], alignment, rowLength);
    }
    observeSwap("CI61.vendor-chain.eglSwapBuffers", display, surface);
}

EGLBoolean onVendorSwap(EGLDisplay display, EGLSurface surface) {
    void* returnAddress = __builtin_return_address(0);
    ATrace_beginSection("Freeform.wallpaperSwap");
    captureSwap(display, surface);
    auto previous = reinterpret_cast<Swap>(vendorPrevious(reinterpret_cast<void*>(onVendorSwap)));
    const EGLBoolean result = previous(display, surface);
    vendorPopStack(returnAddress);
    ATrace_endSection();
    return result;
}

void onVendorHooked(void*, int status, const char* caller, const char* symbol,
        void*, void* previous, void*) {
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "CI61 vendor chain status=%d caller=%s symbol=%s previous=%p",
        status, caller == nullptr ? "null" : caller, symbol == nullptr ? "null" : symbol, previous);
}

void onInvalidate(GLenum target, GLsizei count, const GLenum* attachments) {
    // 在真实 renderer 调用丢弃附件之前记录状态；此处不读回、不改变 GL 状态。
    bool depthDiscarded = false;
    for (GLsizei i = 0; attachments != nullptr && i < count; ++i) {
        depthDiscarded |= attachments[i] == GL_DEPTH_ATTACHMENT || attachments[i] == GL_DEPTH;
    }
    if (depthDiscarded && reinterpret_cast<uintptr_t>(__builtin_return_address(0)) == endDiscardReturn &&
            observeEndPass != nullptr && eglGetCurrentContext() != EGL_NO_CONTEXT) {
        // 设备 ELF 的 0x3698dc 是 endRenderPass 的 blr，返回地址 0x3698e0。
        // beginRenderPass 的 0x369550 必须排除，那里尚未绘制本帧。
        observeEndPass();
    }
    if (depthDiscarded && eglGetCurrentContext() != EGL_NO_CONTEXT) {
        const unsigned sample = discardSamples.fetch_add(1) + 1;
        if (sample <= 16 || sample % 300 == 0) {
            GLint draw = 0, read = 0, viewport[4] = {}, bits = 0;
            GLfloat clear = 0;
            GLint function = 0;
            glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw);
            glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read);
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_DEPTH_BITS, &bits);
            glGetIntegerv(GL_DEPTH_FUNC, &function);
            glGetFloatv(GL_DEPTH_CLEAR_VALUE, &clear);
            GLint type = 0, name = 0;
            if (draw != 0) {
                glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, &type);
                if (type != GL_NONE) {
                    glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER,
                        GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &name);
                }
            }
            __android_log_print(ANDROID_LOG_INFO, kTag,
                "CI59 renderer depth discard sample=%u context=%p target=0x%x draw=%d read=%d "
                "viewport=%d,%d,%d,%d depthBits=%d attachmentType=0x%x attachment=%d "
                "depthFunc=0x%x clear=%g caller=%p",
                sample, eglGetCurrentContext(), target, draw, read,
                viewport[0], viewport[1], viewport[2], viewport[3], bits, type, name,
                function, clear, __builtin_return_address(0));
        }
    }
    originalInvalidate(target, count, attachments);
}

bool pointerBelongsTo(void* function, const char* library) {
    Dl_info info{};
    return function != nullptr && dladdr(function, &info) != 0 &&
        info.dli_fname != nullptr && std::strstr(info.dli_fname, library) != nullptr;
}

template<class Function>
bool replaceSlot(uintptr_t address, Function expected, Function replacement) {
    const long pageSize = sysconf(_SC_PAGESIZE);
    if (pageSize <= 0) return false;
    auto* page = reinterpret_cast<void*>(address & ~(static_cast<uintptr_t>(pageSize) - 1));
    if (mprotect(page, static_cast<size_t>(pageSize), PROT_READ | PROT_WRITE) != 0) return false;
    const bool changed = __atomic_compare_exchange_n(reinterpret_cast<Function*>(address),
        &expected, replacement, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
    // 两个经反汇编确认的槽都处于 GNU_RELRO 的只读页。
    const int restored = mprotect(page, static_cast<size_t>(pageSize), PROT_READ);
    if (restored != 0) __android_log_print(ANDROID_LOG_ERROR, kTag,
        "CI59 renderer RELRO restore failed address=%p", reinterpret_cast<void*>(address));
    return changed;
}
}

bool install(SwapObserver observer, EndPassObserver endPass) {
    std::lock_guard<std::mutex> lock(installMutex);
    uintptr_t base = 0;
    dl_iterate_phdr(findModule, &base);
    if (base == 0 || observer == nullptr) return false;
    void* engine = nullptr;
    std::memcpy(&engine, reinterpret_cast<void*>(base + kEngineGlobal), sizeof(engine));
    if (engine == nullptr) return false;
    // Engine::getBackend() 的设备指令为 ldrb w0,[x0,#0xc]; ret。
    const uint8_t backend = *(static_cast<uint8_t*>(engine) + 0xc);
    if (backend != 1) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "CI59 verified renderer backend=%u; GLES hook skipped", backend);
        return false;
    }
    // FEngine 的首字段为实际 driver；同时核验构造器写入的精确 vtable，
    // 不扫描堆、不猜其他 ABI。nInit 后再次调用时也修复新建 driver 的缓存。
    void* driver = nullptr;
    std::memcpy(&driver, engine, sizeof(driver));
    if (driver == nullptr || *reinterpret_cast<uintptr_t*>(driver) != base + kDriverVtable) return false;
    auto* cachedSlot = reinterpret_cast<Invalidate*>(static_cast<uint8_t*>(driver) + kDriverInvalidate);
    if (installed.load()) {
        const bool repaired = replaceExpectedDispatch(cachedSlot, originalInvalidate, onInvalidate);
        __android_log_print(repaired ? ANDROID_LOG_INFO : ANDROID_LOG_WARN, kTag,
            "CI82 renderer cached dispatch refresh driver=%p cache=%p ready=%d", driver,
            reinterpret_cast<void*>(__atomic_load_n(cachedSlot, __ATOMIC_ACQUIRE)), repaired);
        return repaired;
    }
    auto invalidate = *reinterpret_cast<Invalidate*>(base + kInvalidateSlot);
    if (!pointerBelongsTo(reinterpret_cast<void*>(invalidate), "libGLESv2.so")) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "CI61 verified renderer invalidate slot rejected=%p", invalidate);
        return false;
    }
    if (__atomic_load_n(cachedSlot, __ATOMIC_ACQUIRE) != invalidate) return false;
    uintptr_t vendorBase = 0;
    dl_iterate_phdr(findVendorHook, &vendorBase);
    if (vendorBase == 0) return false;
    // 以下地址来自本机 vendor ELF 的导出符号和反汇编。使用其自身的 TLS 调用链，
    // 不能通过 RTLD_DEFAULT 取得模块自带 ByteHook 的同名函数。
    const auto mode = reinterpret_cast<int (*)()>(vendorBase + 0xb3d8);
    if (mode() != 0) return false;
    auto hookSingle = reinterpret_cast<HookSingle>(vendorBase + 0xb19c);
    vendorPrevious = reinterpret_cast<GetPrevious>(vendorBase + 0xb2e8);
    vendorPopStack = reinterpret_cast<PopStack>(vendorBase + 0xb398);
    originalInvalidate = invalidate;
    observeSwap = observer;
    observeEndPass = endPass;
    endDiscardReturn = base + 0x3698e0;
    const bool cachedHook = replaceExpectedDispatch(cachedSlot, invalidate, onInvalidate);
    if (!cachedHook) return false;
    const bool discardHook = replaceSlot(base + kInvalidateSlot, invalidate, onInvalidate);
    if (!discardHook) {
        replaceExpectedDispatch(cachedSlot, onInvalidate, invalidate);
        return false;
    }
    void* task = hookSingle(frameworkName.c_str(), nullptr, "eglSwapBuffers",
        reinterpret_cast<void*>(onVendorSwap), onVendorHooked, nullptr);
    if (task == nullptr) {
        replaceSlot(base + kInvalidateSlot, onInvalidate, invalidate);
        replaceExpectedDispatch(cachedSlot, onInvalidate, invalidate);
        return false;
    }
    installed.store(true);
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "CI61 verified renderer registered buildId=e4014aed2593c4269e2960bc4c588cace22d57ff "
        "base=%p engine=%p backend=%u vendor=%p task=%p slot=%p discard=%d driver=%p cached=%d",
        reinterpret_cast<void*>(base), engine, backend, reinterpret_cast<void*>(vendorBase), task,
        *reinterpret_cast<void**>(base + kSwapSlot), discardHook, driver, cachedHook);
    return true;
}

bool copyCurrentContextAttributes(std::vector<EGLint>& attributes) {
    std::lock_guard<std::mutex> lock(installMutex);
    uintptr_t base = 0;
    dl_iterate_phdr(findModule, &base);
    if (!installed.load() || !base) return false;
    auto* engine = *reinterpret_cast<uint8_t**>(base + kEngineGlobal);
    if (!engine) return false;
    auto* driver = *reinterpret_cast<uint8_t**>(engine);
    if (!driver || *reinterpret_cast<uintptr_t*>(driver) != base + kDriverVtable) return false;
    // Verified constructor 0x36c178/0x36c198 stores PlatformEGL at driver+0xc8.
    // Its createDriver path saves display/context at +0x50/+0x58 and the exact
    // successful EGL attribute vector at +0x80/+0x88 (0x363344..0x36335c).
    auto* platform = *reinterpret_cast<uint8_t**>(driver + 0xc8);
    if (!platform || *reinterpret_cast<uintptr_t*>(platform) != base + 0x413f90) return false;
    const auto display = *reinterpret_cast<EGLDisplay*>(platform + 0x50);
    const auto context = *reinterpret_cast<EGLContext*>(platform + 0x58);
    if (display != eglGetCurrentDisplay() || context != eglGetCurrentContext()) return false;
    const auto begin = *reinterpret_cast<uintptr_t*>(platform + 0x80);
    const auto end = *reinterpret_cast<uintptr_t*>(platform + 0x88);
    if (!begin || end < begin || end - begin > 256 || (end - begin) % sizeof(EGLint)) return false;
    const auto* values = reinterpret_cast<const EGLint*>(begin);
    const size_t count = (end - begin) / sizeof(EGLint);
    for (size_t index = 0; index < count; index += 2) {
        if (values[index] == EGL_NONE) {
            attributes.assign(values, values + index + 1);
            return !attributes.empty();
        }
        if (index + 1 >= count) return false;
    }
    return false;
}
}
