"""Pack Blender renders. Requires Pillow; run after build.py --all."""
from pathlib import Path
import json, math, shutil, zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
DIRECTIONS=['down','up','left','right']
COUNTS={'idle':4,'run':8,'attack':8}
FONT='/System/Library/Fonts/Supplemental/Arial.ttf'
def font(size):
    try: return ImageFont.truetype(FONT,size)
    except OSError: return ImageFont.load_default(size=size)

manifest={
    'name':'reference_chibi_base', 'version':1,
    'canvas':[90,90], 'sourceCanvas':[180,180],
    'anchor':[45,85.5], 'anchorMeaning':'Projected ground point, measured from top left; feet can lift in run.',
    'directions':DIRECTIONS, 'directionMeaning':'down faces camera, up faces away',
    'shadow':'none; game supplies ground shadow',
    'sheetLayout':'columns are frames, rows are down/up/left/right',
    'animations':{},
    'notes':['Unarmed attack; impact is frame index 4.',
             'PNG assets are generated but not connected to the game loader.',
             'Back and hidden anatomy are interpretations of the supplied front/side reference.']}
frames={}; checks=[]
for animation,count in COUNTS.items():
    sheet=Image.new('RGBA',(count*90,4*90))
    hires=Image.new('RGBA',(count*180,4*180))
    for row,direction in enumerate(DIRECTIONS):
        key=f'{animation}_{direction}'; frames[key]=[]
        dest=ROOT/'frames_90'/key; dest.mkdir(parents=True,exist_ok=True)
        sources=sorted((ROOT/'renders_180'/key).glob('*.png'))
        assert len(sources)==count,(key,len(sources))
        for i,p in enumerate(sources):
            with Image.open(p) as src:
                src=src.convert('RGBA')
                assert src.size==(180,180)
                image=src.resize((90,90),Image.Resampling.LANCZOS)
                alpha=image.getchannel('A'); bounds=alpha.getbbox()
                assert bounds and bounds[0]>0 and bounds[1]>0 and bounds[2]<90 and bounds[3]<90,(key,i,bounds)
                assert alpha.getpixel((0,0))==0
                checks.append({'animation':key,'frame':i,'alphaBounds':bounds})
                image.save(dest/f'{i:02}.png')
                frames[key].append(image)
                sheet.paste(image,(i*90,row*90))
                hires.paste(src,(i*180,row*180))
    (ROOT/'sheets_90').mkdir(exist_ok=True)
    (ROOT/'sheets_180').mkdir(exist_ok=True)
    sheet.save(ROOT/'sheets_90'/f'{animation}.png')
    hires.save(ROOT/'sheets_180'/f'{animation}.png')
    manifest['animations'][animation]={'framesPerDirection':count,
        'frameDurationMs':200 if animation=='idle' else 83,
        'loop':animation!='attack','sheet':f'sheets_90/{animation}.png',
        'framePattern':f'frames_90/{animation}_{{direction}}/{{index:02}}.png'}

palette_bg=(157,161,184)
def board(tick=None):
    canvas=Image.new('RGB',(864,784),(35,40,55))
    draw=ImageDraw.Draw(canvas)
    draw.text((26,18),'ASTRALCORE / CHARACTER BASE',font=font(23),fill=(240,234,225))
    draw.text((26,50),'Rendered from the editable 3D model',font=font(15),fill=(174,185,204))
    for col,direction in enumerate(DIRECTIONS):
        draw.text((30+col*210,85),direction.upper(),font=font(15),fill=(222,224,235))
    for row,(animation,count) in enumerate(COUNTS.items()):
        for col,direction in enumerate(DIRECTIONS):
            x=24+col*210; y=113+row*216
            draw.rounded_rectangle((x,y,x+198,y+204),radius=12,fill=palette_bg)
            i=0 if tick is None else ((tick//3)%count if animation=='idle' else tick%count)
            image=frames[f'{animation}_{direction}'][i].resize((180,180),Image.Resampling.LANCZOS)
            canvas.paste(image,(x+9,y-4),image)
            draw.text((x+13,y+179),animation.upper(),font=font(13),fill=(37,43,63))
    draw.text((26,766),'4 directions | idle 4 / run 8 / attack 8 | transparent PNG',font=font(12),fill=(174,185,204))
    return canvas

board().save(ROOT/'asset_preview.png')
gif=[board(i) for i in range(24)]
gif[0].save(ROOT/'animation_preview.gif',save_all=True,append_images=gif[1:],duration=83,loop=0,optimize=False)

beauty=Image.new('RGB',(1536,860),palette_bg)
for i,name in enumerate(['preview_front.png','preview_three_quarter.png']):
    image=Image.open(ROOT/name).convert('RGBA'); beauty.paste(image,(i*768,40),image)
draw=ImageDraw.Draw(beauty)
draw.text((45,22),'CHARACTER BASE / FRONT',font=font(20),fill=(39,43,59))
draw.text((813,22),'THREE QUARTER',font=font(20),fill=(39,43,59))
beauty.save(ROOT/'model_preview.jpg',quality=94)
(ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(ROOT/'validation.json').write_text(json.dumps({'passed':True,'frameCount':len(checks),
    'checks':['All 80 frames present','RGBA transparency','Expected dimensions','No frame-edge clipping'],
    'frames':checks},indent=2)+'\n')

bundle=ROOT/'character_base_assets.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as archive:
    for p in sorted(ROOT.rglob('*')):
        if p.is_file() and p!=bundle and p.suffix not in ['.blend1','.zip'] and '__pycache__' not in p.parts:
            archive.write(p,'character_base/'+str(p.relative_to(ROOT)))
print(f'Validated {len(checks)} frames; packaged {bundle}')
