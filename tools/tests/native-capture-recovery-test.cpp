#include "../../app/src/main/cpp/CaptureRecoveryPolicy.h"

constexpr bool recoversAfterTransientErrors() {
    gpu_depth::CaptureRecoveryPolicy state;
    state.failed(100);
    state.failed(200);
    state.failed(300);
    if (state.canCapture(301)) return false;
    if (!state.canCapture(1'000'000'300LL)) return false;
    state.failed(1'000'000'300LL);
    if (state.canCapture(1'000'000'301LL)) return false;
    state.reset();
    return state.canCapture(1'000'000'301LL) && state.failures == 0;
}

static_assert(recoversAfterTransientErrors(), "GL temporary errors must recover and surface reset must bypass backoff");
