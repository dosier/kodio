package gg.kodio.core

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

    /**
     * The process method is called for every block of audio data.
     * @param {Float32Array[][]} inputs - An array of inputs, each with an array of channels.
     * @param {Float32Array[][]} outputs - An array of outputs (we don't use this).
     * @param {Record<string, Float32Array>} parameters - Audio parameters (we don't use this).
     * @returns {boolean} - Must return true to keep the processor alive.
     */
    process(inputs, outputs, parameters) {
        // We expect only one input connected to this node.
        const input = inputs[0];

        // If there's no input or the input is empty, do nothing.
        if (!input || input.length === 0) {
            return true;
        }

        // Downmix to mono by averaging channels if necessary.
        // `input` is an array of channels, where each channel is a Float32Array.
        const channelCount = input.length;
        const sampleCount = input[0].length;
        let monoPcmData = input[0]; // Default to left channel if mono

        if (channelCount > 1) {
            // If stereo or more, average the first two channels (Left & Right).
            const rightChannel = input[1];
            monoPcmData = new Float32Array(sampleCount);
            for (let i = 0; i < sampleCount; i++) {
                monoPcmData[i] = (input[0][i] + rightChannel[i]) * 0.5;
            }
        }

        // Post the raw PCM data (as a Float32Array) back to the .main thread.
        // We send a transferable object for performance.
        this.port.postMessage(monoPcmData, [monoPcmData.buffer]);

        return true; // Keep the processor running
    }
}

// Register the processor with a name that we will use in our Kotlin code.
registerProcessor('pcm-recorder-processor', PcmRecorderProcessor);
"""