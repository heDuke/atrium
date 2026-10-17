// JNI surface for com.alliehe.core.color.nativeimpl.NativeColorCore.
#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define ATRIUM_NEON 1
#endif

#include "color_core.h"

namespace {

constexpr jlong kUnsupported = -1;

inline void add_pixel(uint32_t* hist, uint32_t& total, uint32_t r, uint32_t g, uint32_t b, uint32_t a,
                      bool premul) {
  if (a < 128) return;
  if (premul && a < 255) {
    r = std::min<uint32_t>(255, (r * 255 + a / 2) / a);
    g = std::min<uint32_t>(255, (g * 255 + a / 2) / a);
    b = std::min<uint32_t>(255, (b * 255 + a / 2) / a);
  }
  hist[atrium::hist_bin(r, g, b)]++;
  total++;
}

// RGBA_8888 in memory is R, G, B, A bytes. Same ≤64×64 sampling grid as SeedExtractor.kt.
uint32_t extract_rgba8888(const uint8_t* base, int width, int height, int stride_bytes, bool premul) {
  static thread_local uint32_t hist[atrium::kHistSize];
  std::fill(hist, hist + atrium::kHistSize, 0u);
  uint32_t total = 0;
  const int step = atrium::sample_step(width, height);
  for (int y = 0; y < height; y += step) {
    const uint8_t* row = base + static_cast<long>(y) * stride_bytes;
    int x = 0;
#ifdef ATRIUM_NEON
    if (step == 1) {
      // De-interleave 16 pixels per iteration and build their 15-bit bins in vector registers;
      // only the scatter into the histogram stays scalar.
      uint16_t bins[16];
      uint8_t alpha[16];
      for (; x + 16 <= width; x += 16) {
        uint8x16x4_t px = vld4q_u8(row + x * 4);
        uint8x16_t r5 = vshrq_n_u8(px.val[0], 3);
        uint8x16_t g5 = vshrq_n_u8(px.val[1], 3);
        uint8x16_t b5 = vshrq_n_u8(px.val[2], 3);
        uint16x8_t lo = vorrq_u16(vorrq_u16(vshlq_n_u16(vmovl_u8(vget_low_u8(r5)), 10),
                                            vshlq_n_u16(vmovl_u8(vget_low_u8(g5)), 5)),
                                  vmovl_u8(vget_low_u8(b5)));
        uint16x8_t hi = vorrq_u16(vorrq_u16(vshlq_n_u16(vmovl_u8(vget_high_u8(r5)), 10),
                                            vshlq_n_u16(vmovl_u8(vget_high_u8(g5)), 5)),
                                  vmovl_u8(vget_high_u8(b5)));
        vst1q_u16(bins, lo);
        vst1q_u16(bins + 8, hi);
        vst1q_u8(alpha, px.val[3]);
        for (int i = 0; i < 16; ++i) {
          uint8_t a = alpha[i];
          if (a == 255) {
            hist[bins[i]]++;
            total++;
          } else if (a >= 128) {
            const uint8_t* p = row + (x + i) * 4;
            add_pixel(hist, total, p[0], p[1], p[2], a, premul);
          }
        }
      }
    }
#endif
    for (; x < width; x += step) {
      const uint8_t* p = row + x * 4;
      add_pixel(hist, total, p[0], p[1], p[2], p[3], premul);
    }
  }
  return atrium::pick_seed(hist, total);
}

}  // namespace

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_alliehe_core_color_nativeimpl_NativeColorCore_nativeGenerate(JNIEnv* env, jclass, jint seed,
                                                                       jfloat contrast) {
  uint32_t roles[atrium::kRoleCount];
  atrium::generate_roles(static_cast<uint32_t>(seed), contrast, roles);
  jintArray out = env->NewIntArray(atrium::kRoleCount);
  if (out == nullptr) return nullptr;
  env->SetIntArrayRegion(out, 0, atrium::kRoleCount, reinterpret_cast<const jint*>(roles));
  return out;
}

JNIEXPORT jint JNICALL
Java_com_alliehe_core_color_nativeimpl_NativeColorCore_nativeRetone(JNIEnv*, jclass, jint argb,
                                                                     jfloat max_chroma, jfloat tone) {
  return static_cast<jint>(atrium::retone(static_cast<uint32_t>(argb), max_chroma, tone));
}

JNIEXPORT jint JNICALL
Java_com_alliehe_core_color_nativeimpl_NativeColorCore_nativeExtractPixels(JNIEnv* env, jclass,
                                                                            jintArray pixels, jint width,
                                                                            jint height) {
  if (width <= 0 || height <= 0 || env->GetArrayLength(pixels) < width * height) return 0;
  jint* data = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels, nullptr));
  if (data == nullptr) return 0;
  uint32_t seed = atrium::extract_seed_argb(reinterpret_cast<const uint32_t*>(data), width, height, width);
  env->ReleasePrimitiveArrayCritical(pixels, data, JNI_ABORT);
  return static_cast<jint>(seed);
}

// Returns the seed as an unsigned 32-bit value (0 = no seed), or -1 when the bitmap cannot be locked
// (hardware bitmap, non-RGBA_8888 format): the caller then falls back to the copying Kotlin path.
JNIEXPORT jlong JNICALL
Java_com_alliehe_core_color_nativeimpl_NativeColorCore_nativeExtractBitmap(JNIEnv* env, jclass,
                                                                            jobject bitmap) {
  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return kUnsupported;
  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return kUnsupported;
  if ((info.flags & ANDROID_BITMAP_FLAGS_IS_HARDWARE) != 0) return kUnsupported;
  void* pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
    return kUnsupported;
  }
  bool premul = (info.flags & ANDROID_BITMAP_FLAGS_ALPHA_MASK) == ANDROID_BITMAP_FLAGS_ALPHA_PREMUL;
  uint32_t seed = extract_rgba8888(static_cast<const uint8_t*>(pixels), static_cast<int>(info.width),
                                   static_cast<int>(info.height), static_cast<int>(info.stride), premul);
  AndroidBitmap_unlockPixels(env, bitmap);
  return static_cast<jlong>(seed);
}

}  // extern "C"
