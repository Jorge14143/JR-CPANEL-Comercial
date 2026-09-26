"use strict";
const express=require("express"),os=require("os"),fs=require("fs");
const {execFile}=require("child_process"),{promisify}=require("util");
const execFileAsync=promisify(execFile),app=express();
const HOST=process.env.HOST||"127.0.0.1",PORT=Number(process.env.PORT||3100);
let cpuPrev=null,netPrev=null;
app.disable("x-powered-by");app.use(express.static("public",{extensions:["html"]}));
async function cmd(file,args=[]){try{const {stdout}=await execFileAsync(file,args,{timeout:2500,maxBuffer:1024*1024});return stdout.trim()}catch{return""}}
async function disk(){const out=await cmd("df",["-B1","--output=size,used,avail,pcent","/"]);const l=out.split("\n").filter(Boolean).pop()?.trim().split(/\s+/)||[];return{total:+l[0]||0,used:+l[1]||0,free:+l[2]||0,percent:+String(l[3]||"0").replace("%","")}}
async function active(n){return(await cmd("systemctl",["is-active",n]))==="active"}
async function pm2(){const raw=await cmd("pm2",["jlist"]);if(!raw)return[];try{return JSON.parse(raw).map(p=>({name:p.name,status:p.pm2_env?.status||"unknown",cpu:p.monit?.cpu||0,memory:p.monit?.memory||0,restarts:p.pm2_env?.restart_time||0}))}catch{return[]}}
function cpuUsage(){const cp=os.cpus();let idle=0,total=0;for(const c of cp){idle+=c.times.idle;total+=Object.values(c.times).reduce((a,b)=>a+b,0)}const cur={idle,total};let pct=0;if(cpuPrev){const dt=cur.total-cpuPrev.total,di=cur.idle-cpuPrev.idle;pct=dt>0?Math.max(0,Math.min(100,Math.round((1-di/dt)*100))):0}cpuPrev=cur;return pct}
function temperature(){try{const vals=fs.readdirSync("/sys/class/thermal").filter(x=>x.startsWith("thermal_zone")).map(x=>Number(fs.readFileSync("/sys/class/thermal/"+x+"/temp","utf8").trim())/1000).filter(x=>x>0&&x<150);return vals.length?Math.round(Math.max(...vals)*10)/10:null}catch{return null}}
function network(){const ni=os.networkInterfaces();let privateIp=null,iface=null;for(const [n,arr] of Object.entries(ni)){const v=arr?.find(x=>x.family==="IPv4"&&!x.internal);if(v){privateIp=v.address;iface=n;break}}let rx=0,tx=0;if(iface){try{rx=+fs.readFileSync("/sys/class/net/"+iface+"/statistics/rx_bytes","utf8")||0;tx=+fs.readFileSync("/sys/class/net/"+iface+"/statistics/tx_bytes","utf8")||0}catch{}}const now=Date.now(),cur={rx,tx,now},rate=netPrev?{rx:Math.max(0,(rx-netPrev.rx)/((now-netPrev.now)/1000)),tx:Math.max(0,(tx-netPrev.tx)/((now-netPrev.now)/1000))}:{rx:0,tx:0};netPrev=cur;return{interface:iface,privateIp,rxBytes:rx,txBytes:tx,rxPerSec:Math.round(rate.rx),txPerSec:Math.round(rate.tx)}}
async function publicIp(){const r=await cmd("curl",["-4","-fsS","--max-time","2","https://api.ipify.org"]);return /^\d{1,3}(\.\d{1,3}){3}$/.test(r)?r:null}
app.get("/api/dashboard",async(req,res)=>{const total=os.totalmem(),free=os.freemem(),cpu=cpuUsage(),net=network();const [d,processes,nginx,mysql,fail2ban,ddns,pub]=await Promise.all([disk(),pm2(),active("nginx"),active("mysql"),active("fail2ban"),active("jr-cloudflare-ddns.timer"),publicIp()]);res.set("Cache-Control","no-store").json({hostname:os.hostname(),platform:os.platform(),release:os.release(),uptime:os.uptime(),load:os.loadavg(),cpuCount:os.cpus().length,cpuPercent:cpu,temperature:temperature(),network:{...net,publicIp:pub},memory:{total,used:total-free,free,percent:Math.round((total-free)/total*100)},disk:d,pm2:processes,services:{nginx,mysql,fail2ban,ddns},time:new Date().toISOString()})});
app.get("/health",(req,res)=>res.json({ok:true,version:"0.2.0"}));
app.listen(PORT,HOST,()=>console.log(`JR Server Panel v0.2.0 → http://${HOST}:${PORT}`));
