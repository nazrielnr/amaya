export class ErrorMessageFormatter {
    format(input: any): { message: string; raw: string } {
        const raw = typeof input === 'string' ? input : (() => {
            try { return JSON.stringify(input); } catch { return String(input); }
        })();

        let msg = typeof input === 'string' ? input : raw;
        msg = (msg || '').toString();

        const pickFromRaw = (text: string): string | null => {
            if (!text) return null;
            const candidates = [
                /"userErrorMessage"\s*:\s*"([^"]+)"/i,
                /"shortError"\s*:\s*"([^"]+)"/i,
                /"modelErrorMessage"\s*:\s*"([^"]+)"/i,
                /"fullError"\s*:\s*"([^"]+)"/i,
            ];
            for (const re of candidates) {
                const m = text.match(re);
                if (m && m[1]) {
                    return m[1].replace(/\\n/g, '\n').replace(/\\"/g, '"').trim();
                }
            }
            return null;
        };

        const extracted = pickFromRaw(msg) || pickFromRaw(raw);
        if (extracted) {
            msg = extracted;
        }

        const afterRe = /(reset\s+after\s+)([0-9dhms]+)\b/i;
        const m = msg.match(afterRe);
        if (m) {
            const dur = m[2];
            let totalMs = 0;
            const parts = Array.from(dur.matchAll(/(\d+)([dhms])/gi));
            for (const p of parts) {
                const v = parseInt(p[1], 10);
                const u = (p[2] || '').toLowerCase();
                if (isNaN(v)) continue;
                if (u === 'd') totalMs += v * 24 * 60 * 60 * 1000;
                if (u === 'h') totalMs += v * 60 * 60 * 1000;
                if (u === 'm') totalMs += v * 60 * 1000;
                if (u === 's') totalMs += v * 1000;
            }
            if (totalMs > 0) {
                const dt = new Date(Date.now() + totalMs);
                const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                const formatted = `${pad(dt.getDate())}/${pad(dt.getMonth() + 1)}/${dt.getFullYear()} jam ${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
                msg = msg.replace(afterRe, `$1${formatted}`.replace(/reset after /i, 'reset at '));
            }
        }

        const isoRe = /(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2}))/g;
        msg = msg.replace(isoRe, (isoStr: string) => {
            try {
                const date = new Date(isoStr);
                const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} jam ${pad(date.getHours())}:${pad(date.getMinutes())}`;
            } catch {
                return isoStr;
            }
        });

        const altRe = /(\d{2}-\d{2}-\d{2}t\d{2}:\d{2}:\d{2}z)/gi;
        msg = msg.replace(altRe, (altStr: string) => {
            try {
                const parts = altStr.toLowerCase().split('t')[0].split('-');
                const day = parseInt(parts[0], 10);
                const month = parseInt(parts[1], 10);
                let year = parseInt(parts[2], 10);
                if (year < 100) year += 2000;

                const timeParts = altStr.toLowerCase().split('t')[1].replace('z', '').split(':');
                const hours = parseInt(timeParts[0], 10);
                const minutes = parseInt(timeParts[1], 10);

                const date = new Date(year, month - 1, day, hours, minutes);
                const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} jam ${pad(date.getHours())}:${pad(date.getMinutes())}`;
            } catch {
                return altStr;
            }
        });

        if (msg.length > 600) msg = `${msg.substring(0, 600)}...`;
        if (!msg.trim()) msg = 'Unknown error';
        return { message: msg.trim(), raw };
    }
}
