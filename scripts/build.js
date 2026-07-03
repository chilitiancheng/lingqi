import { cp, mkdir, rm } from "node:fs/promises";
import { join } from "node:path";

const root = process.cwd();
const out = join(root, "dist");

await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
await cp(join(root, "index.html"), join(out, "index.html"));
await cp(join(root, "src"), join(out, "src"), { recursive: true });
