#pragma once

namespace verified_renderer {
// 只替换经过身份核验的原始入口；已安装时幂等，第三方入口必须保留。
template<class Function>
bool replaceExpectedDispatch(Function* slot, Function expected, Function replacement) {
    Function current = __atomic_load_n(slot, __ATOMIC_ACQUIRE);
    if (current == replacement) return true;
    return current == expected && __atomic_compare_exchange_n(slot, &current, replacement,
        false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
}
}
