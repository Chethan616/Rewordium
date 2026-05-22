#!/usr/bin/env ruby
# frozen_string_literal: true

# Wires the RewordiumKeyboard Custom Keyboard Extension target into
# ios/Runner.xcodeproj. Idempotent — re-running on a project that already
# has the target verifies and patches missing pieces but is otherwise a no-op.
#
# Why a Ruby script and not Xcode UI:
#   * We develop primarily on Windows; no Xcode available.
#   * Codemagic CI runs this in the iOS build before `flutter build ios`
#     so the project file stays "just-in-time" consistent.
#
# Requirements: Ruby + the `xcodeproj` gem. Both ship with CocoaPods on every
# Codemagic mac image.
#
# Usage: from ios/  →  `ruby scripts/setup_keyboard_target.rb`

require 'xcodeproj'
require 'fileutils'
require 'pathname'

IOS_DIR  = File.expand_path('..', __dir__)
PROJECT  = File.join(IOS_DIR, 'Runner.xcodeproj')

TARGET_NAME   = 'RewordiumKeyboard'
BUNDLE_ID     = 'com.noxquill.rewordium.keyboard'
DEPLOYMENT_T  = '17.0'
SWIFT_VERSION = '5.0'

# Paths are project-relative (against the .xcodeproj parent — i.e. ios/).
AZOOKEY_ROOT   = 'Packages/azooKey'
KB_SCAFFOLD    = 'RewordiumKeyboard'
KB_SOURCES_DIR = "#{AZOOKEY_ROOT}/Keyboard/Display"

RESOURCE_PATHS = [
  "#{AZOOKEY_ROOT}/Keyboard/Dictionary",
  "#{AZOOKEY_ROOT}/azooKey_dictionary_storage",
  "#{AZOOKEY_ROOT}/azooKey_emoji_dictionary_storage",
  "#{AZOOKEY_ROOT}/Resources/AzooKeyIcon-Regular.otf",
  "#{AZOOKEY_ROOT}/Resources/Designs.xcassets",
  "#{AZOOKEY_ROOT}/Resources/Localizable.xcstrings",
  "#{AZOOKEY_ROOT}/Resources/InfoPlist.xcstrings",
  "#{KB_SCAFFOLD}/PrivacyInfo.xcprivacy",
].freeze

# Per AzooKeyCore/Package.swift — pin to the same revisions so SPM resolution
# matches what AzooKey upstream ships. If you upgrade AzooKey, refresh both.
REMOTE_PACKAGES = {
  'AzooKeyKanaKanjiConverter' => {
    url: 'https://github.com/azooKey/AzooKeyKanaKanjiConverter',
    revision: '1def030b6697fb3811f2ae642719811db6b70c3e',
  },
  'CustardKit' => {
    url: 'https://github.com/azooKey/CustardKit',
    revision: '7bddc14eb3f8f0145c6f3a4fea20cf394f8104e8',
  },
}.freeze

# product name => :local | <remote package key>
PRODUCT_PACKAGES = {
  'KeyboardViews'           => :local,
  'KeyboardThemes'          => :local,
  'KeyboardExtensionUtils'  => :local,
  'AzooKeyUtils'            => :local,
  'SwiftUIUtils'            => :local,
  'KanaKanjiConverterModule' => 'AzooKeyKanaKanjiConverter',
  'SwiftUtils'              => 'AzooKeyKanaKanjiConverter',
}.freeze

# ----------------------------------------------------------------------------

def log(msg) = puts "[setup_keyboard_target] #{msg}"

abort "Runner.xcodeproj not found at #{PROJECT}" unless File.directory?(PROJECT)

project = Xcodeproj::Project.open(PROJECT)
runner  = project.targets.find { |t| t.name == 'Runner' }
abort "Runner target not found in project" unless runner

# ----------------------------------------------------------------------------
# Package references — local + remote — created if missing.

local_pkg = project.root_object.package_references.find do |r|
  r.respond_to?(:relative_path) && r.relative_path == "#{AZOOKEY_ROOT}/AzooKeyCore"
end
unless local_pkg
  local_pkg = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  local_pkg.relative_path = "#{AZOOKEY_ROOT}/AzooKeyCore"
  project.root_object.package_references << local_pkg
  log "Added local SPM reference: #{AZOOKEY_ROOT}/AzooKeyCore"
end

remote_pkgs = {}
REMOTE_PACKAGES.each do |name, spec|
  existing = project.root_object.package_references.find do |r|
    r.respond_to?(:repositoryURL) && r.repositoryURL == spec[:url]
  end
  if existing
    remote_pkgs[name] = existing
  else
    ref = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
    ref.repositoryURL = spec[:url]
    ref.requirement   = { 'kind' => 'revision', 'revision' => spec[:revision] }
    project.root_object.package_references << ref
    remote_pkgs[name] = ref
    log "Added remote SPM reference: #{spec[:url]} @ #{spec[:revision][0, 7]}"
  end
end

# ----------------------------------------------------------------------------
# Find or create the extension target.

target = project.targets.find { |t| t.name == TARGET_NAME }
if target
  log "Target '#{TARGET_NAME}' already exists — patching missing SPM products."
else
  target = project.new_target(:app_extension, TARGET_NAME, :ios, DEPLOYMENT_T, nil, :swift)
  log "Created target: #{TARGET_NAME}"

  target.build_configurations.each do |config|
    bs = config.build_settings
    bs['PRODUCT_BUNDLE_IDENTIFIER']      = BUNDLE_ID
    bs['INFOPLIST_FILE']                 = "#{KB_SCAFFOLD}/Info.plist"
    bs['CODE_SIGN_ENTITLEMENTS']         = "#{KB_SCAFFOLD}/RewordiumKeyboard.entitlements"
    bs['IPHONEOS_DEPLOYMENT_TARGET']     = DEPLOYMENT_T
    bs['SWIFT_VERSION']                  = SWIFT_VERSION
    bs['TARGETED_DEVICE_FAMILY']         = '1,2'
    bs['ENABLE_BITCODE']                 = 'NO'
    bs['SWIFT_OBJC_INTEROP_MODE']        = 'objcxx'   # AzooKey depends on C++ interop
    bs['CLANG_CXX_LANGUAGE_STANDARD']    = 'gnu++20'
    bs['CLANG_CXX_LIBRARY']              = 'libc++'
    bs['SKIP_INSTALL']                   = 'YES'
    bs['LD_RUNPATH_SEARCH_PATHS']        = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
    bs['CODE_SIGNING_ALLOWED']           = 'NO'
    bs['MARKETING_VERSION']              = '2.9.1'
    bs['CURRENT_PROJECT_VERSION']        = '$(FLUTTER_BUILD_NUMBER)'
    bs['GENERATE_INFOPLIST_FILE']        = 'NO'
  end

  # Add Swift sources from AzooKey's Keyboard/Display directory.
  keyboard_group = project.main_group.find_subpath('RewordiumKeyboard', true)
  keyboard_group.set_source_tree('<group>')

  source_paths = Dir.glob(File.join(IOS_DIR, KB_SOURCES_DIR, '*.swift')).sort
  abort "No Swift sources found in #{KB_SOURCES_DIR}" if source_paths.empty?

  source_paths.each do |abs_path|
    rel = Pathname.new(abs_path).relative_path_from(Pathname.new(IOS_DIR)).to_s.tr('\\', '/')
    file_ref = keyboard_group.new_reference(rel)
    target.source_build_phase.add_file_reference(file_ref, true)
  end
  log "Added #{source_paths.size} Swift sources from #{KB_SOURCES_DIR}"

  # Resources.
  resources_group = keyboard_group.find_subpath('Resources', true)
  resources_group.set_source_tree('<group>')

  RESOURCE_PATHS.each do |rel_path|
    abs = File.join(IOS_DIR, rel_path)
    unless File.exist?(abs)
      log "  skip (missing on disk): #{rel_path}"
      next
    end
    ref = resources_group.new_reference(rel_path)
    ref.last_known_file_type = 'folder' if File.directory?(abs)
    target.resources_build_phase.add_file_reference(ref, true)
  end
  log "Added #{RESOURCE_PATHS.size} resource entries"
end

# ----------------------------------------------------------------------------
# Ensure all SPM product dependencies are linked. Runs for both newly-created
# and pre-existing targets so the script is self-healing.

existing_products = target.package_product_dependencies.map(&:product_name)
PRODUCT_PACKAGES.each do |product_name, owner|
  next if existing_products.include?(product_name)
  package_ref = (owner == :local) ? local_pkg : remote_pkgs[owner]
  next unless package_ref

  dep = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dep.product_name = product_name
  dep.package      = package_ref
  target.package_product_dependencies << dep
  target.frameworks_build_phase.add_file_reference(dep)
  log "Linked SPM product: #{product_name} (from #{owner == :local ? 'AzooKeyCore' : owner})"
end

# ----------------------------------------------------------------------------
# Embed the extension into the Runner app.

embed_phase = runner.copy_files_build_phases.find { |p| p.name == 'Embed Foundation Extensions' || p.dst_subfolder_spec == '13' }
unless embed_phase
  embed_phase = runner.new_copy_files_build_phase('Embed Foundation Extensions')
  embed_phase.symbol_dst_subfolder_spec = :plug_ins
end

unless embed_phase.files_references.include?(target.product_reference)
  build_file = embed_phase.add_file_reference(target.product_reference)
  build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
  log "Embedded #{TARGET_NAME}.appex into Runner"
end

# Make Runner depend on the extension so build order is correct.
unless runner.dependencies.any? { |d| d.target == target }
  runner.add_dependency(target)
  log "Runner now depends on #{TARGET_NAME}"
end

# ----------------------------------------------------------------------------
project.save
log "Saved #{PROJECT}"
log "Done."
