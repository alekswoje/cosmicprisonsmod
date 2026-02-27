# Launcher Release Helpers

## Generate `cosmic-launcher-manifest.json`

```bash
node scripts/launcher/generate-manifest.mjs \
  --release-dir=/absolute/path/to/release-assets \
  --mod-version=1.0.0 \
  --launcher-version=1.0.0 \
  --minecraft-version=1.21.11 \
  --instance-id=cosmicprisons-managed-1_21_11
```

Required release assets in `--release-dir`:
- `CosmicPrisonsMod-<mod-version>.jar`
- `cosmicprisons-prism-pack-<minecraft-version>.zip`
- `cosmic-launcher-macos-universal.zip`
- `cosmic-launcher-win-x64.zip`
- `cosmic-launcher-win-arm64.zip`

## Prism pack template

Use `scripts/launcher/prism-pack-template/` as the base directory for your managed Prism instance pack zip.
The resulting release asset name should match:
- `cosmicprisons-prism-pack-1.21.11.zip`
