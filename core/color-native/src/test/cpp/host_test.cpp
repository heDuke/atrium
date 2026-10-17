// Host (desktop) test for color_core.cpp. No gtest: plain checks and an exit code.
// Verifies the shared golden file written by the Kotlin GoldenTest (±1 per channel), so a pass means
// the native and Kotlin implementations agree. Build/run: scripts/verify-native-host.sh
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "../../main/cpp/color_core.h"

using atrium::kRoleCount;

static int failures = 0;

static bool close_argb(uint32_t a, uint32_t b) {
  for (int s : {24, 16, 8, 0}) {
    int d = static_cast<int>((a >> s) & 0xFF) - static_cast<int>((b >> s) & 0xFF);
    if (d < -1 || d > 1) return false;
  }
  return true;
}

// Same formulas as GoldenFixtures.image() in Kotlin.
static std::vector<uint32_t> image(const std::string& name, int& w, int& h) {
  std::vector<uint32_t> px;
  auto make = [&](int ww, int hh, auto f) {
    w = ww; h = hh; px.resize(static_cast<size_t>(ww) * hh);
    for (int y = 0; y < hh; ++y) for (int x = 0; x < ww; ++x) px[y * ww + x] = f(x, y);
  };
  if (name == "stripes") {
    make(100, 100, [](int x, int y) -> uint32_t {
      int k = (x + y * 100) % 10;
      return k < 7 ? 0xFF1E88E5u : (k < 9 ? 0xFFE53935u : 0xFF808080u);
    });
  } else if (name == "gradient") {
    make(200, 150, [](int x, int y) -> uint32_t {
      uint32_t r = x * 255 / 199, g = y * 255 / 149;
      return 0xFF000000u | (r << 16) | (g << 8) | 128u;
    });
  } else if (name == "ring") {
    make(48, 48, [](int x, int y) -> uint32_t {
      int dx = x - 24, dy = y - 24, d2 = dx * dx + dy * dy;
      return (d2 >= 200 && d2 <= 400) ? 0xFF43A047u : 0u;
    });
  } else if (name == "gray") {
    make(64, 64, [](int x, int) -> uint32_t {
      uint32_t v = 40 + x * 2;
      return 0xFF000000u | (v << 16) | (v << 8) | v;
    });
  } else {
    std::fprintf(stderr, "unknown image %s\n", name.c_str());
    std::exit(2);
  }
  return px;
}

int main(int argc, char** argv) {
  const char* path = argc > 1 ? argv[1] : "core/color/golden/color.golden";
  std::ifstream in(path);
  if (!in) { std::fprintf(stderr, "cannot open %s\n", path); return 2; }
  std::string line;
  int lines = 0;
  while (std::getline(in, line)) {
    if (line.empty()) continue;
    ++lines;
    std::istringstream ss(line);
    std::string kind;
    ss >> kind;
    if (kind == "scheme") {
      std::string seed_hex; float contrast;
      ss >> seed_hex >> contrast;
      uint32_t seed = static_cast<uint32_t>(std::stoul(seed_hex, nullptr, 16));
      uint32_t roles[kRoleCount];
      atrium::generate_roles(seed, contrast, roles);
      for (int i = 0; i < kRoleCount; ++i) {
        std::string hx; ss >> hx;
        uint32_t want = static_cast<uint32_t>(std::stoul(hx, nullptr, 16));
        if (!close_argb(want, roles[i])) {
          std::fprintf(stderr, "FAIL scheme %s %.1f role %d: want %08X got %08X\n", seed_hex.c_str(), contrast, i, want, roles[i]);
          ++failures;
        }
      }
      // Invariants independent of the golden file.
      if (roles[22] != 0xFF000000u) { std::fprintf(stderr, "FAIL background not black\n"); ++failures; }
      double min_ratio = 4.5 + 2.5 * contrast;
      const int pairs[][2] = {{3, 0}, {4, 2}, {8, 5}, {9, 7}, {13, 10}, {14, 12}, {18, 17}, {19, 17}, {23, 22}, {27, 24}, {28, 26}};
      for (auto& p : pairs) {
        double r = atrium::contrast_ratio(roles[p[0]], roles[p[1]]);
        if (r + 1e-9 < min_ratio) {
          std::fprintf(stderr, "FAIL contrast %s roles %d/%d = %.2f\n", seed_hex.c_str(), p[0], p[1], r);
          ++failures;
        }
      }
    } else if (kind == "extract") {
      std::string name, hx;
      ss >> name >> hx;
      int w, h;
      auto px = image(name, w, h);
      uint32_t got = atrium::extract_seed_argb(px.data(), w, h, w);
      uint32_t want = static_cast<uint32_t>(std::stoul(hx, nullptr, 16));
      if (got != want) {
        std::fprintf(stderr, "FAIL extract %s: want %08X got %08X\n", name.c_str(), want, got);
        ++failures;
      }
    }
  }
  if (lines == 0) { std::fprintf(stderr, "empty golden file\n"); return 2; }
  std::printf("%d golden lines, %d failures\n", lines, failures);
  return failures == 0 ? 0 : 1;
}
