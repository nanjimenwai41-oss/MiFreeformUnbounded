#pragma once

#include <cstdint>
#include <vector>

namespace gpu_depth {
// depthRGBA 是 shader 按 floatBitsToUint 编码的原始深度，避免逆向 Z 在 uint16 量化中归零。
using Frame = void (*)(int, int, int64_t, std::vector<uint8_t>&&);
void initialize(Frame frame);
void setEnabled(bool enabled);
void requestReset();
void beforeDepthDiscard();
void atSwap();
void publisherFinished();
}
