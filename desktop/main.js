const {app,BrowserWindow,dialog,ipcMain}=require('electron');
const fs=require('node:fs');const path=require('node:path');const {spawn}=require('node:child_process');const {pathToFileURL}=require('node:url');
let ui;let busy=false;
function createWindow(){ui=new BrowserWindow({width:950,height:650,webPreferences:{nodeIntegration:true,contextIsolation:false}});ui.loadFile(path.join(__dirname,'index.html'));}
app.whenReady().then(createWindow);app.on('window-all-closed',()=>app.quit());
ipcMain.handle('choose-html',async()=>{const r=await dialog.showOpenDialog(ui,{properties:['openFile'],filters:[{name:'HTML',extensions:['html','htm']} ]});return r.canceled?null:r.filePaths[0]});
function ffmpegPath(){let p=require('ffmpeg-static');if(app.isPackaged)p=p.replace('app.asar','app.asar.unpacked');return p}
ipcMain.handle('export-video',async(_,o)=>{
if(busy)throw Error('Ya hay una exportación en curso');
if(!o||typeof o.file!=='string'||!fs.existsSync(o.file)||!Number.isInteger(o.width)||!Number.isInteger(o.height)||![24,30].includes(o.fps)||!Number.isInteger(o.seconds)||o.seconds<1||o.seconds>600)throw Error('Parámetros incorrectos');
const {file,width,height,fps,seconds}=o;
const save=await dialog.showSaveDialog(ui,{defaultPath:'DRUGO-video.mp4',filters:[{name:'MP4',extensions:['mp4']}]});if(save.canceled||!save.filePath)return null;
busy=true;let render=null,proc=null;
try{
render=new BrowserWindow({show:false,width,height,frame:false,transparent:false,webPreferences:{offscreen:true,nodeIntegration:false,contextIsolation:true,webSecurity:true,backgroundThrottling:false}});
await render.loadURL(pathToFileURL(file).href);
const hasClock=await render.webContents.executeJavaScript("typeof window.drugoRenderAt === 'function'");
if(!hasClock)throw Error('El HTML necesita window.drugoRenderAt(ms). La exportación genérica no está implementada.');
const args=['-y','-f','image2pipe','-vcodec','mjpeg','-r',String(fps),'-i','pipe:0','-an','-c:v','libx264','-preset','veryfast','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',save.filePath];
proc=spawn(ffmpegPath(),args,{windowsHide:true,stdio:['pipe','ignore','pipe']});let stderr='';proc.stderr.on('data',b=>{stderr=(stderr+b.toString()).slice(-5000)});
const done=new Promise((resolve,reject)=>{proc.on('error',reject);proc.on('close',code=>code===0?resolve():reject(Error('FFmpeg terminó con código '+code+': '+stderr)))});
const total=seconds*fps;
for(let i=0;i<total;i++){
const ms=Math.round(i*1000/fps);
await render.webContents.executeJavaScript('window.drugoRenderAt('+ms+')');
const frame=await render.webContents.capturePage();
const jpeg=frame.toJPEG(95);
if(!proc.stdin.write(jpeg))await new Promise((resolve,reject)=>{proc.stdin.once('drain',resolve);proc.stdin.once('error',reject)});
if(i%15===0||i===total-1)ui?.webContents.send('export-progress',{frame:i+1,total,percent:Math.round((i+1)*100/total)});
}
proc.stdin.end();await done;return save.filePath;
}catch(e){if(proc&&!proc.killed){proc.stdin.destroy();proc.kill()}throw e}finally{if(render&&!render.isDestroyed())render.destroy();busy=false}
});
