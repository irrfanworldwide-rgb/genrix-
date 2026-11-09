# The echo canceller Genrix uses to let the user talk over a reply on the loudspeaker.
cmake_minimum_required(VERSION 3.22.1)
project(genrix_aec C)

add_library(genrix_aec SHARED
    aec_jni.c
    speexdsp/mdf.c
    speexdsp/preprocess.c
    speexdsp/filterbank.c
    speexdsp/fftwrap.c
    speexdsp/kiss_fft.c
    speexdsp/kiss_fftr.c)
target_include_directories(genrix_aec PRIVATE speexdsp speexdsp/include)
target_compile_definitions(genrix_aec PRIVATE HAVE_CONFIG_H)
target_compile_options(genrix_aec PRIVATE -O2 -ffast-math -Wno-unused-parameter)
target_link_libraries(genrix_aec m)
# 16 KB pages (newer Android versions require it for native libraries).
target_link_options(genrix_aec PRIVATE "-Wl,-z,max-page-size=16384")
