package space.kodio.core

/**
 * TODO: figure out how to package the audio processor code as a file,
 *       for some reason it's not included when the project is depended upon by another project.
 */
internal const val AUDIO_PROCESSOR_CODE = """
/**
 * A custom AudioWorkletProcessor to capture raw PCM data.
 * This processor receives audio from a source node, downmixes it to mono,
 * and posts the Float32Array data back to the .main thread via its port.
 */
class PcmRecorderProcessor extends AudioWorkletProcessor {

    constructor() {
        super();
        this.batchSize = 2048;
        this.batch = new Float32Array(this.batchSize);
        this.offset = 0;
        this.port.onmessage = (e) => {
            if (e.data === 'flush') {
                this._flush();
            }
        };
    }

    _flush() {
        if (this.offset > 0) {
            const partial = this.batch.slice(0, this.offset);
            this.port.postMessage(partial, [partial.buffer]);
            this.batch = new Float32Array(this.batchSize);
            this.offset = 0;
        }
    }

    /**
     * The process method is called for every block of audio data.
     * @param {Float32Array[][]} inputs - An array of inputs, each with an array of channels.
     * @param {Float32Array[][]} outputs - An array of outputs (we don't use this).
     * @param {Record<string, Float32Array>} parameters - Audio parameters (we don't use this).
     * @returns {boolean} - Must return true to keep the processor alive.
     */
    process(inputs, outputs, parameters) {
        const input = inputs[0];

        if (!input || input.length === 0) {
            return true;
        }

        const channelCount = input.length;
        const sampleCount = input[0].length;

        for (let i = 0; i < sampleCount; i++) {
            let sample;
            if (channelCount > 1) {
                sample = (input[0][i] + input[1][i]) * 0.5;
            } else {
                sample = input[0][i];
            }
            this.batch[this.offset++] = sample;
            if (this.offset === this.batchSize) {
                this.port.postMessage(this.batch, [this.batch.buffer]);
                this.batch = new Float32Array(this.batchSize);
                this.offset = 0;
            }
        }

        return true;
    }
}

registerProcessor('pcm-recorder-processor', PcmRecorderProcessor);
"""
