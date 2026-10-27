#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>

namespace verified_renderer {
using SwapObserver = void (*)(const char*, EGLDisplay, EGLSurface);
using EndPassObserver = void (*)();
bool install(SwapObserver observer, EndPassObserver endPass);
}
