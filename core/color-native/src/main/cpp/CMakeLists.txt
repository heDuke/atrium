cmake_minimum_required(VERSION 3.22.1)
project(atrium_color CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(atrium_color SHARED color_core.cpp jni_bridge.cpp)
# -ffp-contract=off: no fused multiply-add, so results match the JVM (and the host test) bit-for-bit.
target_compile_options(atrium_color PRIVATE -O2 -Wall -Wextra -Werror -ffp-contract=off -fvisibility=hidden)
target_link_libraries(atrium_color PRIVATE jnigraphics log)
# 16 KB page alignment (Android 15+ requirement for native libraries).
target_link_options(atrium_color PRIVATE "-Wl,-z,max-page-size=16384")
