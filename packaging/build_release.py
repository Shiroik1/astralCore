#!/usr/bin/env python3
"""Build self-contained macOS ARM64 and Windows x64 ZIPs from one verified game JAR.
Run on Apple Silicon macOS with Python 3 and JDK 21+ (jpackage required for the Mac app).
Downloads are pinned in toolchain-lock.json, verified with SHA-256, and kept under ignored build/.
"""
from __future__ import annotations
import argparse, hashlib, json, os, platform, plistlib, shutil, struct, subprocess, tarfile, zipfile
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'/'release'
DOWNLOADS=ROOT/'build'/'downloads'
DIST=ROOT/'dist'
VERSION=(ROOT/'packaging/version.txt').read_text().strip()
LOCK=json.loads((ROOT/'packaging/toolchain-lock.json').read_text())
JAVA=Path(os.environ['JAVA_HOME'])/'bin'/'java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java')

def run(*args, **kwargs):
    shown=args if len(args)<18 else (*args[:10],f'... ({len(args)} arguments)')
    print('+', ' '.join(map(str,shown)),flush=True)
    return subprocess.run(list(map(str,args)),check=True,**kwargs)

def checksum(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def download(item):
    DOWNLOADS.mkdir(parents=True,exist_ok=True);dest=DOWNLOADS/item['archive']
    if not dest.exists() or checksum(dest)!=item['sha256']:
        part=dest.with_suffix(dest.suffix+'.part')
        run('curl','--fail','--location','--silent','--show-error','--retry','2',item['url'],'--output',part)
        if checksum(part)!=item['sha256']:raise RuntimeError('Download checksum mismatch: '+item['archive'])
        part.replace(dest)
    print('Verified',dest.name,flush=True);return dest

def unpack(archive,target):
    if (target/'.complete').exists():return
    if target.exists():shutil.rmtree(target)
    target.mkdir(parents=True)
    if archive.name.endswith('.zip'):
        with zipfile.ZipFile(archive) as z:z.extractall(target)
    else:
        with tarfile.open(archive) as t:t.extractall(target,filter='data')
    (target/'.complete').write_text(checksum(archive))

def bundle():
    classes=BUILD/'classes';resources=BUILD/'resources';inputs=BUILD/'input'
    for folder in [classes,resources,inputs]:
        if folder.exists():shutil.rmtree(folder)
        folder.mkdir(parents=True)
    libraries=sorted((ROOT/'lib').glob('*.jar'))
    run('javac','--release','21','-encoding','UTF-8','-cp',os.pathsep.join(map(str,libraries)),'-d',classes,*sorted((ROOT/'src').rglob('*.java')))
    shutil.copytree(ROOT/'res',resources,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.DS_Store'))
    shutil.copytree(ROOT/'maps',resources/'maps',dirs_exist_ok=True,ignore=shutil.ignore_patterns('.DS_Store'))
    (resources/'build-info.properties').write_text('version='+VERSION+'\n',encoding='utf-8')
    for name in ['terrain.txt','levels.txt','scenery.json','grass.txt']:
        if not (resources/'maps'/name).is_file():raise RuntimeError('Missing release map: '+name)
    (inputs/'lib').mkdir()
    for lib in libraries:shutil.copy2(lib,inputs/'lib'/lib.name)
    manifest=BUILD/'MANIFEST.MF'
    manifest.write_text('Manifest-Version: 1.0\nMain-Class: main.Main\nImplementation-Title: AstralCore\nImplementation-Version: '+VERSION+'\nClass-Path: '+' '.join('lib/'+p.name for p in libraries)+'\n\n')
    run('jar','--create','--file',inputs/'AstralCore.jar','--manifest',manifest,'-C',classes,'.','-C',resources,'.')
    return inputs

def icons(inputs):
    output=BUILD/'icons';output.mkdir(exist_ok=True)
    run('javac','--release','21','-cp',inputs/'AstralCore.jar','-d',BUILD,ROOT/'packaging/MakeIcon.java')
    run(JAVA,'-Djava.awt.headless=true','-cp',os.pathsep.join(map(str,[BUILD,inputs/'AstralCore.jar'])),'MakeIcon',output)
    iconset=output/'AstralCore.iconset';iconset.mkdir(exist_ok=True)
    for size in [16,32,128,256,512]:
        shutil.copy2(output/f'icon-{size}.png',iconset/f'icon_{size}x{size}.png')
        shutil.copy2(output/f'icon-{size*2}.png',iconset/f'icon_{size}x{size}@2x.png')
    run('iconutil','--convert','icns','--output',output/'AstralCore.icns',iconset)
    payloads=[(size,(output/f'icon-{size}.png').read_bytes()) for size in [16,32,48,256]]
    offset=6+16*len(payloads);entries=[];images=[]
    for size,data in payloads:
        entries.append(struct.pack('<BBBBHHII',size%256,size%256,0,0,1,32,len(data),offset));images.append(data);offset+=len(data)
    (output/'AstralCore.ico').write_bytes(struct.pack('<HHH',0,1,len(payloads))+b''.join(entries)+b''.join(images))
    return output

def notices(package):
    shutil.copy2(ROOT/'packaging/PLAYER_README.txt',package/'READ ME.txt')
    shutil.copy2(ROOT/'packaging/THIRD-PARTY-NOTICES.txt',package/'THIRD-PARTY-NOTICES.txt')
    folder=package/'third-party-licenses';folder.mkdir(exist_ok=True)
    for jar in sorted((ROOT/'lib').glob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if Path(name).name.upper().startswith(('LICENSE','NOTICE','COPYING')) and not name.endswith('/'):
                    target=folder/(jar.stem+'-'+Path(name).name);target.write_bytes(z.read(name))

def zip_package(folder):
    DIST.mkdir(exist_ok=True);archive=DIST/(folder.name+'.zip')
    if archive.exists():archive.unlink()
    if folder.name.endswith('windows-x64'):
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
            for path in sorted(folder.rglob('*')):
                if path.is_file():z.write(path,path.relative_to(folder.parent))
    else:run('ditto','-c','-k','--sequesterRsrc','--keepParent',folder,archive)
    print('Created',archive,round(archive.stat().st_size/1024/1024,1),'MiB',flush=True)
    return archive

def mac_package(inputs,icon_dir,runtime):
    folder=BUILD/f'AstralCore_v{VERSION}-macos-arm64'
    if folder.exists():shutil.rmtree(folder)
    folder.mkdir();home=next(runtime.glob('*/Contents/Home'))
    numeric=VERSION.split('-')[0];parts=numeric.split('.');bundle_version='.'.join([str(int(parts[0])+1),*parts[1:]])
    run('jpackage','--type','app-image','--name','AstralCore','--dest',folder,'--input',inputs,'--main-jar','AstralCore.jar',
        '--app-version',bundle_version,'--vendor','AstralCore','--description','AstralCore '+VERSION,
        '--mac-package-identifier','io.github.shiroik1.astralcore','--runtime-image',home,'--icon',icon_dir/'AstralCore.icns',
        '--java-options','-Dastral.packaged=true','--java-options','-Dfile.encoding=UTF-8')
    app=folder/'AstralCore.app';info=app/'Contents/Info.plist'
    metadata=plistlib.loads(info.read_bytes());metadata['CFBundleShortVersionString']=numeric;metadata['CFBundleGetInfoString']='AstralCore '+VERSION
    info.write_bytes(plistlib.dumps(metadata))
    run('codesign','--force','--sign','-','--preserve-metadata=entitlements,flags',app)
    notices(folder);return zip_package(folder)

def windows_package(inputs,icon_dir,runtime,compiler):
    folder=BUILD/f'AstralCore_v{VERSION}-windows-x64'
    if folder.exists():shutil.rmtree(folder)
    folder.mkdir();shutil.copytree(inputs,folder/'app')
    home=next(p.parent for p in runtime.glob('*/release'));shutil.copytree(home,folder/'runtime')
    zig=next(compiler.glob('*/zig'));rc=BUILD/'launcher.rc';res=BUILD/'launcher.res'
    rc.write_text('101 ICON "'+str(icon_dir/'AstralCore.ico')+'"\n1 VERSIONINFO\nFILEVERSION 0,1,0,0\nPRODUCTVERSION 0,1,0,0\nFILETYPE 1\nBEGIN\n BLOCK "StringFileInfo"\n BEGIN\n  BLOCK "040904b0"\n  BEGIN\n   VALUE "ProductName", "AstralCore"\n   VALUE "FileDescription", "AstralCore '+VERSION+'"\n   VALUE "FileVersion", "'+VERSION+'"\n  END\n END\nEND\n')
    env=os.environ.copy();env['ZIG_GLOBAL_CACHE_DIR']=str(BUILD/'zig-cache');env['ZIG_LOCAL_CACHE_DIR']=str(BUILD/'zig-cache-local')
    run(zig,'rc','/fo',res,rc,env=env)
    run(zig,'cc','-target','x86_64-windows-gnu','-O2','-Wl,--subsystem,windows',ROOT/'packaging/windows_launcher.c',res,'-luser32','-o',folder/'AstralCore.exe',env=env)
    data=(folder/'AstralCore.exe').read_bytes();pe=struct.unpack_from('<I',data,60)[0]
    if struct.unpack_from('<H',data,pe+4)[0]!=0x8664 or struct.unpack_from('<H',data,pe+24+68)[0]!=2:
        raise RuntimeError('Windows launcher must be an x64 GUI executable')
    notices(folder);return zip_package(folder)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target',choices=['all','mac','windows','jar'],default='all');args=parser.parse_args()
    if args.target!='jar' and (platform.system()!='Darwin' or platform.machine()!='arm64'):raise SystemExit('Native Mac packaging and this cross-build entry point require Apple Silicon macOS. Use --target jar elsewhere.')
    BUILD.mkdir(parents=True,exist_ok=True);inputs=bundle()
    if args.target=='jar':return
    names=['mac-jre','windows-jre','zig'] if args.target=='all' else ['mac-jre'] if args.target=='mac' else ['windows-jre','zig']
    with ThreadPoolExecutor(max_workers=3) as pool:archives=list(pool.map(download,[LOCK['downloads'][name] for name in names]))
    extracted={}
    for name,archive in zip(names,archives):
        target=BUILD/'tools'/name;unpack(archive,target);extracted[name]=target
    icon_dir=icons(inputs);artifacts=[]
    if args.target in ['all','mac']:artifacts.append(mac_package(inputs,icon_dir,extracted['mac-jre']))
    if args.target in ['all','windows']:artifacts.append(windows_package(inputs,icon_dir,extracted['windows-jre'],extracted['zig']))
    artifacts=sorted(DIST.glob(f'AstralCore_v{VERSION}-*.zip'))
    (DIST/'SHA256SUMS.txt').write_text(''.join(checksum(p)+'  '+p.name+'\n' for p in artifacts))
    shutil.copy2(ROOT/'packaging/toolchain-lock.json',DIST/'build-toolchain.json')
    print('Release',VERSION,'is ready in',DIST,flush=True)
if __name__=='__main__':main()
