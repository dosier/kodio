// The dev server serves the repo root and the module dir as static folders so the
// browser can load sources referenced by source maps. Watching them (build output,
// .gradle, node_modules, the whole repo) exhausts file handles on macOS and kills
// the server with "EMFILE: too many open files, watch". Keep serving, stop watching:
// compiled output is served from webpack memory and still hot-reloads.
if (config.devServer && config.devServer.static) {
    const entries = Array.isArray(config.devServer.static)
        ? config.devServer.static
        : [config.devServer.static];
    config.devServer.static = entries.map((entry) =>
        typeof entry === "string"
            ? { directory: entry, watch: false }
            : Object.assign({}, entry, { watch: false })
    );
}
