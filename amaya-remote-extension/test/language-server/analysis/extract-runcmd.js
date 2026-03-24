const fs = require('fs');
const path = require('path');

// Read test-trajectory-utf8.json which we know has RUN_COMMAND
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
    console.log('Type:', step.type);
    
    if (step.runCommand) {
        console.log('\nrunCommand keys:', Object.keys(step.runCommand).join(', '));
        const rc = step.runCommand;
        
        // Check all possible output fields
        const outputFields = ['output', 'stdout', 'stderr', 'result', 'summary', 'message', 'text', 'commandOutput', 'executionOutput', 'exitCode'];
        console.log('\nOutput fields:');
        for (const field of outputFields) {
            if (rc[field] !== undefined) {
                const val = typeof rc[field] === 'string' ? rc[field] : JSON.stringify(rc[field]);
                console.log(`  ${field}: ${val.substring(0, 200)}${val.length > 200 ? '...' : ''}`);
            }
        }
        
        // Show full runCommand object for first result
        if (i === 0) {
            console.log('\nFull runCommand object:');
            console.log(JSON.stringify(rc, null, 2));
        }
    }
    console.log('\n' + '='.repeat(60) + '\n');
});
