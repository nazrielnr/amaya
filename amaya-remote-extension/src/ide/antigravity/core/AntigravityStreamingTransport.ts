import * as http from 'http';
import { IIDEStreamCallbacks, IIDEStreamingTransport } from '../../../interfaces/IIDEStreamingTransport';
import { AntigravityHttpCore } from './AntigravityHttpCore';
import { streamCascadeUpdates } from './streaming-api';

export class AntigravityStreamingTransport implements IIDEStreamingTransport {
    constructor(private readonly httpCore: AntigravityHttpCore) {}

    stream(sessionId: string, subscriberId: string, callbacks: IIDEStreamCallbacks): http.ClientRequest {
        return streamCascadeUpdates(
            this.httpCore.getPort(),
            this.httpCore.getUseHttps(),
            this.httpCore.getCsrfToken(),
            sessionId,
            subscriberId,
            callbacks,
            this.httpCore.getAgent()
        );
    }
}
