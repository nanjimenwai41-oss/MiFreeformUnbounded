#include "../../app/src/main/cpp/RendererDispatchPolicy.h"

using Function = int (*)();
int original() { return 1; }
int observer() { return 2; }
int thirdParty() { return 3; }

// 不依赖 Android/CRT，允许在宿主上实际执行同一原子替换策略。
extern "C" int mainCRTStartup() {
    Function got = original;
    Function cached = got;
    if (!verified_renderer::replaceExpectedDispatch(&got, original, observer)) return 1;
    // 回归现场：GOT 已安装，但 driver 保留旧入口，采集仍不会执行。
    if (got() != 2 || cached() != 1) return 2;
    if (!verified_renderer::replaceExpectedDispatch(&cached, original, observer) || cached() != 2) return 3;
    if (!verified_renderer::replaceExpectedDispatch(&cached, original, observer)) return 4;
    Function foreign = thirdParty;
    if (verified_renderer::replaceExpectedDispatch(&foreign, original, observer) || foreign() != 3) return 5;
    // 下一次 renderer 重建后，新的缓存同样必须恢复。
    cached = original;
    return verified_renderer::replaceExpectedDispatch(&cached, original, observer) && cached() == 2 ? 0 : 6;
}
