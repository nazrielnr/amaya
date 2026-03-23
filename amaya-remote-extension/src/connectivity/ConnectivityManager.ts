import * as vscode from 'vscode';
import * as qr from 'qrcode';
import * as ip from 'ip';
import * as os from 'os';
import { execSync } from 'child_process';

/**
 * Manages connectivity discovery and displays connection info (QR code).
 */
export class ConnectivityManager {
    private static outputChannel: vscode.OutputChannel;

    static initialize() {
        this.outputChannel = vscode.window.createOutputChannel("Amaya Remote Session");
    }

    static async showConnectionInfo(port: number) {
        if (!this.outputChannel) this.initialize();

        const allIps = this.getAllLocalIps();
        const localIp = this.getBestLocalIp(allIps);
        const bluetoothMac = await this.getBluetoothMacAsync();

        // Protocol: amaya://connect?ip=...&port=...&bt=...
        const connectionString = `amaya://connect?ip=${localIp}&port=${port}${bluetoothMac ? `&bt=${bluetoothMac}` : ''}`;

        try {
            const qrCode = await qr.toString(connectionString, { type: 'utf8' });

            this.outputChannel.clear();
            this.outputChannel.show(true);
            this.outputChannel.appendLine("====================================================");
            this.outputChannel.appendLine("           AMAYA REMOTE SESSION: READY              ");
            this.outputChannel.appendLine("====================================================");
            this.outputChannel.appendLine("");
            this.outputChannel.appendLine("Scan this QR code in the Amaya Android app to connect:");
            this.outputChannel.appendLine("");
            this.outputChannel.appendLine(qrCode);
            this.outputChannel.appendLine("");
            this.outputChannel.appendLine(`IP Address: ${localIp}`);
            if (allIps.length > 1) {
                this.outputChannel.appendLine(`Other IPs : ${allIps.filter(i => i !== localIp).join(', ')}`);
            }
            this.outputChannel.appendLine(`Port      : ${port}`);
            if (bluetoothMac) {
                this.outputChannel.appendLine(`Bluetooth : ${bluetoothMac}`);
            }
            this.outputChannel.appendLine("");
            this.outputChannel.appendLine("Make sure both devices are on the same network.");
            this.outputChannel.appendLine("====================================================");

        } catch (err) {
            console.error('[Amaya Remote] QR generation failed:', err);
            vscode.window.showErrorMessage("Failed to generate QR code for connection.");
        }
    }

    private static async getBluetoothMacAsync(): Promise<string | null> {
        return new Promise((resolve) => {
            try {
                const { exec } = require('child_process');
                exec('getmac /v /fo csv', (error: any, stdout: string) => {
                    if (error) return resolve(null);
                    const lines = stdout.split('\n');
                    for (const line of lines) {
                        if (line.toLowerCase().includes('bluetooth')) {
                            const parts = line.split(',');
                            const macPart = parts.find((p: string) => /([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})/.test(p));
                            if (macPart) return resolve(macPart.replace(/"/g, '').trim());
                        }
                    }
                    resolve(null);
                });
            } catch {
                resolve(null);
            }
        });
    }

    private static getAllLocalIps(): string[] {
        const interfaces = os.networkInterfaces();
        const addresses: string[] = [];
        for (const k in interfaces) {
            const iface = interfaces[k];
            if (!iface) continue;
            for (const address of iface) {
                if (address.family === 'IPv4' && !address.internal) {
                    addresses.push(address.address);
                }
            }
        }
        return addresses;
    }

    private static getBestLocalIp(ips: string[]): string {
        if (ips.length === 0) return '127.0.0.1';
        // Prioritize common Wi-Fi/Ethernet subnets, avoid VirtualBox (192.168.56.x)
        const priorityIp = ips.find(ip => {
            const isVbox = ip.startsWith('192.168.56.');
            const isWSL = ip.startsWith('172.');
            return !isVbox && !isWSL;
        });
        return priorityIp || ips[0];
    }
}
