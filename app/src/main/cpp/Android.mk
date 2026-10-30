LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := freeformnative
LOCAL_SRC_FILES := freeformnative.cpp VerifiedRendererHook.cpp GpuDepthCapture.cpp AsyncMaskReadback.cpp
LOCAL_CPPFLAGS := -std=c++17 -fvisibility=hidden
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv3 -ldl
include $(BUILD_SHARED_LIBRARY)
