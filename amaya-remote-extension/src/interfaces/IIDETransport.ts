export interface IIDETransport {
    callEndpoint(methodName: string, payload: any): Promise<any>;
}
