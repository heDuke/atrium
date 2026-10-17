// Clean-room implementation from the published CIE / CAM16 formulas (CIE 15:2004 L*, Li et al. 2017
// "Comprehensive color solutions: CAM16, CAT16, and CAM16-UCS" incl. the p1..p5 inverse). Not derived
// from any third-party color library. Keep operation order identical to Hct.kt / TonalScheme.kt /
// SeedExtractor.kt so both implementations agree to ±1 per channel.
#include "color_core.h"

#include <algorithm>
#include <cmath>

namespace atrium {
namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr double kEpsilon = 216.0 / 24389.0;
constexpr double kKappa = 24389.0 / 27.0;
constexpr int kJIterations = 26;
constexpr int kChromaIterations = 14;
constexpr double kGamutEps = 1e-4;
constexpr double kSurroundC = 0.69;
constexpr double kNc = 1.0;
constexpr uint32_t kBlack = 0xFF000000u;

const double kRgbToXyz[9] = {
    0.4124, 0.3576, 0.1805,
    0.2126, 0.7152, 0.0722,
    0.0193, 0.1192, 0.9505,
};
const double kM16[9] = {
    0.401288, 0.650173, -0.051461,
    -0.250268, 1.204414, 0.045854,
    -0.002079, 0.048952, 0.953127,
};
const double kWhite[3] = {95.047, 100.0, 108.883};

void mul3(const double m[9], const double v[3], double out[3]) {
  double x = m[0] * v[0] + m[1] * v[1] + m[2] * v[2];
  double y = m[3] * v[0] + m[4] * v[1] + m[5] * v[2];
  double z = m[6] * v[0] + m[7] * v[1] + m[8] * v[2];
  out[0] = x; out[1] = y; out[2] = z;
}

void invert3(const double m[9], double out[9]) {
  double a = m[0], b = m[1], c = m[2];
  double d = m[3], e = m[4], f = m[5];
  double g = m[6], h = m[7], i = m[8];
  double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  out[0] = (e * i - f * h) / det; out[1] = (c * h - b * i) / det; out[2] = (b * f - c * e) / det;
  out[3] = (f * g - d * i) / det; out[4] = (a * i - c * g) / det; out[5] = (c * d - a * f) / det;
  out[6] = (d * h - e * g) / det; out[7] = (b * g - a * h) / det; out[8] = (a * e - b * d) / det;
}

double y_from_lstar(double lstar) {
  double ft = (lstar + 16.0) / 116.0;
  double ft3 = ft * ft * ft;
  double y = ft3 > kEpsilon ? ft3 : (116.0 * ft - 16.0) / kKappa;
  return y * 100.0;
}

double lstar_from_y(double y) {
  double yr = y / 100.0;
  double f = yr > kEpsilon ? std::cbrt(yr) : (kKappa * yr + 16.0) / 116.0;
  return 116.0 * f - 16.0;
}

double sgn(double x) { return x > 0.0 ? 1.0 : (x < 0.0 ? -1.0 : 0.0); }

struct Viewing {
  double xyz_to_rgb[9];
  double m16_inv[9];
  double yb, la, d, drgb[3], fl, n, z, nbb, aw, chroma_factor;

  double adapt(double x) const {
    double p = std::pow(fl * std::fabs(x) / 100.0, 0.42);
    return sgn(x) * 400.0 * p / (p + 27.13) + 0.1;
  }
  double unadapt(double x) const {
    double y = x - 0.1;
    double ay = std::fabs(y);
    if (ay >= 400.0) return NAN;
    return sgn(y) * (100.0 / fl) * std::pow((27.13 * ay) / (400.0 - ay), 1.0 / 0.42);
  }

  Viewing() {
    invert3(kRgbToXyz, xyz_to_rgb);
    invert3(kM16, m16_inv);
    yb = y_from_lstar(50.0);
    la = 200.0 / kPi * yb / 100.0;
    n = yb / kWhite[1];
    z = 1.48 + std::sqrt(n);
    nbb = 0.725 / std::pow(n, 0.2);
    chroma_factor = std::pow(1.64 - std::pow(0.29, n), 0.73);
    d = std::clamp(1.0 * (1.0 - (1.0 / 3.6) * std::exp((-la - 42.0) / 92.0)), 0.0, 1.0);
    double rgb_w[3];
    mul3(kM16, kWhite, rgb_w);
    for (int i = 0; i < 3; ++i) drgb[i] = d * (100.0 / rgb_w[i]) + 1.0 - d;
    double k = 1.0 / (5.0 * la + 1.0);
    double k4 = k * k * k * k;
    fl = k4 * la + 0.1 * (1.0 - k4) * (1.0 - k4) * std::cbrt(5.0 * la);
    double ra = adapt(drgb[0] * rgb_w[0]);
    double ga = adapt(drgb[1] * rgb_w[1]);
    double ba = adapt(drgb[2] * rgb_w[2]);
    aw = (2.0 * ra + ga + ba / 20.0 - 0.305) * nbb;
  }
};

const Viewing& vc() {
  static const Viewing v;
  return v;
}

double linearize(int c) {
  double v = c / 255.0;
  double l = v <= 0.04045 ? v / 12.92 : std::pow((v + 0.055) / 1.055, 2.4);
  return l * 100.0;
}

// linear 0..100 -> 0..255; nearbyint == Kotlin's round() (half to even).
int delinearize(double l) {
  double v = l / 100.0;
  double e = v <= 0.0031308 ? v * 12.92 : 1.055 * std::pow(v, 1.0 / 2.4) - 0.055;
  int r = static_cast<int>(std::nearbyint(e * 255.0));
  return std::clamp(r, 0, 255);
}

uint32_t make_argb(int r, int g, int b) {
  return 0xFF000000u | (static_cast<uint32_t>(r) << 16) | (static_cast<uint32_t>(g) << 8) |
         static_cast<uint32_t>(b);
}

void argb_to_xyz(uint32_t argb, double out[3]) {
  double rgb[3] = {linearize((argb >> 16) & 0xFF), linearize((argb >> 8) & 0xFF), linearize(argb & 0xFF)};
  mul3(kRgbToXyz, rgb, out);
}

uint32_t xyz_to_argb(const double xyz[3]) {
  double lin[3];
  mul3(vc().xyz_to_rgb, xyz, lin);
  return make_argb(delinearize(lin[0]), delinearize(lin[1]), delinearize(lin[2]));
}

uint32_t gray_from_y(double y) {
  int c = delinearize(y);
  return make_argb(c, c, c);
}

double sanitize_hue(double h) {
  double r = h - 360.0 * std::floor(h / 360.0);
  return r >= 360.0 ? 0.0 : r;
}

// CAM16 inverse (J, C, h) -> XYZ. Returns false when the nonlinearity cannot be inverted.
bool cam_to_xyz(double j, double chroma, double h, double out[3]) {
  const Viewing& v = vc();
  if (j <= 0.0) { out[0] = out[1] = out[2] = 0.0; return true; }
  double t = chroma == 0.0 ? 0.0 : std::pow(chroma / (std::sqrt(j / 100.0) * v.chroma_factor), 1.0 / 0.9);
  double big_a = v.aw * std::pow(j / 100.0, 1.0 / (kSurroundC * v.z));
  double p2 = big_a / v.nbb + 0.305;
  double p3 = 21.0 / 20.0;
  double hr = h * kPi / 180.0;
  double s = std::sin(hr);
  double co = std::cos(hr);
  double et = 0.25 * (std::cos(hr + 2.0) + 3.8);
  double a = 0.0, b = 0.0;
  if (t != 0.0) {
    double p1 = (50000.0 / 13.0) * kNc * v.nbb * et / t;
    if (std::fabs(s) >= std::fabs(co)) {
      double p4 = p1 / s;
      b = p2 * (2.0 + p3) * (460.0 / 1403.0) /
          (p4 + (2.0 + p3) * (220.0 / 1403.0) * (co / s) - 27.0 / 1403.0 + p3 * (6300.0 / 1403.0));
      a = b * co / s;
    } else {
      double p5 = p1 / co;
      a = p2 * (2.0 + p3) * (460.0 / 1403.0) /
          (p5 + (2.0 + p3) * (220.0 / 1403.0) - (27.0 / 1403.0 - p3 * (6300.0 / 1403.0)) * (s / co));
      b = a * s / co;
    }
  }
  double ra = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0;
  double ga = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0;
  double ba = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0;
  double rc = v.unadapt(ra);
  double gc = v.unadapt(ga);
  double bc = v.unadapt(ba);
  if (std::isnan(rc) || std::isnan(gc) || std::isnan(bc)) return false;
  double c[3] = {rc / v.drgb[0], gc / v.drgb[1], bc / v.drgb[2]};
  mul3(v.m16_inv, c, out);
  return true;
}

bool solve_for_y(double h, double chroma, double target_y, double out[3]) {
  double lo = 0.0, hi = 100.0;
  double tmp[3];
  for (int k = 0; k < kJIterations; ++k) {
    double j = (lo + hi) / 2.0;
    double y = cam_to_xyz(j, chroma, h, tmp) ? tmp[1] : NAN;
    if (std::isnan(y) || y > target_y) hi = j; else lo = j;
  }
  if (!cam_to_xyz((lo + hi) / 2.0, chroma, h, out)) return false;
  if (std::fabs(out[1] - target_y) > 0.01 * std::max(1.0, target_y)) return false;
  double lin[3];
  mul3(vc().xyz_to_rgb, out, lin);
  for (double c : lin) {
    if (c < -kGamutEps * 100.0 || c > 100.0 + kGamutEps * 100.0) return false;
  }
  return true;
}

}  // namespace

void hct_from_argb(uint32_t argb, double out[3]) {
  const Viewing& v = vc();
  double xyz[3];
  argb_to_xyz(argb, xyz);
  double tone = lstar_from_y(xyz[1]);
  double rgb[3];
  mul3(kM16, xyz, rgb);
  double ra = v.adapt(v.drgb[0] * rgb[0]);
  double ga = v.adapt(v.drgb[1] * rgb[1]);
  double ba = v.adapt(v.drgb[2] * rgb[2]);
  double a = ra - 12.0 * ga / 11.0 + ba / 11.0;
  double b = (ra + ga - 2.0 * ba) / 9.0;
  double hue = std::atan2(b, a) * 180.0 / kPi;
  if (hue < 0.0) hue += 360.0;
  double big_a = (2.0 * ra + ga + ba / 20.0 - 0.305) * v.nbb;
  if (big_a <= 0.0) { out[0] = hue; out[1] = 0.0; out[2] = tone; return; }
  double j = 100.0 * std::pow(big_a / v.aw, kSurroundC * v.z);
  double et = 0.25 * (std::cos(hue * kPi / 180.0 + 2.0) + 3.8);
  double t = (50000.0 / 13.0 * kNc * v.nbb * et * std::hypot(a, b)) / (ra + ga + 21.0 / 20.0 * ba);
  double chroma = std::pow(t, 0.9) * std::sqrt(j / 100.0) * v.chroma_factor;
  out[0] = hue; out[1] = chroma; out[2] = tone;
}

uint32_t hct_to_argb(double hue, double chroma, double tone) {
  if (tone <= 0.0001) return kBlack;
  if (tone >= 99.9999) return 0xFFFFFFFFu;
  double target_y = y_from_lstar(tone);
  double h = sanitize_hue(hue);
  if (chroma < 0.5) return gray_from_y(target_y);
  double xyz[3];
  if (solve_for_y(h, chroma, target_y, xyz)) return xyz_to_argb(xyz);
  double lo = 0.0, hi = chroma;
  double best[3];
  bool have_best = false;
  for (int k = 0; k < kChromaIterations; ++k) {
    double mid = (lo + hi) / 2.0;
    if (solve_for_y(h, mid, target_y, xyz)) {
      best[0] = xyz[0]; best[1] = xyz[1]; best[2] = xyz[2];
      have_best = true;
      lo = mid;
    } else {
      hi = mid;
    }
  }
  return have_best ? xyz_to_argb(best) : gray_from_y(target_y);
}

double tone_of(uint32_t argb) {
  double xyz[3];
  argb_to_xyz(argb, xyz);
  return lstar_from_y(xyz[1]);
}

double contrast_ratio(uint32_t a, uint32_t b) {
  double xa[3], xb[3];
  argb_to_xyz(a, xa);
  argb_to_xyz(b, xb);
  double hi = std::max(xa[1], xb[1]);
  double lo = std::min(xa[1], xb[1]);
  return (hi + 5.0) / (lo + 5.0);
}

namespace {

constexpr double kMinRatio = 4.5;
constexpr double kOutlineMinRatio = 3.0;

uint32_t on_color(double hue, double chroma, double tone, uint32_t background, double min_ratio) {
  bool lighter = tone_of(background) < 50.0;
  double t = tone;
  uint32_t color = hct_to_argb(hue, chroma, t);
  while (contrast_ratio(color, background) < min_ratio) {
    t += lighter ? 1.0 : -1.0;
    if (t < 0.0 || t > 100.0) break;
    color = hct_to_argb(hue, chroma, t);
  }
  return color;
}

void accent(uint32_t* out, int base, double hue, double chroma, double min_ratio) {
  uint32_t acc = hct_to_argb(hue, chroma, 80.0);
  uint32_t container = hct_to_argb(hue, chroma, 30.0);
  out[base] = acc;
  out[base + 1] = hct_to_argb(hue, chroma, 70.0);
  out[base + 2] = container;
  out[base + 3] = on_color(hue, chroma, 20.0, acc, min_ratio);
  out[base + 4] = on_color(hue, chroma, 90.0, container, min_ratio);
}

}  // namespace

void generate_roles(uint32_t seed, float contrast, uint32_t out[kRoleCount]) {
  double hct[3];
  hct_from_argb(seed, hct);
  double h = hct[0];
  double c = hct[1];
  float cc = std::clamp(contrast, 0.0f, 1.0f);
  double min_ratio = kMinRatio + 2.5 * static_cast<double>(cc);
  bool mono = c < 4.0;
  double pc = mono ? 0.0 : std::max(c, 36.0);
  double sc = mono ? 0.0 : 16.0;
  double tc = mono ? 0.0 : 24.0;

  accent(out, 0, h, pc, min_ratio);
  accent(out, 5, h, sc, min_ratio);
  accent(out, 10, h + 60.0, tc, min_ratio);

  uint32_t surf_high = hct_to_argb(h, 4.0, 22.0);
  out[15] = hct_to_argb(h, 4.0, 12.0);
  out[16] = hct_to_argb(h, 4.0, 17.0);
  out[17] = surf_high;
  out[18] = on_color(h, 4.0, 92.0, surf_high, min_ratio);
  out[19] = on_color(h, 8.0, 80.0, surf_high, min_ratio);
  out[20] = on_color(h, 8.0, 60.0, kBlack, kOutlineMinRatio);
  out[21] = hct_to_argb(h, 8.0, 40.0);
  out[22] = kBlack;
  out[23] = on_color(h, 4.0, 95.0, kBlack, min_ratio);

  accent(out, 24, 25.0, 80.0, min_ratio);
}

uint32_t retone(uint32_t argb, float max_chroma, float tone) {
  double hct[3];
  hct_from_argb(argb, hct);
  return hct_to_argb(hct[0], std::min(hct[1], static_cast<double>(max_chroma)), static_cast<double>(tone));
}

int sample_step(int width, int height) {
  int m = std::max(width, height);
  return std::max(1, (m + 63) / 64);
}

uint32_t pick_seed(const uint32_t* hist, uint32_t total) {
  constexpr int kBuckets = 36;
  if (total == 0) return 0;
  double count[kBuckets] = {};
  double chroma_sum[kBuckets] = {};
  double best_score[kBuckets] = {};
  uint32_t best_color[kBuckets] = {};
  for (int bin = 0; bin < kHistSize; ++bin) {
    uint32_t n = hist[bin];
    if (n == 0) continue;
    int r = (((bin >> 10) & 0x1F) << 3) | 4;
    int g = (((bin >> 5) & 0x1F) << 3) | 4;
    int b = ((bin & 0x1F) << 3) | 4;
    uint32_t argb = make_argb(r, g, b);
    double hct[3];
    hct_from_argb(argb, hct);
    if (hct[1] < 15.0 || hct[2] < 12.0 || hct[2] > 92.0) continue;
    int bucket = std::clamp(static_cast<int>(hct[0] / 10.0), 0, kBuckets - 1);
    count[bucket] += static_cast<double>(n);
    chroma_sum[bucket] += hct[1] * n;
    double s = n * hct[1];
    if (s > best_score[bucket]) { best_score[bucket] = s; best_color[bucket] = argb; }
  }
  int winner = -1;
  double winner_score = 0.0;
  for (int i = 0; i < kBuckets; ++i) {
    if (count[i] == 0.0) continue;
    double smoothed = (count[(i + kBuckets - 1) % kBuckets] * 0.5 + count[i] +
                       count[(i + 1) % kBuckets] * 0.5) / total;
    if (count[i] / total < 0.01) continue;
    double score = smoothed * (chroma_sum[i] / count[i]);
    if (score > winner_score) { winner_score = score; winner = i; }
  }
  return winner < 0 ? 0 : best_color[winner];
}

uint32_t extract_seed_argb(const uint32_t* pixels, int width, int height, int stride) {
  if (width <= 0 || height <= 0) return 0;
  static thread_local uint32_t hist[kHistSize];
  std::fill(hist, hist + kHistSize, 0u);
  int step = sample_step(width, height);
  uint32_t total = 0;
  for (int y = 0; y < height; y += step) {
    const uint32_t* row = pixels + static_cast<long>(y) * stride;
    for (int x = 0; x < width; x += step) {
      uint32_t p = row[x];
      if ((p >> 24) >= 128) {
        hist[hist_bin((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF)]++;
        total++;
      }
    }
  }
  return pick_seed(hist, total);
}

}  // namespace atrium
