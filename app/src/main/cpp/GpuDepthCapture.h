#pragma once

#include <cstdint>
#include <vector>

namespace gpu_depth {
// Four binary alpha pixels per RGBA texel; rows are GL bottom-origin and padded to four bytes.
using Frame = void (*)(int, int, int64_t, std::vector<uint8_t>&&);
void initialize(Frame frame);
void setEnabled(bool enabled);
void requestReset();
void beforeDepthDiscard();
void atSwap();
int64_t takeSwapCapture();
}
