"use strict";
const express=require("express");
const os=require("os");
const {execFile}=require("child_process");
const {promisify}=require("util");
const execFileAsync=promisify(execFile);
const app=express();
const HOST=process.env.HOST||"127.0.0.1";
const PORT=Number(process.env.PORT||3100);
app.disable("x-powered-by");
app.use(express.static("public",{extensions:["html"]}));

async function cmd(file,args=[]){
  try { const {stdout}=await execFileAsync(file,args,{timeout:2500,maxBuffer:1024*1024}); return stdout.trim(); }
  catch { return ""; }
}
async function disk(){
  const out=await cmd("df",["-B1","--output=size,used,avail,pcent","/"]);
  const l=out.split("\n").filter(Boolean).pop()?.trim().split(/\s+/)||[];
  return {total:Number(l[0]||0),used:Number(l[1]||0),free:Number(l[2]||0),percent:Number((l[3]||"0").replace("%",""))};
}
async function active(name){ return (await cmd("systemctl",["is-active",name]))==="active"; }
async function pm2(){
  const raw=await cmd("pm2",["jlist"]); if(!raw)return [];
  try{return JSON.parse(raw).map(p=>({name:p.name,status:p.pm2_env?.status||"unknown",cpu:p.monit?.cpu||0,memory:p.monit?.memory||0,restarts:p.pm2_env?.restart_time||0}));}catch{return [];}
}
app.get("/api/dashboard",async(req,res)=>{
  const total=os.totalmem(), free=os.freemem();
  const [d,processes,nginx,mysql,fail2ban,ddns]=await Promise.all([
    disk(),pm2(),active("nginx"),active("mysql"),active("fail2ban"),active("jr-cloudflare-ddns.timer")
  ]);
  res.set("Cache-Control","no-store").json({
    hostname:os.hostname(),platform:os.platform(),release:os.release(),
    uptime:os.uptime(),load:os.loadavg(),cpuCount:os.cpus().length,
    memory:{total,used:total-free,free,percent:Math.round((total-free)/total*100)},
    disk:d,pm2:processes,services:{nginx,mysql,fail2ban,ddns},time:new Date().toISOString()
  });
});
app.get("/health",(req,res)=>res.json({ok:true,version:"0.1.0"}));
app.listen(PORT,HOST,()=>console.log(`JR Server Panel v0.1.0 → http://${HOST}:${PORT}`));
