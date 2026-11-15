"""Rebuild the reference-inspired character with Blender 5.x; no add-ons required.
Usage: Blender --background --python build.py -- [--all]
"""
import bpy, math, json, sys
from pathlib import Path
from mathutils import Vector

OUT = Path(__file__).resolve().parent
FULL = '--all' in sys.argv
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

def linear(v):
    return v / 12.92 if v < .04045 else ((v + .055) / 1.055) ** 2.4

def material(name, rgb):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*[linear(c) for c in rgb], 1)
    m.use_nodes = True
    bs = m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value = m.diffuse_color
    bs.inputs['Roughness'].default_value = .78
    if name == 'Warm porcelain skin':
        bs.inputs['Subsurface Weight'].default_value = .035
    return m

skin = material('Warm porcelain skin', (1, .855, .737))
eye = material('Soft charcoal eyes', (.29, .225, .235))

def rounded(name, location, scale, exponent=1., mat=skin):
    # A superellipsoid: rounded-square head and softly squared capsule limbs.
    verts, faces = [], []
    rings, sides = 40, 64
    def sp(v): return math.copysign(abs(v)**exponent, v)
    for j in range(rings+1):
        lat = -math.pi/2 + math.pi*j/rings
        for i in range(sides):
            lon = 2*math.pi*i/sides
            verts.append((scale[0]*sp(math.cos(lat))*sp(math.cos(lon)),
                          scale[1]*sp(math.cos(lat))*sp(math.sin(lon)),
                          scale[2]*sp(math.sin(lat))))
    for j in range(rings):
        for i in range(sides):
            a=j*sides+i; b=j*sides+(i+1)%sides
            faces.append((a,b,b+sides,a+sides))
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(verts,[],faces); mesh.update()
    obj=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(obj)
    obj.location=location; obj.data.materials.append(mat)
    for p in mesh.polygons: p.use_smooth=True
    return obj

parts=[]
parts.append(rounded('Head', (0,0,2.16), (.91,.69,.82), .88))
parts.append(rounded('Torso', (0,0,.91), (.44,.29,.57), .88))
parts.append(rounded('Neck', (0,0,1.43), (.22,.20,.20)))
for sign, side in [(1,'L'),(-1,'R')]:
    parts.append(rounded('Ear.'+side,(sign*.90,0,1.94),(.175,.16,.19)))
    arm=rounded('Arm.'+side,(sign*.51,0,.98),(.175,.205,.405), .90)
    arm.rotation_euler[1]=sign*-.48
    parts.append(arm)
    parts.append(rounded('Leg.'+side,(sign*.245,0,.32),(.195,.245,.32), .70))

# Fuse the skin into a continuous editable surface before rigging.
bpy.ops.object.select_all(action='DESELECT')
for o in parts: o.select_set(True)
bpy.context.view_layer.objects.active=parts[0]
bpy.ops.object.join()
body=bpy.context.object; body.name='Character_Skin'
bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
rem=body.modifiers.new('Continuous skin','REMESH'); rem.mode='VOXEL'; rem.voxel_size=.027
bpy.ops.object.modifier_apply(modifier=rem.name)
sm=body.modifiers.new('Soft joins','SMOOTH'); sm.factor=1.0; sm.iterations=5
bpy.ops.object.modifier_apply(modifier=sm.name)
for p in body.data.polygons: p.use_smooth=True

eyes=[]
for sign,side in [(1,'L'),(-1,'R')]:
    # The face is -Y. Eyes follow the curved head surface without floating.
    x=sign*.38; z=1.94
    # Superellipse implicit surface, with the same exponent as the head.
    n=2/.88
    y=-.69*(1-(abs(x)/.91)**n-(abs(z-2.16)/.82)**n)**(1/n)
    eyes.append(rounded('Eye.'+side,(x,y-.012,z),(.090,.035,.183), .70, eye))

armdata=bpy.data.armatures.new('Chibi skeleton')
rig=bpy.data.objects.new('Character_Rig',armdata); bpy.context.collection.objects.link(rig)
bpy.context.view_layer.objects.active=rig
bpy.ops.object.select_all(action='DESELECT'); rig.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
def bone(name,head,tail,parent=None):
    b=armdata.edit_bones.new(name); b.head=head; b.tail=tail
    if parent: b.parent=armdata.edit_bones[parent]
bone('root',(0,0,0),(0,0,.25))
bone('torso',(0,0,.60),(0,0,1.43),'root')
bone('head',(0,0,1.43),(0,0,2.65),'torso')
for sign,side in [(1,'L'),(-1,'R')]:
    bone('arm.'+side,(sign*.34,0,1.28),(sign*.66,0,.65),'torso')
    bone('leg.'+side,(sign*.245,0,.60),(sign*.245,0,.08),'root')
bpy.ops.object.mode_set(mode='OBJECT')
rig.show_in_front=True

groups={n:body.vertex_groups.new(name=n) for n in ['torso','head','arm.L','arm.R','leg.L','leg.R']}
def smooth(a,b,v):
    t=max(0,min(1,(v-a)/(b-a))); return t*t*(3-2*t)
for v in body.data.vertices:
    x,y,z=body.matrix_world @ v.co
    wh=smooth(1.32,1.56,z)
    wa=smooth(.32,.49,abs(x))*(1-smooth(1.22,1.45,z))*(1-wh)
    wl=(1-smooth(.46,.70,z))*(1-wh-wa)
    weights={'head':wh,'arm.'+('L' if x>0 else 'R'):wa,
             'leg.'+('L' if x>0 else 'R'):wl,'torso':max(0,1-wh-wa-wl)}
    for n,w in weights.items():
        if w>0: groups[n].add([v.index],w,'REPLACE')
def bind(obj,group=None):
    if group: obj.vertex_groups.new(name=group).add(list(range(len(obj.data.vertices))),1,'REPLACE')
    mod=obj.modifiers.new('Skeleton deformation','ARMATURE'); mod.object=rig
    obj.parent=rig
bind(body)
for o in eyes: bind(o,'head')

actions={}; samples={'idle':[1,7,13,19], 'run':list(range(1,9)), 'attack':list(range(1,9))}
for name in samples:
    rig.animation_data_create(); rig.animation_data.action=None
    action=bpy.data.actions.new(name); action.use_fake_user=True
    rig.animation_data.action=action
    end={'idle':25,'run':9,'attack':8}[name]
    for frame in range(1,end+1):
        t=(frame-1)/(end-1); wave=math.sin(t*2*math.pi)
        for pb in rig.pose.bones:
            pb.rotation_mode='XYZ'; pb.rotation_euler=(0,0,0); pb.location=(0,0,0); pb.scale=(1,1,1)
        root=rig.pose.bones['root']; torso=rig.pose.bones['torso']; head=rig.pose.bones['head']
        if name=='idle':
            torso.scale=(1+.008*wave,1+.008*wave,1+.012*wave)
            head.rotation_euler[1]=.012*wave
        elif name=='run':
            root.location.z=.045*(1-math.cos(t*4*math.pi))
            torso.rotation_euler[0]=.10
            for s,side in [(1,'L'),(-1,'R')]:
                rig.pose.bones['leg.'+side].rotation_euler[0]=s*.60*wave
                rig.pose.bones['arm.'+side].rotation_euler[0]=-s*.65*wave
            head.rotation_euler[2]=.035*wave
        else:
            # A readable unarmed strike: anticipation, extension, recovery.
            swing=[0,-.25,-.50,.65,1.15,.60,.15,0][frame-1]
            rig.pose.bones['arm.R'].rotation_euler[0]=-swing
            rig.pose.bones['arm.R'].rotation_euler[1]=-.35*max(swing,0)
            rig.pose.bones['arm.L'].rotation_euler[0]=-.22*max(swing,0)
            torso.rotation_euler[2]=.16*swing
            head.rotation_euler[2]=-.08*swing
        for pb in rig.pose.bones:
            for path in ['location','rotation_euler','scale']: pb.keyframe_insert(data_path=path,frame=frame,group=pb.name)
    actions[name]=action

scene=bpy.context.scene; scene.render.engine='CYCLES'
scene.cycles.samples=24; scene.cycles.use_denoising=True
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG'; scene.render.image_settings.color_mode='RGBA'
scene.render.fps=12
scene.view_settings.view_transform='Standard'
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.78,.80,.85,1)
scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.65
def area(name,loc,power,size):
    data=bpy.data.lights.new(name,'AREA'); data.energy=power; data.shape='DISK'; data.size=size
    obj=bpy.data.objects.new(name,data); scene.collection.objects.link(obj); obj.location=loc
    obj.rotation_euler=(Vector((0,0,1.5))-obj.location).to_track_quat('-Z','Y').to_euler()
area('Key softbox',(-3,-4,6),130,4)
area('Fill softbox',(3,-2,3),80,3)
area('Rim softbox',(1,3,5),90,3)
camdata=bpy.data.cameras.new('Orthographic'); cam=bpy.data.objects.new('Camera',camdata)
scene.collection.objects.link(cam); scene.camera=cam; camdata.type='ORTHO'

def camera(elevation=10, ortho=3.9, target=1.57):
    angle=math.radians(elevation); focus=Vector((0,0,target))
    cam.location=focus+Vector((0,-8*math.cos(angle),8*math.sin(angle)))
    cam.rotation_euler=(focus-cam.location).to_track_quat('-Z','Y').to_euler()
    camdata.ortho_scale=ortho

def render(path,size):
    path.parent.mkdir(parents=True,exist_ok=True)
    scene.render.resolution_x=size; scene.render.resolution_y=size; scene.render.resolution_percentage=100
    scene.render.filepath=str(path); bpy.ops.render.render(write_still=True)

rig.animation_data.action=actions['idle']; scene.frame_set(1)
camera(0,3.55,1.49)
render(OUT/'preview_front.png',768)
rig.rotation_euler.z=math.radians(-70)
render(OUT/'preview_three_quarter.png',768)
rig.rotation_euler.z=0

# Game camera: slight overhead view, fixed foot anchor near the existing 95% baseline.
camera(10,4.05,1.82)
scene.frame_start=1; scene.frame_end=24
rig.animation_data.action=actions['idle']; scene.frame_set(1)
scene.render.resolution_x=180; scene.render.resolution_y=180
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'character_base.blend'))
bpy.ops.object.select_all(action='DESELECT')
for o in [rig,body,*eyes]: o.select_set(True)
bpy.context.view_layer.objects.active=rig
bpy.ops.export_scene.gltf(filepath=str(OUT/'character_base.glb'),use_selection=True,export_animations=True)

if FULL:
    for direction,degrees in [('down',0),('left',-90),('up',180),('right',90)]:
        rig.rotation_euler.z=math.radians(degrees)
        for name,frames in samples.items():
            rig.animation_data.action=actions[name]
            for index,frame in enumerate(frames):
                scene.frame_set(frame)
                render(OUT/'renders_180'/f'{name}_{direction}'/f'{index:02}.png',180)
    (OUT/'render_complete.json').write_text(json.dumps({'directions':['down','up','left','right'],'animations':{k:len(v) for k,v in samples.items()}},indent=2))
print('CHARACTER BUILD COMPLETE',flush=True)
