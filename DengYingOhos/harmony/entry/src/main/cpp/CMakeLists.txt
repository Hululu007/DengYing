cmake_minimum_required(VERSION 3.5.0)
project(entry)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif()

include_directories(${CMAKE_CURRENT_SOURCE_DIR}
                    ${CMAKE_CURRENT_SOURCE_DIR}/include)

# 对照实验：单 K/N 运行时（只 libdengyui.so），不链 libdengying.so。
add_library(entry SHARED napi_init.cpp)

target_link_libraries(entry PUBLIC libace_napi.z.so libhilog_ndk.z.so)

set(DENGYUI_SO ${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/libdengyui.so)
add_library(dengyui SHARED IMPORTED)
set_target_properties(dengyui PROPERTIES IMPORTED_LOCATION ${DENGYUI_SO})
target_link_libraries(entry PUBLIC dengyui)

set(SKIKOBRIDGE_SO ${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/libskikobridge.so)
if(EXISTS ${SKIKOBRIDGE_SO})
    add_library(skikobridge SHARED IMPORTED)
    set_target_properties(skikobridge PROPERTIES IMPORTED_LOCATION ${SKIKOBRIDGE_SO})
    target_link_libraries(entry PUBLIC skikobridge)
    message(STATUS "skikobridge linked: ${SKIKOBRIDGE_SO}")
else()
    message(WARNING "skikobridge not found at ${SKIKOBRIDGE_SO}")
endif()

target_link_libraries(entry PUBLIC libdeviceinfo_ndk.z.so librawfile.z.so
  libicu.so libc++_shared.so)
