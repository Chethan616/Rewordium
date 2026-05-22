#!/usr/bin/env ruby
# frozen_string_literal: true

# Wires the RewordiumKeyboard Custom Keyboard Extension target into
# ios/Runner.xcodeproj. Idempotent — re-running on a fresh checkout produces
# the same pbxproj as a first run.
#
# Why a Ruby script and not Xcode UI:
#   * We develop on Windows; no Xcode available locally.
#   * Codemagic CI runs this in the iOS build before `flutter build ios` so
#     the project file stays in sync without committing pbxproj edits.
#
# What it sets up:
#   1. A remote SPM reference to KeyboardKit (free, MIT-licensed).
#   2. The `RewordiumKeyboard` app extension target with our Swift sources
#      from ios/RewordiumKeyboard/**/*.swift.
#   3. App Group entitlements on the Runner target so the host app can write
#      settings the extension reads.
#   4. Embed-extension build phase on Runner.
#
# Requirements: Ruby + the `xcodeproj` gem (CocoaPods ships them on every
# Codemagic mac image).
#
# Usage: from ios/  →  `ruby scripts/setup_keyboard_target.rb`

require 'xcodeproj'
require 'pathname'

IOS_DIR  = File.expand_path('..', __dir__)
PROJECT  = File.join(IOS_DIR, 'Runner.xcodeproj')

TARGET_NAME   = 'RewordiumKeyboard'
BUNDLE_ID     = 'com.noxquill.rewordium.keyboard'
DEPLOYMENT_T  = '17.0'
SWIFT_VERSION = '5.0'

# Paths are project-relative (against the .xcodeproj parent — i.e. ios/).
KB_DIR              = 'RewordiumKeyboard'
RUNNER_ENTITLEMENTS = 'Runner/Runner.entitlements'

KEYBOARDKIT_URL      = 'https://github.com/KeyboardKit/KeyboardKit.git'
KEYBOARDKIT_VERSION  = '10.5.0'         # pin minor — KeyboardKit ships often
KEYBOARDKIT_PRODUCT  = 'KeyboardKit'

# ----------------------------------------------------------------------------

def log(msg) = puts "[setup_keyboard_target] #{msg}"

abort "Runner.xcodeproj not found at #{PROJECT}" unless File.directory?(PROJECT)

project = Xcodeproj::Project.open(PROJECT)
runner  = project.targets.find { |t| t.name == 'Runner' }
abort "Runner target not found" unless runner

# ----------------------------------------------------------------------------
# Runner: App Group entitlement so the host app can write to the shared store.

runner.build_configurations.each do |config|
  config.build_settings['CODE_SIGN_ENTITLEMENTS'] = RUNNER_ENTITLEMENTS
end
log "Runner: CODE_SIGN_ENTITLEMENTS = #{RUNNER_ENTITLEMENTS}"

# ----------------------------------------------------------------------------
# KeyboardKit SPM reference.

package_ref = project.root_object.package_references.find do |r|
  r.respond_to?(:repositoryURL) && r.repositoryURL == KEYBOARDKIT_URL
end
unless package_ref
  package_ref = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
  package_ref.repositoryURL = KEYBOARDKIT_URL
  package_ref.requirement   = { 'kind' => 'upToNextMinorVersion', 'minimumVersion' => KEYBOARDKIT_VERSION }
  project.root_object.package_references << package_ref
  log "Added remote SPM: KeyboardKit @ #{KEYBOARDKIT_VERSION} (upToNextMinor)"
end

# ----------------------------------------------------------------------------
# Find or create the extension target.

target = project.targets.find { |t| t.name == TARGET_NAME }
if target
  log "Target '#{TARGET_NAME}' already exists — verifying SPM link."
else
  target = project.new_target(:app_extension, TARGET_NAME, :ios, DEPLOYMENT_T, nil, :swift)
  log "Created target: #{TARGET_NAME}"

  target.build_configurations.each do |config|
    bs = config.build_settings
    # PRODUCT_NAME + WRAPPER_EXTENSION are critical — without them, the .appex
    # output path becomes literal ".appex" and Xcode's link + mkdir commands
    # collide. xcodeproj 1.27's default template no longer sets these for
    # app_extension, so we pin them here.
    bs['PRODUCT_NAME']                   = '$(TARGET_NAME)'
    bs['WRAPPER_EXTENSION']              = 'appex'
    bs['PRODUCT_BUNDLE_PACKAGE_TYPE']    = 'XPC!'
    bs['PRODUCT_BUNDLE_IDENTIFIER']      = BUNDLE_ID
    bs['INFOPLIST_FILE']                 = "#{KB_DIR}/Info.plist"
    bs['CODE_SIGN_ENTITLEMENTS']         = "#{KB_DIR}/RewordiumKeyboard.entitlements"
    bs['IPHONEOS_DEPLOYMENT_TARGET']     = DEPLOYMENT_T
    bs['SWIFT_VERSION']                  = SWIFT_VERSION
    bs['TARGETED_DEVICE_FAMILY']         = '1,2'
    bs['ENABLE_BITCODE']                 = 'NO'
    bs['SKIP_INSTALL']                   = 'YES'
    bs['LD_RUNPATH_SEARCH_PATHS']        = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
    bs['CODE_SIGNING_ALLOWED']           = 'NO'
    bs['MARKETING_VERSION']              = '2.9.1'
    bs['CURRENT_PROJECT_VERSION']        = '$(FLUTTER_BUILD_NUMBER)'
    bs['GENERATE_INFOPLIST_FILE']        = 'NO'
    bs['DEFINES_MODULE']                 = 'YES'
    bs['SWIFT_OPTIMIZATION_LEVEL']       = config.name == 'Debug' ? '-Onone' : '-O'
  end

  # Compile sources: every .swift file under ios/RewordiumKeyboard/.
  kb_group = project.main_group.find_subpath(KB_DIR, true)
  kb_group.set_source_tree('<group>')

  sources = Dir.glob(File.join(IOS_DIR, KB_DIR, '**', '*.swift')).sort
  abort "No Swift sources found in #{KB_DIR}" if sources.empty?
  sources.each do |abs_path|
    rel = Pathname.new(abs_path).relative_path_from(Pathname.new(IOS_DIR)).to_s.tr('\\', '/')
    file_ref = kb_group.new_reference(rel)
    target.source_build_phase.add_file_reference(file_ref, true)
  end
  log "Added #{sources.size} Swift sources from #{KB_DIR}"

  # Plist + entitlements + privacy manifest are referenced from build settings
  # but the privacy manifest still needs to be copied into the bundle.
  privacy_manifest = File.join(IOS_DIR, KB_DIR, 'PrivacyInfo.xcprivacy')
  if File.exist?(privacy_manifest)
    ref = kb_group.new_reference("#{KB_DIR}/PrivacyInfo.xcprivacy")
    target.resources_build_phase.add_file_reference(ref, true)
    log "Added PrivacyInfo.xcprivacy as resource"
  end
end

# ----------------------------------------------------------------------------
# Ensure the SPM product is linked (works for both fresh + existing targets).

linked_products = target.frameworks_build_phase.files.map do |bf|
  bf.respond_to?(:product_ref) ? bf.product_ref&.product_name : nil
end.compact

unless linked_products.include?(KEYBOARDKIT_PRODUCT)
  dep = target.package_product_dependencies.find { |d| d.product_name == KEYBOARDKIT_PRODUCT }
  unless dep
    dep = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
    dep.product_name = KEYBOARDKIT_PRODUCT
    dep.package      = package_ref
    target.package_product_dependencies << dep
  end
  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = dep
  target.frameworks_build_phase.files << build_file
  log "Linked SPM product: #{KEYBOARDKIT_PRODUCT}"
end

# ----------------------------------------------------------------------------
# Embed the extension into the Runner app.

embed_phase = runner.copy_files_build_phases.find { |p|
  p.name == 'Embed Foundation Extensions' || p.dst_subfolder_spec == '13'
}
unless embed_phase
  embed_phase = runner.new_copy_files_build_phase('Embed Foundation Extensions')
  embed_phase.symbol_dst_subfolder_spec = :plug_ins
end

unless embed_phase.files_references.include?(target.product_reference)
  bf = embed_phase.add_file_reference(target.product_reference)
  bf.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
  log "Embedded #{TARGET_NAME}.appex into Runner"
end

unless runner.dependencies.any? { |d| d.target == target }
  runner.add_dependency(target)
  log "Runner now depends on #{TARGET_NAME}"
end

# ----------------------------------------------------------------------------
project.save
log "Saved #{PROJECT}"
log "Done."
