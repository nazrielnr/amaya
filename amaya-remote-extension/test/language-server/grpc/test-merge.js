const fs = require('fs');
const { antigravityApi } = require('./out/antigravity-api.js');

async function analyze() {
    const dbPath = 'c:\\Users\\BiuBiu\\AppData\\Roaming\\Code\\User\\globalStorage\\state.vscdb';
    const sqlite3 = require('sqlite3').verbose();
    const db = new sqlite3.Database(dbPath);

    const getRow = (query, params) => new Promise((resolve, reject) => {
        db.get(query, params, (err, row) => err ? reject(err) : resolve(row));
    });

    const row = await getRow("SELECT value FROM ItemTable WHERE key = 'codeium.codeiumAccountDetails'");
    if (!row) throw new Error("No token");
    const accountDetails = JSON.parse(row.value);
    const token = accountDetails.metadata.apiKey;
    const csrfStr = fs.readFileSync('c:\\tmp\\csrf.txt', 'utf8').trim();

    antigravityApi.updateTokens(token, csrfStr);

    console.log("Fetching trajectories...");
    const trajs = await antigravityApi.client.getUserTrajectoryDescriptions({});
    if (!trajs || !trajs.descriptions || trajs.descriptions.length === 0) return;

    const target = trajs.descriptions[0].id;
    const stepsResponse = await antigravityApi.client.getCascadeTrajectory({ id: target });
    if (!stepsResponse || !stepsResponse.messages) return;

    let allSteps = [];
    for (const msg of stepsResponse.messages) {
        if (msg.cortexSteps) {
            allSteps = allSteps.concat(msg.cortexSteps);
        }
    }

    console.log("----- Running stepsToMessages -----");
    const msgs = antigravityApi.stepsToMessages(allSteps);
    let i = 0;
    for (const m of msgs) {
        i++;
        console.log(`\n--- Message ${i} (${m.role}) ---`);
        if (m.thinking) console.log(`Thinking (${m.thinking.length} chars)`);
        if (m.toolCalls && m.toolCalls.length > 0) {
            console.log(`Tools: ${m.toolCalls.map(t => t.name).join(', ')}`);
        }
        if (m.content) console.log(`Content (${m.content.length} chars): "${m.content.substring(0, 80).replace(/\n/g, '\\n')}..."`);
    }
}

analyze().then(() => process.exit(0)).catch(e => { console.error(e); process.exit(1); });
