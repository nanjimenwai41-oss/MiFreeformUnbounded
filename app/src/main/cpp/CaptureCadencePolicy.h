#pragma once

#include <stdint.h>

namespace gpu_depth {
/** Phase-locked cap: small renderer jitter must not turn 120 Hz into 60 Hz. */
struct CaptureCadencePolicy {
    static constexpr int64_t interval = 8'333'333;
    static constexpr int64_t tolerance = 750'000;
    int64_t deadline = 0;

    void reset() { deadline = 0; }

    bool accept(int64_t timestamp) {
        if (timestamp + tolerance < deadline) return false;
        // Keep the original phase while frames arrive regularly. After a pause,
        // resume immediately without accumulating a backlog of missed captures.
        deadline = timestamp > deadline + interval ? timestamp + interval : deadline + interval;
        return true;
    }
};
}
