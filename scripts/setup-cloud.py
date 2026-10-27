#!/usr/bin/env python3
"""Install verified, task-local Android build tools without changing system files."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path('/workspace/tooling')
ROOT.mkdir(parents=True, exist_ok=True)

def download(url, name, expected, algorithm='sha256'):
    path = ROOT / name
    if not path.exists():
        print('Downloading', name, flush=True)
        with urllib.request.urlopen(url, timeout=120) as response, path.open('wb') as output:
            shutil.copyfileobj(response, output)
    h = hashlib.new(algorithm)
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            h.update(block)
    if h.hexdigest() != expected:
        raise RuntimeError('Checksum mismatch: ' + name)
    return path

if not (ROOT / 'jdk17/bin/javac').exists():
    archive = download('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.14%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.14_7.tar.gz', 'jdk17.tar.gz', 'a3af83983fb94dd7d11b13ba2dba0fb6819dc2caaf87e6937afd22ad4680ae9a')
    target = ROOT / 'jdk-extract'
    target.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
        tar.extractall(target, filter='data')
    shutil.move(str(next(target.iterdir())), str(ROOT / 'jdk17'))

if not (ROOT / 'gradle-8.13/bin/gradle').exists():
    archive = download('https://services.gradle.org/distributions/gradle-8.13-bin.zip', 'gradle.zip', '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78')
    with zipfile.ZipFile(archive) as z:
        z.extractall(ROOT)
    (ROOT / 'gradle-8.13/bin/gradle').chmod(0o755)

sdk = ROOT / 'android-sdk'
if not (sdk / 'cmdline-tools/12.0/bin/sdkmanager').exists():
    archive = download('https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip', 'cmdline12.zip', 'd313adb7aedccf6cf0cfca51ec180f0059f5f8f8', 'sha1')
    target = ROOT / 'sdk-extract'
    target.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(target)
    (sdk / 'cmdline-tools').mkdir(parents=True, exist_ok=True)
    shutil.move(str(target / 'cmdline-tools'), str(sdk / 'cmdline-tools/12.0'))
    for path in (sdk / 'cmdline-tools/12.0/bin').iterdir():
        path.chmod(0o755)

# Preserve JDK trust and add the cloud's supplied public CA certificates.
# No TLS bypasses, proxy credentials or credential files are used.
store = ROOT / 'java-cacerts'
temporary_store = ROOT / 'java-cacerts.new'
shutil.copy(ROOT / 'jdk17/lib/security/cacerts', temporary_store)
seen = set()
for variable in ('SSL_CERT_FILE', 'NODE_EXTRA_CA_CERTS'):
    value = os.environ.get(variable)
    if not value or value in seen:
        continue
    seen.add(value)
    certificates = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', Path(value).read_text(), re.S)
    for index, certificate in enumerate(certificates):
        cert = ROOT / 'public-ca.pem'
        cert.write_text(certificate)
        subprocess.run([str(ROOT / 'jdk17/bin/keytool'), '-importcert', '-noprompt', '-alias', f'cloud-{variable}-{index}', '-file', str(cert), '-keystore', str(temporary_store), '-storepass', 'changeit'], check=True, capture_output=True)
temporary_store.replace(store)

env_script = '''export JAVA_HOME=/workspace/tooling/jdk17
export ANDROID_HOME=/workspace/tooling/android-sdk
export ANDROID_USER_HOME=/workspace/tooling/android-user
export ANDROID_EMULATOR_HOME=/workspace/tooling/android-user
export ANDROID_AVD_HOME=/workspace/tooling/avd
export GRADLE_USER_HOME=/workspace/tooling/gradle-home
export PATH="$JAVA_HOME/bin:/workspace/tooling/gradle-8.13/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_TOOL_OPTIONS="$(python3 - <<'PYENV'
import os,urllib.parse
p=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
opts=['-Djavax.net.ssl.trustStore=/workspace/tooling/java-cacerts','-Djavax.net.ssl.trustStorePassword=changeit']
if p.hostname:opts += ['-Dhttps.proxyHost='+p.hostname,'-Dhttps.proxyPort='+str(p.port or 80),'-Dhttp.proxyHost='+p.hostname,'-Dhttp.proxyPort='+str(p.port or 80)]
print(' '.join(opts))
PYENV
)"
'''
(ROOT / 'java-env.sh').write_text(env_script)
for folder in ('android-user', 'avd', 'gradle-home', 'npm-cache'):
    (ROOT / folder).mkdir(exist_ok=True)
subprocess.run(['bash', '-c', '''set -e
. /workspace/tooling/java-env.sh
python3 - <<'PYSDK'
import subprocess
sdk='/workspace/tooling/android-sdk/cmdline-tools/12.0/bin/sdkmanager'
subprocess.run([sdk,'--licenses'],input='y\\n'*200,text=True,check=True,stdout=subprocess.DEVNULL)
subprocess.run([sdk,'platforms;android-35','build-tools;35.0.0','platform-tools','ndk;26.1.10909125'],input='y\\n'*200,text=True,check=True)
PYSDK
'''], check=True)
print('Android tools installed. Activate with: . /workspace/tooling/java-env.sh')
