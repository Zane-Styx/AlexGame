#!/bin/bash
# Jetson Nano Automated Setup Script for ZealGame Hand Tracking

set -e  # Exit on error

echo "=========================================="
echo "ZealGame Jetson Nano Setup"
echo "=========================================="
echo ""

# Check if running on Jetson
if [ ! -f /etc/nv_tegra_release ]; then
    echo "WARNING: This doesn't appear to be a Jetson device!"
    read -p "Continue anyway? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Update system
echo "[1/6] Updating system packages..."
sudo apt-get update
sudo apt-get upgrade -y

# Install Python dependencies
echo "[2/6] Installing Python and pip..."
sudo apt-get install -y python3 python3-pip python3-dev

# Install OpenCV
echo "[3/6] Installing OpenCV..."
sudo apt-get install -y python3-opencv

# Install MediaPipe and other Python packages
echo "[4/6] Installing MediaPipe and dependencies..."
pip3 install --upgrade pip
pip3 install mediapipe numpy

# Set up project directory
echo "[5/6] Setting up project directory..."
INSTALL_DIR="$HOME/ZealGame"
mkdir -p "$INSTALL_DIR"

# Copy Python files if they exist in current directory
if [ -d "$(pwd)/vision" ] && [ -f "$(pwd)/bridge_server.py" ]; then
    echo "Copying Python files from current directory..."
    cp -r vision/ "$INSTALL_DIR/"
    cp -r game/ "$INSTALL_DIR/" 2>/dev/null || true
    cp bridge_server.py "$INSTALL_DIR/"
else
    echo "Python files not found in current directory."
    echo "Please copy the python folder contents to: $INSTALL_DIR"
fi

# Create systemd service for auto-start
echo "[6/6] Creating systemd service..."
sudo tee /etc/systemd/system/zealgame-bridge.service > /dev/null <<EOF
[Unit]
Description=ZealGame Python Bridge Server
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/python3 $INSTALL_DIR/bridge_server.py
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Enable but don't start the service yet
sudo systemctl daemon-reload
sudo systemctl enable zealgame-bridge.service

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Jetson IP Address:"
hostname -I
echo ""
echo "To start the bridge server:"
echo "  sudo systemctl start zealgame-bridge"
echo ""
echo "To stop the bridge server:"
echo "  sudo systemctl stop zealgame-bridge"
echo ""
echo "To view logs:"
echo "  sudo journalctl -u zealgame-bridge -f"
echo ""
echo "Or run manually:"
echo "  cd $INSTALL_DIR && python3 bridge_server.py"
echo ""
echo "Next steps:"
echo "1. Update Java client to use this Jetson IP address"
echo "2. Start the service: sudo systemctl start zealgame-bridge"
echo "3. Test from PC: telnet <JETSON_IP> 9009"
echo ""
