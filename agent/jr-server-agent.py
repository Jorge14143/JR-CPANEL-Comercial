#!/usr/bin/env python3
import json, os, socket, subprocess, time, urllib.request

PANEL=os.environ.get("JR_AGENT_PANEL","").rstrip("/")
TOKEN=os.environ.get("JR_AGENT_TOKEN","")
INTERVAL=max(15,int(os.environ.get("JR_AGENT_INTERVAL","30")))
SERVICES=["nginx","mysql","mariadb","fail2ban","ssh","sshd","cron","docker","NetworkManager","systemd-networkd"]

def sh(*args):
    try:return subprocess.check_output(args,text=True,stderr=subprocess.DEVNULL,timeout=5).strip()
    except:return ""

def cpu():
    def snap():
        v=open("/proc/stat").readline().split()[1:];n=list(map(int,v));return sum(n),n[3]+(n[4] if len(n)>4 else 0)
    a,i=snap();time.sleep(.25);b,j=snap();return round(100*(1-(j-i)/max(1,b-a)))

def metrics():
    mem={k:int(v.split()[0]) for k,v in (line.split(":",1) for line in open("/proc/meminfo")) if k in ("MemTotal","MemAvailable")}
    st=os.statvfs("/");total=st.f_blocks*st.f_frsize;free=st.f_bavail*st.f_frsize
    temp=None
    for base in ("/sys/class/thermal",):
        try:
            vals=[]
            for d in os.listdir(base):
                p=os.path.join(base,d,"temp")
                if os.path.exists(p):
                    v=float(open(p).read().strip())/1000
                    if 0<v<150:vals.append(v)
            if vals:temp=round(max(vals),1)
        except:pass
    return {"cpuPercent":cpu(),"memoryPercent":round(100*(1-mem["MemAvailable"]/mem["MemTotal"])),"diskPercent":round(100*(1-free/max(1,total))),"temperature":temp,"uptime":int(float(open("/proc/uptime").read().split()[0]))}

def services():
    out=[]
    for name in SERVICES:
        load=sh("systemctl","show",name,"-p","LoadState","--value")
        if load!="loaded":continue
        state=sh("systemctl","is-active",name) or "unknown";enabled=sh("systemctl","is-enabled",name) or "unknown"
        out.append({"name":name,"label":name,"state":state,"active":state=="active","enabled":enabled})
    return out

def send():
    body=json.dumps({"hostname":socket.gethostname(),"metrics":metrics(),"services":services()}).encode()
    req=urllib.request.Request(PANEL+"/api/agent/v1/checkin",data=body,headers={"Authorization":"Bearer "+TOKEN,"Content-Type":"application/json","Accept":"application/json","User-Agent":"JR-Server-Agent/1.0"},method="POST")
    with urllib.request.urlopen(req,timeout=10) as r:r.read()

if not PANEL.startswith("https://") or not TOKEN:raise SystemExit("JR_AGENT_PANEL/JR_AGENT_TOKEN faltan")
while True:
    try:send()
    except Exception as e:print("checkin:",e,flush=True)
    time.sleep(INTERVAL)
