import {parseArgs} from "node:util";
import * as fs from "node:fs";
import * as path from "node:path";
import {fileURLToPath} from "node:url";
import {execSync} from "node:child_process";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TEMPLATE_DIR = path.join(__dirname, "..", "templates");

interface FileTask {
    template: string;
    target: string;
}

interface LangConfig {
    dirs: string[];
    files: FileTask[];
    installCmd?: string;
    startCmd: string;
}

const CONFIGS: Record<string, LangConfig> = {
    ts: {
        dirs: ["src"],
        files: [
            {template: "template-package.json", target: "package.json"},
            {template: "template-tsconfig.json", target: "tsconfig.json"},
            {template: "template-gitignore", target: ".gitignore"},
            {template: "template-index.ts", target: "src/index.ts"},
            {template: "template-processor.ts", target: "src/processor.ts"},
        ],
        installCmd: "npm install",
        startCmd: "npm run start"
    },
    java: {
        dirs: ["src/main/java/me/hsgamer/testgenesis/agent"],
        files: [
            {template: "template-java-pom.xml", target: "pom.xml"},
            {template: "template-java-gitignore", target: ".gitignore"},
            {template: "template-java-app.java", target: "src/main/java/me/hsgamer/testgenesis/agent/AgentApp.java"},
            {template: "template-java-processor.java", target: "src/main/java/me/hsgamer/testgenesis/agent/ExampleProcessor.java"},
        ],
        startCmd: "mvn exec:java"
    }
};

async function main() {
    const {values, positionals} = parseArgs({
        options: {
            name: {type: "string", short: "n"},
            lang: {type: "string", short: "l"},
            help: {type: "boolean", short: "h"},
        },
        allowPositionals: true
    });

    if (values.help || (positionals.length === 0 && !values.name)) {
        console.log("Usage: testgenesis-gen <project-name>");
        console.log("Options:");
        console.log("  -n, --name <name>  Name of the project");
        console.log("  -l, --lang <lang>  Language (ts, java) [default: ts]");
        console.log("  -h, --help         Show this help");
        return;
    }

    const projectName = values.name || positionals[0];
    const lang = (values.lang || "ts").toLowerCase();
    const config = CONFIGS[lang];

    if (!config) {
        console.error(`[Error] Unsupported language: ${lang}. Supported: ${Object.keys(CONFIGS).join(", ")}`);
        process.exit(1);
    }

    const targetDir = path.resolve(process.cwd(), projectName);

    console.log(`[Gen] Scaffolding ${lang} project in: ${targetDir}`);

    if (fs.existsSync(targetDir)) {
        console.error(`[Error] Directory already exists: ${targetDir}`);
        process.exit(1);
    }

    // 1. Create Directories
    fs.mkdirSync(targetDir, {recursive: true});
    for (const dir of config.dirs) {
        fs.mkdirSync(path.join(targetDir, dir), {recursive: true});
    }

    // 2. Write Files
    for (const file of config.files) {
        console.log(`[Gen] Creating ${file.target}...`);
        const templatePath = path.join(TEMPLATE_DIR, file.template);
        if (!fs.existsSync(templatePath)) {
            console.warn(`[Warning] Template not found: ${file.template}`);
            continue;
        }
        
        let content = fs.readFileSync(templatePath, "utf-8");
        content = content.replace(/__name__/g, projectName);
        
        const targetPath = path.join(targetDir, file.target);
        fs.mkdirSync(path.dirname(targetPath), {recursive: true});
        fs.writeFileSync(targetPath, content);
    }

    // 3. Install Dependencies
    if (config.installCmd) {
        console.log(`[Gen] Installing dependencies...`);
        try {
            execSync(config.installCmd, {cwd: targetDir, stdio: "inherit"});
        } catch (err) {
            console.error(`[Warning] Failed to install dependencies. Please run '${config.installCmd}' manually.`);
        }
    }

    console.log(`\n[Gen] Success! Created ${projectName} at ${targetDir}`);
    console.log(`\nTo get started:`);
    console.log(`  cd ${projectName}`);
    console.log(`  ${config.startCmd}`);
}

main().catch(console.error);
