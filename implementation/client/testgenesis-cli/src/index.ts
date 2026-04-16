import {parseArgs} from "node:util";
import * as fs from "node:fs";
import * as path from "node:path";
import {fileURLToPath} from "node:url";
import {execSync} from "node:child_process";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TEMPLATE_DIR = path.join(__dirname, "..", "templates");

async function main() {
    const {values, positionals} = parseArgs({
        options: {
            name: {type: "string", short: "n"},
            help: {type: "boolean", short: "h"},
        },
        allowPositionals: true
    });

    if (values.help || (positionals.length === 0 && !values.name)) {
        console.log("Usage: testgenesis-gen <project-name>");
        console.log("Options:");
        console.log("  -n, --name <name>  Name of the project");
        console.log("  -h, --help         Show this help");
        return;
    }

    const projectName = values.name || positionals[0];
    const targetDir = path.resolve(process.cwd(), projectName);

    console.log(`[Gen] Scaffolding project in: ${targetDir}`);

    if (fs.existsSync(targetDir)) {
        console.error(`[Error] Directory already exists: ${targetDir}`);
        process.exit(1);
    }

    fs.mkdirSync(targetDir, {recursive: true});
    fs.mkdirSync(path.join(targetDir, "src"), {recursive: true});

    const files = [
        {template: "template-package.json", target: "package.json"},
        {template: "template-tsconfig.json", target: "tsconfig.json"},
        {template: "template-gitignore", target: ".gitignore"},
        {template: "template-index.ts", target: "src/index.ts"},
        {template: "template-processor.ts", target: "src/processor.ts"},
    ];

    for (const file of files) {
        console.log(`[Gen] Creating ${file.target}...`);
        const templatePath = path.join(TEMPLATE_DIR, file.template);
        let content = fs.readFileSync(templatePath, "utf-8");
        content = content.replace(/__name__/g, projectName);
        fs.writeFileSync(path.join(targetDir, file.target), content);
    }

    console.log(`[Gen] Installing dependencies...`);
    try {
        execSync("npm install", {cwd: targetDir, stdio: "inherit"});
        console.log(`\n[Gen] Success! Created ${projectName} at ${targetDir}`);
        console.log(`\nTo get started:`);
        console.log(`  cd ${projectName}`);
        console.log(`  npm run start`);
    } catch (err) {
        console.error(`[Warning] Failed to install dependencies. Please run 'npm install' manually.`);
    }
}

main().catch(console.error);
