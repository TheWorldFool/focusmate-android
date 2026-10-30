from pathlib import Path
import subprocess, zipfile, shutil, json, hashlib, os, tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
TOOLS=ROOT/'.toolchain'
SDK=TOOLS/'platforms-android-35/android-35/android.jar'
BT=TOOLS/'build-tools-35.0.0/android-15'
(ROOT/'build').mkdir(exist_ok=True)
# Isolate every build so deleted/renamed classes and old dex cannot leak into APKs.
BUILD=Path(tempfile.mkdtemp(prefix='apk-',dir=ROOT/'build'))
java_home=os.environ.get('JAVA_HOME')
javac=shutil.which('javac')
if java_home:
    JAVA=Path(java_home)/'bin'
elif javac:
    JAVA=Path(javac).resolve().parent
else:
    raise SystemExit('JDK not found. Set JAVA_HOME or add the JDK bin directory to PATH.')
if not (JAVA/'javac.exe').is_file():
    raise SystemExit('This build script requires Windows and a full JDK (javac.exe).')
if not SDK.is_file() or not (BT/'aapt2.exe').is_file():
    raise SystemExit('Android tools not found. Run: python download-toolchain.py')
def run(args):
    command=[str(x.relative_to(ROOT)) if isinstance(x,Path) and x.is_relative_to(ROOT) else str(x) for x in args]
    result=subprocess.run(command,cwd=ROOT)
    if result.returncode:raise SystemExit(result.returncode)
for name in ['generated','classes','dex']:(BUILD/name).mkdir(exist_ok=True)
run([BT/'aapt2.exe','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip'])
run([BT/'aapt2.exe','link','-o',BUILD/'base.apk','--manifest',ROOT/'app/src/main/AndroidManifest.xml','-I',SDK,'--java',BUILD/'generated','-R',BUILD/'resources.zip','--auto-add-overlay'])
sources=list((ROOT/'app/src/main/java').rglob('*.java'))+list((BUILD/'generated').rglob('*.java'))
args=['-encoding','UTF-8','-source','8','-target','8','-classpath',str(SDK),'-d',str(BUILD/'classes')]+[str(p) for p in sources]
(BUILD/'javac.args').write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
run([JAVA/'javac.exe','@'+str(BUILD/'javac.args')])
run([JAVA/'jar.exe','cf',BUILD/'classes.jar','-C',BUILD/'classes','.'])
run([JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',SDK,'--min-api','26','--output',BUILD/'dex',BUILD/'classes.jar'])
shutil.copyfile(BUILD/'base.apk',BUILD/'unsigned.apk')
with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
    for dex in (BUILD/'dex').glob('*.dex'):apk.write(dex,dex.name)
run([BT/'zipalign.exe','-f','-p','4',BUILD/'unsigned.apk',BUILD/'aligned.apk'])
key=ROOT/'local-debug.keystore'
if not key.exists():run([JAVA/'keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','localdebug','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Local Focus Development, O=Personal, C=CN'])
output=ROOT/'专注清单-本地版.apk'
run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','localdebug','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--out',output,BUILD/'aligned.apk'])
run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--verbose',output])
digest=hashlib.sha256(output.read_bytes()).hexdigest()
version=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
(ROOT/'build-result.json').write_text(json.dumps({'apk':output.name,'sha256':digest,'size':output.stat().st_size,'package':'cn.local.focusmate','minSdk':26,'targetSdk':35,'version':version},ensure_ascii=False,indent=2),encoding='utf-8')
print('BUILT',output.name,'bytes',output.stat().st_size,'SHA256',digest)
