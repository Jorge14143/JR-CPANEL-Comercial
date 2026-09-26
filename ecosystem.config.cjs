module.exports = {
  apps: [{
    name: "jr-server-panel",
    script: "./server.js",
    cwd: __dirname,
    instances: 1,
    exec_mode: "fork",
    autorestart: true,
    watch: false,
    max_memory_restart: "250M",
    restart_delay: 2000,
    kill_timeout: 5000,
    env: {
      NODE_ENV: "production",
      HOST: "127.0.0.1",
      PORT: "3100"
    }
  }]
};
