const fs = require('fs');
const path = require('path');

// Read test-trajectory-utf8.json
const filePath = path.join(__dirname, 'test-trajectory-utf8.json');
const data = fs.readFileSync(filePath, 'utf8');

// Find all RUN_COMMAND steps
const results = [];
let idx = 0;

while ((idx = data.indexOf('CORTEX_STEP_TYPE_RUN_COMMAND', idx)) !== -1) {
    // Find the start of this object
    let start = data.lastIndexOf('{', idx);
    let depth = 0;
    let end = start;
    
    // Find the end of this object
    for (let i = start; i < data.length; i++) {
        if (data[i] === '{') depth++;
        if (data[i] === '}') {
            depth--;
            if (depth === 0) {
                end = i + 1;
                break;
            }
        }
    }
    
    try {
        const obj = JSON.parse(data.substring(start, end));
        results.push(obj);
    } catch (e) {
        console.log('Parse error at index', idx);
    }
    
    idx = end;
}

console.log(`Found ${results.length} RUN_COMMAND steps:\n`);

results.forEach((step, i) => {
    console.log(`--- RUN_COMMAND #${i+1} ---`);
    console.log('Status:', step.status);
    
    if (step.runCommand) {
        const rc = step.runCommand;
        
        // Check for combinedOutput
        if (rc.combinedOutput) {
            console.log('combinedOutput.full:', rc.combinedOutput.full);
        }
        
        // Check for rawDebugOutput
        if (rc.rawDebugOutput) {
            console.log('rawDebugOutput (len ' + rc.rawDebugOutput.length + '):', rc.rawDebugOutput.substring(0, 200));
        }
        
        // Check other fields
        ['output', 'stdout', 'stderr', 'result', 'summary', 'message', 'text'].forEach(field => {
            if (rc[field]) {
                console.log(`${field}:`, rc[field]);
            }
        });
    }
    console.log('\n' + '='.repeat(60) + '\n');
});
