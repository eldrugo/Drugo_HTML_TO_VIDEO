const {app,BrowserWindow,dialog,ipcMain}=require('electron');
const fs=require('node:fs');const path=require('node:path');const {spawn}=require('node:child_process');const {pathToFileURL}=require('node:url');
let ui;let busy=false;
function createWindow(){ui=new BrowserWindow({width:950,height:650,webPreferences:{nodeIntegration:true,contextIsolation:false}});ui.loadFile(path.join(__dirname,'index.html'));}
app.whenReady().then(createWindow);app.on('window-all-closed',()=>app.quit());
ipcMain.handle('choose-audio',async()=>{const r=await dialog.showOpenDialog(ui,{properties:['openFile','multiSelections'],filters:[{name:'Audio',extensions:['mp3','wav','m4a','aac','ogg','flac']} ]});return r.canceled?[]:r.filePaths});
ipcMain.handle('choose-html',async()=>{const r=await dialog.showOpenDialog(ui,{properties:['openFile'],filters:[{name:'HTML',extensions:['html','htm']} ]});return r.canceled?null:r.filePaths[0]});
function ffmpegPath(){let p=require('ffmpeg-static');if(app.isPackaged)p=p.replace('app.asar','app.asar.unpacked');return p}
ipcMain.handle('export-video',async(_,o)=>{
if(busy)throw Error('Ya hay una exportación en curso');
if(!o||typeof o.file!=='string'||!fs.existsSync(o.file)||!Number.isInteger(o.width)||!Number.isInteger(o.height)||![24,30].includes(o.fps)||!Number.isInteger(o.seconds)||o.seconds<1||o.seconds>600)throw Error('Parámetros incorrectos');
const {file,width,height,fps,seconds}=o;
const tracks=Array.isArray(o.tracks)?o.tracks:[];
if(tracks.length>12||tracks.some(t=>!t||typeof t.file!=='string'||!fs.existsSync(t.file)||!['.mp3','.wav','.m4a','.aac','.ogg','.flac'].includes(path.extname(t.file).toLowerCase())||!Number.isFinite(t.offset)||t.offset<0||t.offset>=seconds||!Number.isFinite(t.volume)||t.volume<0||t.volume>2))throw Error('Pistas de audio incorrectas');
const save=await dialog.showSaveDialog(ui,{defaultPath:'DRUGO-video.mp4',filters:[{name:'MP4',extensions:['mp4']}]});if(save.canceled||!save.filePath)return null;
busy=true;let render=null,proc=null;
try{
render=new BrowserWindow({show:false,width,height,frame:false,transparent:false,webPreferences:{offscreen:true,nodeIntegration:false,contextIsolation:true,webSecurity:true,backgroundThrottling:false}});
await render.loadURL(pathToFileURL(file).href);
const hasClock=await render.webContents.executeJavaScript("typeof window.drugoRenderAt === 'function'");
const startTime=Date.now();
if(hasClock)await render.webContents.executeJavaScript('window.__drugoDurationMs='+seconds*1000);
const args=['-y','-f','image2pipe','-vcodec','mjpeg','-r',String(fps),'-i','pipe:0'];
for(const track of tracks)args.push('-i',track.file);
if(tracks.length){
 const filters=tracks.map((track,i)=>'['+(i+1)+':a]aresample=48000,volume='+track.volume+',adelay='+Math.round(track.offset*1000)+'|'+Math.round(track.offset*1000)+'[a'+i+']');
 filters.push(tracks.map((_,i)=>'[a'+i+']').join('')+'amix=inputs='+tracks.length+':duration=longest:dropout_transition=0,atrim=duration='+seconds+',asetpts=N/SR/TB[aout]');
 args.push('-filter_complex',filters.join(';'),'-map','0:v:0','-map','[aout]','-c:a','aac','-b:a','192k');
}else args.push('-an');
args.push('-c:v','libx264','-preset','veryfast','-crf','18','-pix_fmt','yuv420p','-t',String(seconds),'-movflags','+faststart',save.filePath);
proc=spawn(ffmpegPath(),args,{windowsHide:true,stdio:['pipe','ignore','pipe']});let stderr='';proc.stderr.on('data',b=>{stderr=(stderr+b.toString()).slice(-5000)});
const done=new Promise((resolve,reject)=>{proc.on('error',reject);proc.on('close',code=>code===0?resolve():reject(Error('FFmpeg terminó con código '+code+': '+stderr)))});
const total=seconds*fps;
for(let i=0;i<total;i++){
const ms=Math.round(i*1000/fps);
if(hasClock){await render.webContents.executeJavaScript('window.drugoRenderAt('+ms+')')}else{const remaining=ms-(Date.now()-startTime);if(remaining>0)await new Promise(resolve=>setTimeout(resolve,remaining));}
const frame=await render.webContents.capturePage();
const jpeg=frame.toJPEG(95);
if(!proc.stdin.write(jpeg))await new Promise((resolve,reject)=>{proc.stdin.once('drain',resolve);proc.stdin.once('error',reject)});
if(i%15===0||i===total-1)ui?.webContents.send('export-progress',{frame:i+1,total,percent:Math.round((i+1)*100/total)});
}
proc.stdin.end();await done;return save.filePath;
}catch(e){if(proc&&!proc.killed){proc.stdin.destroy();proc.kill()}throw e}finally{if(render&&!render.isDestroyed())render.destroy();busy=false}
});
