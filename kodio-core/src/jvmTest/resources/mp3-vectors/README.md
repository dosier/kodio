# MP3 test vectors

## ISO / minimp3

Layer III bitstreams and reference PCM files (`l3-*.bit` / `l3-*.pcm`) are taken from
[lieff/minimp3](https://github.com/lieff/minimp3) (`vectors/`).

They are used for smoke decode coverage. Full ISO/IEC 11172-4 PSNR > 96 dB
conformance is not gated yet for the current JavaMP3-based decoder port.

## LAME fixtures

`lame-*.mp3` files were generated with LAME 3.100. Matching `lame-*.pcm` files
were decoded with ffmpeg for optional accuracy comparison.
