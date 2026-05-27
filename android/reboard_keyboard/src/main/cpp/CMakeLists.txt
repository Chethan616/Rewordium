#
# Copyright (C) 2026 The ReBoard Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.22)

project(rewordium_latinime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ---------------------------------------------------------------------------
# AOSP LatinIME native source (Apache 2.0, vendored as a git submodule at
# android-16.0.0_r4). The submodule lives untouched at aosp/ — we never
# modify those files. Our own JNI shim (in jni/) calls into the AOSP pure
# C++ classes; AOSP's own JNI entry files (com_android_inputmethod_*.cpp
# and jni_common.cpp) are intentionally excluded so we don't bake AOSP's
# package names into the runtime symbol table.
#
# The file list mirrors LATIN_IME_CORE_SRC_FILES in
# aosp/native/jni/Android.bp. Keep them in sync when bumping the AOSP pin.
# ---------------------------------------------------------------------------

set(AOSP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/aosp/native/jni)

set(AOSP_CORE_SOURCES
    # dictionary/header
    ${AOSP_ROOT}/src/dictionary/header/header_policy.cpp
    ${AOSP_ROOT}/src/dictionary/header/header_read_write_utils.cpp
    # dictionary/property
    ${AOSP_ROOT}/src/dictionary/property/ngram_context.cpp
    # dictionary/structure
    ${AOSP_ROOT}/src/dictionary/structure/dictionary_structure_with_buffer_policy_factory.cpp
    # dictionary/structure/pt_common
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/bigram/bigram_list_read_write_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/dynamic_pt_gc_event_listeners.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/dynamic_pt_reading_helper.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/dynamic_pt_reading_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/dynamic_pt_updating_helper.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/dynamic_pt_writing_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/patricia_trie_reading_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/pt_common/shortcut/shortcut_list_reading_utils.cpp
    # dictionary/structure/v2
    ${AOSP_ROOT}/src/dictionary/structure/v2/patricia_trie_policy.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v2/ver2_patricia_trie_node_reader.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v2/ver2_pt_node_array_reader.cpp
    # dictionary/structure/v4
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_dict_buffers.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_dict_constants.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_patricia_trie_node_reader.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_patricia_trie_node_writer.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_patricia_trie_policy.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_patricia_trie_reading_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_patricia_trie_writing_helper.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/ver4_pt_node_array_reader.cpp
    # dictionary/structure/v4/content
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/dynamic_language_model_probability_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/language_model_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/language_model_dict_content_global_counters.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/shortcut_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/sparse_table_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/v4/content/terminal_position_lookup_table.cpp
    # dictionary/utils
    ${AOSP_ROOT}/src/dictionary/utils/buffer_with_extendable_buffer.cpp
    ${AOSP_ROOT}/src/dictionary/utils/byte_array_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/dict_file_writing_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/file_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/forgetting_curve_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/format_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/mmapped_buffer.cpp
    ${AOSP_ROOT}/src/dictionary/utils/multi_bigram_map.cpp
    ${AOSP_ROOT}/src/dictionary/utils/probability_utils.cpp
    ${AOSP_ROOT}/src/dictionary/utils/sparse_table.cpp
    ${AOSP_ROOT}/src/dictionary/utils/trie_map.cpp
    # dictionary/structure/backward/v402
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_dict_buffers.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_dict_constants.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_patricia_trie_node_reader.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_patricia_trie_node_writer.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_patricia_trie_policy.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_patricia_trie_reading_utils.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_patricia_trie_writing_helper.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/ver4_pt_node_array_reader.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/content/bigram_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/content/probability_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/content/shortcut_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/content/sparse_table_dict_content.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/content/terminal_position_lookup_table.cpp
    ${AOSP_ROOT}/src/dictionary/structure/backward/v402/bigram/ver4_bigram_list_policy.cpp
    # suggest/core
    ${AOSP_ROOT}/src/suggest/core/suggest.cpp
    ${AOSP_ROOT}/src/suggest/core/dicnode/dic_node.cpp
    ${AOSP_ROOT}/src/suggest/core/dicnode/dic_node_utils.cpp
    ${AOSP_ROOT}/src/suggest/core/dicnode/dic_nodes_cache.cpp
    ${AOSP_ROOT}/src/suggest/core/dictionary/dictionary.cpp
    ${AOSP_ROOT}/src/suggest/core/dictionary/dictionary_utils.cpp
    ${AOSP_ROOT}/src/suggest/core/dictionary/digraph_utils.cpp
    ${AOSP_ROOT}/src/suggest/core/dictionary/error_type_utils.cpp
    ${AOSP_ROOT}/src/suggest/core/layout/additional_proximity_chars.cpp
    ${AOSP_ROOT}/src/suggest/core/layout/proximity_info.cpp
    ${AOSP_ROOT}/src/suggest/core/layout/proximity_info_params.cpp
    ${AOSP_ROOT}/src/suggest/core/layout/proximity_info_state.cpp
    ${AOSP_ROOT}/src/suggest/core/layout/proximity_info_state_utils.cpp
    ${AOSP_ROOT}/src/suggest/core/policy/weighting.cpp
    ${AOSP_ROOT}/src/suggest/core/session/dic_traverse_session.cpp
    ${AOSP_ROOT}/src/suggest/core/result/suggestion_results.cpp
    ${AOSP_ROOT}/src/suggest/core/result/suggestions_output_utils.cpp
    # suggest/policyimpl
    ${AOSP_ROOT}/src/suggest/policyimpl/gesture/gesture_suggest_policy_factory.cpp
    ${AOSP_ROOT}/src/suggest/policyimpl/typing/scoring_params.cpp
    ${AOSP_ROOT}/src/suggest/policyimpl/typing/typing_scoring.cpp
    ${AOSP_ROOT}/src/suggest/policyimpl/typing/typing_suggest_policy.cpp
    ${AOSP_ROOT}/src/suggest/policyimpl/typing/typing_traversal.cpp
    ${AOSP_ROOT}/src/suggest/policyimpl/typing/typing_weighting.cpp
    # utils
    ${AOSP_ROOT}/src/utils/autocorrection_threshold_utils.cpp
    ${AOSP_ROOT}/src/utils/char_utils.cpp
    ${AOSP_ROOT}/src/utils/jni_data_utils.cpp
    ${AOSP_ROOT}/src/utils/log_utils.cpp
    ${AOSP_ROOT}/src/utils/time_keeper.cpp
)

# Our own JNI shim — original code, separate from AOSP. Phase 2 keeps the
# minimal smoke-test surface; phase 4 layers the real Suggest/Dictionary
# bindings on top.
set(SHIM_SOURCES
    jni/latinime_jni.cpp
)

add_library(rewordium_latinime SHARED ${SHIM_SOURCES} ${AOSP_CORE_SOURCES})

target_include_directories(rewordium_latinime PRIVATE
    jni
    ${AOSP_ROOT}/src
)

# Compile flags: relaxed vs AOSP's own Android.bp. AOSP builds with -Werror
# and -Weffc++; that's calibrated for their toolchain and tightens with every
# NDK release. We mirror the substantive checks but allow warnings so an NDK
# bump never blocks a build.
target_compile_options(rewordium_latinime PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-missing-field-initializers
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

# 16 KB page size alignment for Android 15+ (matches reboard_lib:native).
set_target_properties(rewordium_latinime PROPERTIES
    LINK_FLAGS "-Wl,-z,max-page-size=16384"
)

target_link_libraries(rewordium_latinime
    PRIVATE
    android
    log
)
