#pragma once

namespace gpu_depth {
// 连续 GL 错误只退避一秒，不能把一次临时失效变成进程生命周期内永久失效。
struct CaptureRecoveryPolicy {
    unsigned failures = 0;
    long long retryAfterNanos = 0;

    constexpr bool canCapture(long long now) const {
        return failures < 3 || now >= retryAfterNanos;
    }
    constexpr void failed(long long now) {
        ++failures;
        if (failures >= 3) retryAfterNanos = now + 1'000'000'000LL;
    }
    constexpr void reset() {
        failures = 0;
        retryAfterNanos = 0;
    }
};
}
