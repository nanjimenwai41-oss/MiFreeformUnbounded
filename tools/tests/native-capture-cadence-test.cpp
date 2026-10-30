#include "../../app/src/main/cpp/CaptureCadencePolicy.h"

// Run the production scheduler directly on the host without Android or a CRT.
extern "C" int mainCRTStartup() {
    gpu_depth::CaptureCadencePolicy cadence;
    // Alternating sub-ms jitter previously dropped almost every other frame.
    for (int i = 0; i < 1'200; ++i) {
        const auto timestamp = 1'000'000'000LL + i * cadence.interval + (i % 2 ? -300'000 : 300'000);
        if (!cadence.accept(timestamp)) return 1;
    }
    cadence.reset();
    int accepted = 0;
    for (int i = 0; i < 2'400; ++i) accepted += cadence.accept(1'000'000'000LL + i * cadence.interval / 2);
    if (accepted != 1'200) return 2;
    cadence.reset();
    for (int i = 0; i < 600; ++i) if (!cadence.accept(1'000'000'000LL + i * cadence.interval * 2)) return 3;
    if (!cadence.accept(100'000'000'000LL)) return 4;
    if (cadence.accept(100'000'000'001LL)) return 5;
    return 0;
}
