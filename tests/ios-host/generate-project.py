"""Generate a dependency-free Xcode fixture using the isolated PamAuth package."""
from pathlib import Path
import json
import plistlib
import shutil

root = Path('ios-host')
root.mkdir(exist_ok=True)
shutil.copy('plugin/tests/ios-host/App.swift', root / 'App.swift')
shutil.copy('plugin/tests/ios-host/PrivacyUITests.swift', root / 'PrivacyUITests.swift')
objects = {}
def add(name, value):
    identifier = f'{len(objects) + 1:024X}'
    objects[identifier] = {'isa': name, **value}
    return identifier
source = add('PBXFileReference', {'lastKnownFileType': 'sourcecode.swift', 'path': 'App.swift', 'sourceTree': '<group>'})
product = add('PBXFileReference', {'explicitFileType': 'wrapper.application', 'path': 'PamAuthHost.app', 'sourceTree': 'BUILT_PRODUCTS_DIR'})
package = add('XCLocalSwiftPackageReference', {'relativePath': '../certification'})
dependency = add('XCSwiftPackageProductDependency', {'productName': 'PamAuth', 'package': package})
source_build = add('PBXBuildFile', {'fileRef': source})
framework_build = add('PBXBuildFile', {'productRef': dependency})
sources = add('PBXSourcesBuildPhase', {'buildActionMask': 2147483647, 'files': [source_build], 'runOnlyForDeploymentPostprocessing': 0})
frameworks = add('PBXFrameworksBuildPhase', {'buildActionMask': 2147483647, 'files': [framework_build], 'runOnlyForDeploymentPostprocessing': 0})
group = add('PBXGroup', {'children': [source, product], 'sourceTree': '<group>'})
def configs(settings):
    configurations = [add('XCBuildConfiguration', {'name': name, 'buildSettings': settings}) for name in ['Debug', 'Release']]
    return add('XCConfigurationList', {'buildConfigurations': configurations, 'defaultConfigurationIsVisible': 0, 'defaultConfigurationName': 'Debug'})
target_config = configs({'PRODUCT_BUNDLE_IDENTIFIER': 'dev.pam.auth.certification.ios', 'PRODUCT_NAME': 'PamAuthHost', 'SDKROOT': 'iphoneos', 'IPHONEOS_DEPLOYMENT_TARGET': '15.0', 'SWIFT_VERSION': '5.0', 'TARGETED_DEVICE_FAMILY': '1,2', 'INFOPLIST_FILE': 'Info.plist', 'CODE_SIGNING_ALLOWED': 'NO'})
target = add('PBXNativeTarget', {'buildConfigurationList': target_config, 'buildPhases': [sources, frameworks], 'buildRules': [], 'dependencies': [], 'name': 'PamAuthHost', 'productName': 'PamAuthHost', 'productReference': product, 'productType': 'com.apple.product-type.application', 'packageProductDependencies': [dependency]})
test_source = add('PBXFileReference', {'lastKnownFileType': 'sourcecode.swift', 'path': 'PrivacyUITests.swift', 'sourceTree': '<group>'})
test_product = add('PBXFileReference', {'explicitFileType': 'wrapper.cfbundle', 'path': 'PamAuthHostUITests.xctest', 'sourceTree': 'BUILT_PRODUCTS_DIR'})
objects[group]['children'].extend([test_source, test_product])
test_build = add('PBXBuildFile', {'fileRef': test_source})
test_sources = add('PBXSourcesBuildPhase', {'buildActionMask': 2147483647, 'files': [test_build], 'runOnlyForDeploymentPostprocessing': 0})
test_config = configs({'PRODUCT_BUNDLE_IDENTIFIER': 'dev.pam.auth.certification.ios.uitests', 'PRODUCT_NAME': '$(TARGET_NAME)', 'SDKROOT': 'iphoneos', 'IPHONEOS_DEPLOYMENT_TARGET': '15.0', 'SWIFT_VERSION': '5.0', 'GENERATE_INFOPLIST_FILE': 'YES', 'CODE_SIGNING_ALLOWED': 'NO', 'TEST_TARGET_NAME': 'PamAuthHost'})
test_dependency = add('PBXTargetDependency', {'target': target})
test_target = add('PBXNativeTarget', {'buildConfigurationList': test_config, 'buildPhases': [test_sources], 'buildRules': [], 'dependencies': [test_dependency], 'name': 'PamAuthHostUITests', 'productName': 'PamAuthHostUITests', 'productReference': test_product, 'productType': 'com.apple.product-type.bundle.ui-testing'})
project = add('PBXProject', {'attributes': {'LastUpgradeCheck': '1600'}, 'buildConfigurationList': configs({}), 'compatibilityVersion': 'Xcode 14.0', 'developmentRegion': 'en', 'knownRegions': ['en', 'Base'], 'mainGroup': group, 'projectDirPath': '', 'projectRoot': '', 'targets': [target, test_target], 'packageReferences': [package]})
def encode(value):
    if isinstance(value, dict): return '{' + ''.join(f'{json.dumps(k)} = {encode(v)};' for k, v in value.items()) + '}'
    if isinstance(value, list): return '(' + ','.join(encode(v) for v in value) + ')'
    return json.dumps(value)
project_dir = root / 'PamAuthHost.xcodeproj'
project_dir.mkdir(exist_ok=True)
(project_dir / 'project.pbxproj').write_text('// !$*UTF8*$!\n' + encode({'archiveVersion': 1, 'classes': {}, 'objectVersion': 56, 'objects': objects, 'rootObject': project}))
with (root / 'Info.plist').open('wb') as file:
    plistlib.dump({'CFBundleIdentifier': '$(PRODUCT_BUNDLE_IDENTIFIER)', 'CFBundleExecutable': '$(EXECUTABLE_NAME)', 'CFBundleName': 'PamAuthHost', 'CFBundlePackageType': 'APPL', 'CFBundleVersion': '1', 'CFBundleShortVersionString': '1.0', 'NSFaceIDUsageDescription': 'Certify native PAM authentication.', 'UILaunchScreen': {}, 'UIApplicationSceneManifest': {'UIApplicationSupportsMultipleScenes': False}}, file)

scheme_dir = project_dir / 'xcshareddata/xcschemes'
scheme_dir.mkdir(parents=True, exist_ok=True)
def reference(identifier, name, buildable):
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier}" BuildableName="{buildable}" BlueprintName="{name}" ReferencedContainer="container:PamAuthHost.xcodeproj"/>'
(scheme_dir / 'PamAuthHost.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference(target, 'PamAuthHost', 'PamAuthHost.app')}</BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{reference(test_target, 'PamAuthHostUITests', 'PamAuthHostUITests.xctest')}</TestableReference></Testables></TestAction>
</Scheme>''')
