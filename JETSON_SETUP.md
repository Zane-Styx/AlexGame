# Jetson Nano GPU Setup Guide

## Automated Setup (Recommended)

### Option 1: Deploy from Windows PC

```powershell
cd ZealGame\python
.\deploy_to_jetson.ps1 -JetsonIP 192.168.1.XXX -Username jetson
```

This will:
- Copy Python files to Jetson
- Install all dependencies
- Set up auto-start service
- Optionally update Java client IP

### Option 2: Manual Setup on Jetson

```bash
# Copy files to Jetson first (from PC)
scp -r python/ jetson@<JETSON_IP>:~/ZealGame-Deploy/

# Then on Jetson:
cd ~/ZealGame-Deploy
chmod +x setup_jetson.sh
./setup_jetson.sh
```

## Quick Start

After setup completes:

```bash
# Start the service
sudo systemctl start zealgame-bridge

# Check if running
sudo systemctl status zealgame-bridge

# View real-time logs
sudo journalctl -u zealgame-bridge -f

# Stop the service
sudo systemctl stop zealgame-bridge
```

The server auto-starts on boot.

## Manual Setup (If Needed)

```bash
# Update system
sudo apt-get update
sudo apt-get upgrade

# Install Python 3.8+ if needed
sudo apt-get install python3-pip python3-dev

# Install OpenCV with CUDA support
sudo apt-get install python3-opencv

# Install MediaPipe
pip3 install mediapipe

# Copy Python folder to Jetson
# scp -r python/ jetson@<JETSON_IP>:~/ZealGame/
```

## 2. Run Python Backend on Jetson

```bash
cd ~/ZealGame/python
python3 bridge_server.py
```

The server will auto-detect GPU and print:
```
[HandTracker] GPU delegate enabled (Jetson detected)
[python-bridge] listening on 0.0.0.0:9009 (threaded)
```

## 3. Update Java Client

Change the IP address in `GameTwoScreen.java` and `GameOneScreen.java`:

```java
// From:
pythonClient = new PythonBridgeClient("127.0.0.1", 9009);

// To:
pythonClient = new PythonBridgeClient("192.168.1.XXX", 9009);  // Your Jetson IP
```

## 4. Configure Network

Make sure:
- Jetson and PC are on same network
- Port 9009 is not blocked by firewall
- Test connection: `ping <JETSON_IP>` from PC

## Performance Gains

- **CPU (Intel)**: ~10-15 FPS, high lag
- **GPU (Jetson)**: ~30-60 FPS, minimal lag
- 5-10x faster hand tracking
- Can use full resolution (no downscaling needed)
- Process every frame (no skipping needed)

## Troubleshooting

### GPU Not Detected
Check Jetson identification:
```bash
cat /etc/nv_tegra_release
```

### Connection Refused
Update bridge_server.py to listen on all interfaces:
```python
run(host="0.0.0.0", port=9009)
```

### Slow Performance
Monitor GPU usage:
```bash
sudo tegrastats
```
