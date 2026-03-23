import { IDEMessageAttachment } from '../../interfaces/IIDEClient';

export type AttachmentParseResult = {
    attachments: IDEMessageAttachment[];
    errors: string[];
};

export class AttachmentValidator {
    parse(raw: any): AttachmentParseResult {
        const errors: string[] = [];
        if (!Array.isArray(raw)) return { attachments: [], errors };

        const parsed: (IDEMessageAttachment | null)[] = raw.map((item: any, index: number) => {
            const mimeType = typeof item?.mimeType === 'string' ? item.mimeType.trim() : '';
            const dataBase64 = typeof item?.dataBase64 === 'string' ? item.dataBase64.trim() : '';
            const fileName = typeof item?.fileName === 'string' ? item.fileName : undefined;

            if (!mimeType) {
                errors.push(`Attachment ${index + 1}: missing mimeType`);
                return null;
            }
            if (!dataBase64) {
                errors.push(`Attachment ${index + 1}: missing dataBase64`);
                return null;
            }

            const hasDataUriPrefix = dataBase64.startsWith('data:');
            const actualBase64 = hasDataUriPrefix ? dataBase64.split(',')[1] : dataBase64;
            if (!actualBase64 || actualBase64.length === 0) {
                errors.push(`Attachment ${index + 1}: invalid base64 data`);
                return null;
            }

            const attachment: IDEMessageAttachment = { mimeType, dataBase64 };
            if (fileName) attachment.fileName = fileName;
            return attachment;
        });

        const attachments = parsed.filter((item): item is IDEMessageAttachment => item !== null);
        return { attachments, errors };
    }
}
