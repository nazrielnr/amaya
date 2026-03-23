import * as os from 'os';

const LOCALHOST_PATTERNS = [
    /localhost/i,
    /127\.0\.0\.1/i,
    /0\.0\.0\.0/i,
    /::1/i,
];

const PORT_PATTERN = /:(\d{1,5})/;

/**
 * URL_PATTERN breakdown:
 * Group 1: Full URL string
 * Group 2: Protocol (http:// or https://) - Optional
 * Group 3: Host (localhost, 127.0.0.1, etc.)
 * Group 4: Port (:3000) - Optional
 * Group 5: Path (/foo/bar) - Optional
 * 
 * We use non-capturing groups (?:) where we don't need the specific fragment.
 * Logic: Must have (Protocol) OR (Port) to match, to avoid plain "localhost" matches.
 */
const URL_PATTERN = /((https?:\/\/)(localhost|127\.0\.0\.1|0\.0\.0\.0|::1)(?::\d{1,5})?(?:\/[^\s]*)?|(localhost|127\.0\.0\.1|0\.0\.0\.0|::1)(:\d{1,5})(?:\/[^\s]*)?)/gi;

export interface LocalhostLink {
    original: string;
    converted: string;
    fullUrl: string;
    port?: string;
    path?: string;
}

export function getLocalIp(): string {
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

    if (addresses.length === 0) return '127.0.0.1';

    const priorityIp = addresses.find(ip => {
        const isVbox = ip.startsWith('192.168.56.');
        const isWSL = ip.startsWith('172.');
        return !isVbox && !isWSL;
    });

    return priorityIp || addresses[0];
}

export function isLocalhostUrl(text: string): boolean {
    return LOCALHOST_PATTERNS.some(pattern => pattern.test(text));
}

export function extractPort(url: string): string | undefined {
    const match = url.match(PORT_PATTERN);
    return match ? match[1] : undefined;
}

export function convertLocalhostToIp(text: string, localIp?: string): { converted: string; links: LocalhostLink[] } {
    if (!text || typeof text !== 'string') {
        return { converted: text, links: [] };
    }

    const ip = localIp || getLocalIp();
    const links: LocalhostLink[] = [];
    let converted = text;

    // We need to use exec or matchAll carefully because manual group counting is error-prone.
    // Instead of complex groups, let's parse the match fragment.
    const regex = new RegExp(URL_PATTERN);
    let match;
    while ((match = regex.exec(text)) !== null) {
        const fullMatch = match[0];
        
        // Parse the match to find host and port
        let host = '';
        let port = '';
        let protocol = '';
        let path = '';

        if (fullMatch.includes('://')) {
            protocol = fullMatch.split('://')[0] + '://';
            const afterProto = fullMatch.split('://')[1];
            host = afterProto.split(/[:/]/)[0];
            const portMatch = afterProto.match(PORT_PATTERN);
            port = portMatch ? portMatch[0] : '';
            path = afterProto.includes('/') ? '/' + afterProto.split('/').slice(1).join('/') : '';
        } else {
            protocol = 'http://';
            host = fullMatch.split(/[:/]/)[0];
            const portMatch = fullMatch.match(PORT_PATTERN);
            port = portMatch ? portMatch[0] : '';
            path = fullMatch.includes('/') ? '/' + fullMatch.split('/').slice(1).join('/') : '';
        }

        if (!LOCALHOST_PATTERNS.some(pattern => pattern.test(host))) {
            continue;
        }

        const actualPort = port.replace(':', '') || '3000';
        const finalUrl = `${protocol}${ip}:${actualPort}${path}`;

        const link: LocalhostLink = {
            original: fullMatch,
            converted: `[${ip}:${actualPort}${path}](${finalUrl})`,
            fullUrl: finalUrl,
            port: actualPort,
            path: path,
        };

        links.push(link);
    }

    if (links.length > 0) {
        // Replace from longest to shortest to avoid partial replacements
        const sortedLinks = [...links].sort((a, b) => b.original.length - a.original.length);
        sortedLinks.forEach(link => {
            // Use split/join for global replacement without regex escaping issues
            converted = converted.split(link.original).join(link.converted);
        });
    }

    return { converted, links };
}

export function convertLocalhostToHtmlLink(text: string, localIp?: string): { converted: string; links: LocalhostLink[] } {
    const result = convertLocalhostToIp(text, localIp);
    // This is used for terminal or other web-like views where we just want the URL
    return {
        converted: result.converted,
        links: result.links
    };
}

export function formatLocalhostAsPlainLink(text: string, localIp?: string): string {
    if (!text || typeof text !== 'string') {
        return text;
    }

    const ip = localIp || getLocalIp();
    const regex = new RegExp(URL_PATTERN);
    let result = text;
    const replacements: { original: string; replacement: string }[] = [];

    let match;
    while ((match = regex.exec(text)) !== null) {
        const fullMatch = match[0];
        
        let host = '';
        let port = '';
        let protocol = '';
        let path = '';

        if (fullMatch.includes('://')) {
            protocol = fullMatch.split('://')[0] + '://';
            const afterProto = fullMatch.split('://')[1];
            host = afterProto.split(/[:/]/)[0];
            const portMatch = afterProto.match(PORT_PATTERN);
            port = portMatch ? portMatch[0] : '';
            path = afterProto.includes('/') ? '/' + afterProto.split('/').slice(1).join('/') : '';
        } else {
            protocol = 'http://';
            host = fullMatch.split(/[:/]/)[0];
            const portMatch = fullMatch.match(PORT_PATTERN);
            port = portMatch ? portMatch[0] : '';
            path = fullMatch.includes('/') ? '/' + fullMatch.split('/').slice(1).join('/') : '';
        }

        if (!LOCALHOST_PATTERNS.some(pattern => pattern.test(host))) {
            continue;
        }

        const actualPort = port.replace(':', '') || '3000';
        const finalUrl = `${protocol}${ip}:${actualPort}${path}`;
        replacements.push({ original: fullMatch, replacement: finalUrl });
    }

    // Sort by length descending to avoid nested replacement issues
    replacements.sort((a, b) => b.original.length - a.original.length);
    replacements.forEach(rep => {
        result = result.split(rep.original).join(rep.replacement);
    });

    return result;
}

export function hasLocalhostLink(text: string): boolean {
    if (!text) return false;
    const regex = new RegExp(URL_PATTERN);
    return regex.test(text);
}

export const LOCALHOST_URL_REGEX = URL_PATTERN;
