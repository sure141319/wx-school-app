# HEIC/HEIF test fixtures

The binary fixtures in this directory come from the official
[`strukturag/libheif`](https://github.com/strukturag/libheif) repository,
tag `v1.23.1`, under its LGPL-3.0 license:

- `heic-32.heic`: `fuzzing/data/corpus/colors-no-alpha.heic`
  (`SHA-256 76F82FFC717A647B1C9C2551E5EA0545832A2D3216C7540F7E5B092282A04B63`)
- `heif-32.heif`: `fuzzing/data/corpus/hevc32.heif`
  (`SHA-256 7076E79A3A6F7ED9C09089FBE3A7608EB6C044FA34DE900F3E3467704F36BCB7`)

They are used only by the decoder integration test.
