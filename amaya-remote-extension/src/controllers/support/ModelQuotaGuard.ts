type QuotaInfo = {
    remainingFraction?: number;
    resetTime?: string;
};

type GuardModel = {
    id: string;
    label: string;
    supportsImages?: boolean;
    quotaInfo?: QuotaInfo;
};

export class ModelQuotaGuard {
    validateSend(models: GuardModel[], selectedModelId: string): string | null {
        const selectedModel = models.find((m) => m.id === selectedModelId);
        if (!selectedModel?.quotaInfo) return null;

        const isExhausted =
            selectedModel.quotaInfo.remainingFraction === undefined ||
            selectedModel.quotaInfo.remainingFraction === 0;
        if (!isExhausted) return null;

        let errorMsg = `Model "${selectedModel.label}" quota is exhausted.`;
        if (selectedModel.quotaInfo.resetTime) {
            errorMsg += ` Quota will reset at ${this.formatResetTime(selectedModel.quotaInfo.resetTime)}.`;
        }
        return errorMsg;
    }

    private formatResetTime(raw: string): string {
        const isoDate = new Date(raw);
        if (!Number.isNaN(isoDate.getTime())) {
            return this.formatDate(isoDate);
        }

        const altRe = /(\d{2}-\d{2}-\d{2}t\d{2}:\d{2}:\d{2}z)/gi;
        return raw.replace(altRe, (altStr: string) => {
            try {
                const parts = altStr.toLowerCase().split('t')[0].split('-');
                const day = parseInt(parts[0], 10);
                const month = parseInt(parts[1], 10);
                let year = parseInt(parts[2], 10);
                if (year < 100) year += 2000;

                const timeParts = altStr.toLowerCase().split('t')[1].replace('z', '').split(':');
                const hours = parseInt(timeParts[0], 10);
                const minutes = parseInt(timeParts[1], 10);

                return this.formatDate(new Date(year, month - 1, day, hours, minutes));
            } catch {
                return altStr;
            }
        });
    }

    private formatDate(date: Date): string {
        const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
        return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} jam ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
}
