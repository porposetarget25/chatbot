package com.example.genai.service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class AudioMixService {

    // Background music volume relative to speech
    private static final double BG_GAIN = 0.500; // 30%

    public Path mixSpeechWithBackground(Path speechPath, Path bgPath, Path outputPath) throws Exception {
        // Open original audio streams (MP3/MPEG)
        try (AudioInputStream speechIn = AudioSystem.getAudioInputStream(speechPath.toFile());
             AudioInputStream bgIn = AudioSystem.getAudioInputStream(bgPath.toFile())) {

            AudioFormat speechFormat = speechIn.getFormat();

            float sampleRate = speechFormat.getSampleRate();
            if (sampleRate == AudioSystem.NOT_SPECIFIED) {
                sampleRate = 44100f; // fallback
            }

            // Force both to 16-bit, stereo, little-endian PCM
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,        // bits per sample
                    2,         // channels: force stereo
                    4,         // frame size = 2 bytes * 2 channels
                    sampleRate,
                    false      // little endian
            );

            try (AudioInputStream speechPcm = AudioSystem.getAudioInputStream(targetFormat, speechIn);
                 AudioInputStream bgPcm = AudioSystem.getAudioInputStream(targetFormat, bgIn)) {

                byte[] mixedBytes = mixPcmStreams(speechPcm, bgPcm, targetFormat);

                //If directory exists
                if (outputPath.getParent() != null) {
                    Files.createDirectories(outputPath.getParent());
                }

                try (ByteArrayInputStream bais = new ByteArrayInputStream(mixedBytes);
                     AudioInputStream mixedStream = new AudioInputStream(
                             bais,
                             targetFormat,
                             mixedBytes.length / targetFormat.getFrameSize())) {

                    AudioSystem.write(mixedStream, AudioFileFormat.Type.WAVE, outputPath.toFile());
                }
            }
        }

        return outputPath;
    }

    /**
     * Mix two PCM_SIGNED 16-bit little-endian stereo streams.
     * Duration = speech stream length (bg is truncated or padded as silence).
     */
    private byte[] mixPcmStreams(AudioInputStream speechPcm,
                                 AudioInputStream bgPcm,
                                 AudioFormat format) throws IOException {

        int frameSize = format.getFrameSize(); // should be 4 (stereo 16-bit)
        int bufferFrames = 1024;
        int bufferSize = frameSize * bufferFrames;

        byte[] speechBuf = new byte[bufferSize];
        byte[] bgBuf = new byte[bufferSize];
        byte[] outBuf = new byte[bufferSize];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int speechBytesRead;
        while ((speechBytesRead = speechPcm.read(speechBuf)) != -1) {
            int bgBytesRead = bgPcm.read(bgBuf);

            if (bgBytesRead == -1) {
                // BG finished: treat as silence for the remaining speech part
                Arrays.fill(bgBuf, 0, speechBytesRead, (byte) 0);
                bgBytesRead = speechBytesRead;
            }

            // iterate per 16-bit sample (2 bytes) – this naturally covers both channels
            for (int i = 0; i < speechBytesRead; i += 2) {
                short speechSample = bytesToShortLE(speechBuf, i);
                short bgSample = (i < bgBytesRead) ? bytesToShortLE(bgBuf, i) : 0;

                int mixed = (int) speechSample + (int) (bgSample * BG_GAIN);

                // clamp to 16-bit range
                if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
                if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;

                shortToBytesLE((short) mixed, outBuf, i);
            }

            baos.write(outBuf, 0, speechBytesRead);
        }

        // We stop when speech ends → background beyond that is ignored
        return baos.toByteArray();
    }

    private short bytesToShortLE(byte[] buffer, int offset) {
        int low = buffer[offset] & 0xFF;
        int high = buffer[offset + 1]; // signed
        return (short) ((high << 8) | low);
    }

    private void shortToBytesLE(short value, byte[] buffer, int offset) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
