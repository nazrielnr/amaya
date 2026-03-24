const https = require('https');
const path = require('path');

require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env.local') });

const CONFIG = {
    host: '127.0.0.1',
    port: 53125,
    csrfToken: process.env.ANTIGRAVITY_CSRF_TOKEN || '',
    apiKey: process.env.ANTIGRAVITY_API_KEY || '',
};

if (!CONFIG.csrfToken || !CONFIG.apiKey) {
    console.error('ERROR: Missing ANTIGRAVITY_CSRF_TOKEN or ANTIGRAVITY_API_KEY');
    console.error('Copy .env.local.example to .env.local and fill in your credentials');
    process.exit(1);
}

const metadata = {
    ideName: 'antigravity',
    apiKey: CONFIG.apiKey,
    locale: 'en',
    ideVersion: '1.19.6',
    extensionName: 'antigravity'
};

function callEndpoint(methodName, body) {
    return new Promise((resolve) => {
        const bodyStr = JSON.stringify(body);
        const options = {
            hostname: CONFIG.host,
            port: CONFIG.port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': CONFIG.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        };
        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', (e) => resolve({ error: e.message, status: 0 }));
        req.write(bodyStr);
        req.end();
    });
}

async function testModelVariations() {
    // enum number for PLACEHOLDER_M18 is 1018
    // from generator-metadata, the real request used string "MODEL_PLACEHOLDER_M18" for planModel
    // and { model: "MODEL_PLACEHOLDER_M18" } for requestedModel
    const cascadeId = 'ed4dbaa8-c664-4029-9f1c-f1c4ade8f289';

    const variations = [
        {
            name: '1) Numeric 1018 in plannerConfig.planModel',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascadeConfig: {
                    plannerConfig: {
                        plannerTypeConfig: {
                            case: 'conversational',
                            value: {}
                        },
                        planModel: 1018,
                        requestedModel: { model: 1018 }
                    }
                }
            }
        },
        {
            name: '2) String in plannerConfig.planModel',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascadeConfig: {
                    plannerConfig: {
                        plannerTypeConfig: {
                            case: 'conversational',
                            value: {}
                        },
                        planModel: 'MODEL_PLACEHOLDER_M18',
                        requestedModel: { model: 'MODEL_PLACEHOLDER_M18' }
                    }
                }
            }
        },
        {
            name: '3) Numeric 1018 direct in cascadeConfig',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascadeConfig: {
                    planModel: 1018,
                    requestedModel: { model: 1018 },
                    modelName: 'gemini-3-flash'
                }
            }
        },
        {
            name: '4) Full plannerConfig with numeric model',
            body: {
                metadata, cascadeId,
                items: [{ text: 'test' }],
                cascadeConfig: {
                    plannerConfig: {
                        planModel: 1018,
                        requestedModel: { model: 1018 }
                    },
                    planModel: 1018,
                    requestedModel: { model: 1018 }
                }
            }
        },
        {
            name: '5) Using gemini-3-flash as string alias',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascadeConfig: {
                    planModel: 'gemini-3-flash',
                    requestedModel: { alias: 'gemini-3-flash' }
                }
            }
        }
    ];

    for (const v of variations) {
        console.log(`\nTesting: ${v.name}`);
        const res = await callEndpoint('SendUserCascadeMessage', v.body);
        if (res.error) {
            console.log(`Error: ${res.error}`);
        } else {
            console.log(`Status: ${res.status}, Body: ${res.data.substring(0, 200)}`);
            if (res.status === 200) {
                console.log('\n✅ SUCCESS! This variation works!');
                break;
            }
        }
    }
}

testModelVariations().catch(console.error);
