"""Generate a dependency-free Xcode fixture using the isolated PamAuth package."""
from pathlib import Path
import json
import plistlib
import shutil

root = Path('ios-host')
root.mkdir(exist_ok=True)
shutil.copy('plugin/tests/ios-host/App.swift', root / 'App.swift')
objects = {}
def add(name, value):
    identifier = f'{len(objects) + 1:024X}'
    objects[identifier] = {'isa': name, **value}
    return identifier
source = add('PBXFileReference', {'lastKnownFileType': 'sourcecode.swift', 'path': 'App.swift', 'sourceTree': '<group>'})
product = add('PBXFileReference', {'explicitFileType': 'wrapper.application', 'path': 'PamAuthHost.app', 'sourceTree': 'BUILT_PRODUCTS_DIR'})
package = add('XCLocalSwiftPackageReference', {'relativePath': '../certification'})
dependency = add('XCSwiftPackageProductDependency', {'productName': 'PamAuth'})
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
project = add('PBXProject', {'attributes': {'LastUpgradeCheck': '1600'}, 'buildConfigurationList': configs({}), 'compatibilityVersion': 'Xcode 14.0', 'developmentRegion': 'en', 'knownRegions': ['en', 'Base'], 'mainGroup': group, 'projectDirPath': '', 'projectRoot': '', 'targets': [target], 'packageReferences': [package]})
def encode(value):
    if isinstance(value, dict): return '{' + ''.join(f'{json.dumps(k)} = {encode(v)};' for k, v in value.items()) + '}'
    if isinstance(value, list): return '(' + ','.join(encode(v) for v in value) + ')'
    return json.dumps(value)
project_dir = root / 'PamAuthHost.xcodeproj'
project_dir.mkdir(exist_ok=True)
(project_dir / 'project.pbxproj').write_text('// !$*UTF8*$!\n' + encode({'archiveVersion': 1, 'classes': {}, 'objectVersion': 56, 'objects': objects, 'rootObject': project}))
with (root / 'Info.plist').open('wb') as file:
    plistlib.dump({'CFBundleIdentifier': '$(PRODUCT_BUNDLE_IDENTIFIER)', 'CFBundleExecutable': '$(EXECUTABLE_NAME)', 'CFBundleName': 'PamAuthHost', 'CFBundlePackageType': 'APPL', 'CFBundleVersion': '1', 'CFBundleShortVersionString': '1.0', 'NSFaceIDUsageDescription': 'Certify native PAM authentication.', 'UILaunchScreen': {}, 'UIApplicationSceneManifest': {'UIApplicationSupportsMultipleScenes': False}}, file)
