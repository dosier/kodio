# MP3 test vectors

## ISO / minimp3

Layer III bitstreams and reference PCM files (`l3-*.bit` / `l3-*.pcm`) are taken from
[lieff/minimp3](https://github.com/lieff/minimp3) (`vectors/`).

Hard ISO/IEC 11172-4 style conformance is gated at PSNR > 96 dB for the MPEG-1
Layer III set (`l3-compl`, `l3-he_*`, `l3-si*`, `l3-sin1k0db`). Nonstandard ID3 /
VBR-tag vectors are smoke-tested for decode only.

## LAME fixtures

`lame-*.mp3` files were generated with LAME 3.100. Matching `lame-*.pcm` files
were decoded with ffmpeg for optional accuracy comparison.
