from pathlib import Path
import urllib.request, xml.etree.ElementTree as ET, zipfile, hashlib, json, shutil

ROOT=Path(__file__).resolve().parent
TOOLS=ROOT/'.toolchain'
TOOLS.mkdir(exist_ok=True)
metadata=TOOLS/'repository2-3.xml'
if not metadata.exists():
    local=ROOT/'research/android-repository.xml'
    if local.is_file():
        shutil.copyfile(local,metadata)
    else:
        with urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml',timeout=60) as response:
            metadata.write_bytes(response.read())
repo=ET.parse(metadata).getroot()
packages={p.attrib['path']:p for p in repo if p.tag.endswith('remotePackage')}
def child(e,name): return next((x for x in e if x.tag.split('}')[-1]==name),None)
def text(e,name):
    item=child(e,name)
    return item.text if item is not None else None
result={}
for name in ['platforms;android-35','build-tools;35.0.0','platform-tools']:
    package=packages[name]
    archive=None
    for a in child(package,'archives'):
        os=text(a,'host-os')
        if os in ('windows',None): archive=a; break
    complete=child(archive,'complete')
    url='https://dl.google.com/android/repository/'+text(complete,'url')
    expected=text(complete,'checksum')
    output=TOOLS/Path(url).name
    if not output.exists():
        print('Downloading',name,url,flush=True)
        with urllib.request.urlopen(url,timeout=60) as response,output.open('wb') as target:
            total=0; mark=0
            while True:
                block=response.read(1024*1024)
                if not block:break
                target.write(block);total+=len(block)
                if total-mark>16*1024*1024: print(name,round(total/1024/1024),'MB',flush=True);mark=total
    actual=hashlib.sha1(output.read_bytes()).hexdigest()
    if actual!=expected:raise RuntimeError('Checksum mismatch: '+name)
    folder=TOOLS/name.replace(';','-')
    folder.mkdir(exist_ok=True)
    with zipfile.ZipFile(output) as z:
        for item in z.infolist():
            resolved=(folder/item.filename).resolve()
            if not resolved.is_relative_to(folder.resolve()):raise RuntimeError('Unsafe archive entry')
        for item in z.infolist():
            target=folder/item.filename
            # Re-running setup must not try to overwrite an unchanged adb.exe
            # while the local ADB server is using it on Windows.
            if not item.is_dir() and target.is_file() and target.stat().st_size==item.file_size:
                if target.read_bytes()==z.read(item):continue
            z.extract(item,folder)
    result[name]=str(folder)
    print('Verified and extracted',name,flush=True)
(TOOLS/'paths.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
