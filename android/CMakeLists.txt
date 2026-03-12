cmake_minimum_required(VERSION 3.22.1)
project(ReactNativePressRipple)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Create the shared C++ library
add_library(ReactNativePressRipple SHARED cpp-adapter.cpp)

# Include nitrogen-generated CMake config — adds all generated .cpp sources,
# include dirs, Folly flags, find_package calls, and target_link_libraries.
include(${CMAKE_SOURCE_DIR}/../nitrogen/generated/android/ReactNativePressRipple+autolinking.cmake)
