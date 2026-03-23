import * as http from 'http';

export interface IIDEStreamCallbacks {
    onData?: (data: any) => void;
    onError?: (error: string) => void;
    onEnd?: () => void;
}

export interface IIDEStreamingTransport {
    stream(sessionId: string, subscriberId: string, callbacks: IIDEStreamCallbacks): http.ClientRequest;
}
