// Atrium native color core: clean-room CAM16/HCT, tonal scheme and seed extraction.
// Pure C++17 (no Android headers) so it also builds as a host test executable.
// Mirrors core/color/src/main/kotlin/com/alliehe/core/color/{Hct,TonalScheme,SeedExtractor}.kt
// statement by statement; parity is enforced by core/color/golden/color.golden.
#pragma once

#include <cstdint>

namespace atrium {

constexpr int kRoleCount = 29;

// [hue, chroma, tone]
void hct_from_argb(uint32_t argb, double out[3]);
uint32_t hct_to_argb(double hue, double chroma, double tone);
double tone_of(uint32_t argb);
double contrast_ratio(uint32_t a, uint32_t b);

// Fills kRoleCount roles in WearColorRoles order.
void generate_roles(uint32_t seed, float contrast, uint32_t out[kRoleCount]);

uint32_t retone(uint32_t argb, float max_chroma, float tone);

// 15-bit RGB histogram (5 bits per channel), sized 1 << 15.
constexpr int kHistSize = 1 << 15;
inline int hist_bin(uint32_t r, uint32_t g, uint32_t b) {
  return static_cast<int>(((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3));
}
// Scores a filled histogram; returns 0 when no seed qualifies.
uint32_t pick_seed(const uint32_t* hist, uint32_t total);
// Samples ARGB pixels (row stride in pixels) on a <= 64x64 grid; returns 0 when none.
uint32_t extract_seed_argb(const uint32_t* pixels, int width, int height, int stride);
int sample_step(int width, int height);

}  // namespace atrium
