package com.vsrcraft.client.audio;

import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;

/**
 * Time-stretcher WSOLA (Waveform Similarity Overlap-Add) en Java puro para PCM de 16 bits.
 * Cambia la velocidad de reproducción (0.5x - 2.0x) preservando el tono (pitch) original.
 */
public final class AudioTimeStretcher {

    private AudioTimeStretcher() {}

    public record StretchResult(ShortBuffer pcm, int totalSamples) {}

    public static StretchResult stretch(ShortBuffer inputPcm, int channels, int sampleRate, float speed) {
        if (inputPcm == null || channels <= 0 || sampleRate <= 0 || Math.abs(speed - 1.0f) < 0.001f) {
            return null;
        }

        int ch = Math.min(2, channels);
        int inputTotalFrames = inputPcm.limit() / channels;
        if (inputTotalFrames <= 0) {
            return null;
        }

        // Ventana de ~24 ms con 50% de solapamiento (Hann) y radio de búsqueda de ~8 ms
        int halfWin = Math.max(128, (sampleRate * 12) / 1000);
        int winSize = halfWin * 2;
        int hopOut = halfWin;
        int searchRadius = Math.max(32, (sampleRate * 8) / 1000);

        if (inputTotalFrames < winSize * 2) {
            return null;
        }

        // Extraer a arreglo corto para acceso rápido sin bounds checks repetidos
        short[] in = new short[inputTotalFrames * ch];
        for (int i = 0; i < inputTotalFrames; i++) {
            int srcBase = i * channels;
            int dstBase = i * ch;
            in[dstBase] = inputPcm.get(srcBase);
            if (ch == 2) {
                in[dstBase + 1] = inputPcm.get(srcBase + 1);
            }
        }

        float[] hann = new float[winSize];
        for (int i = 0; i < winSize; i++) {
            hann[i] = 0.5f * (1.0f - (float) Math.cos((2.0 * Math.PI * i) / winSize));
        }

        int outTotalFrames = Math.max(winSize, (int) Math.ceil(inputTotalFrames / (double) speed));
        float[] outAccum = new float[(outTotalFrames + winSize) * ch];

        int prevInPos = 0;
        // Copiar primer frame directamente con ventana Hann
        accumulateWindow(in, 0, ch, winSize, hann, outAccum, 0);

        int outPos = hopOut;
        while (outPos + winSize <= outTotalFrames + halfWin) {
            int nominalInPos = (int) Math.round(outPos * (double) speed);
            if (nominalInPos + winSize >= inputTotalFrames) {
                break;
            }

            int naturalCont = prevInPos + hopOut;
            int minSearch = Math.max(0, nominalInPos - searchRadius);
            int maxSearch = Math.min(inputTotalFrames - winSize, nominalInPos + searchRadius);

            int bestInPos = Math.max(minSearch, Math.min(maxSearch, nominalInPos));
            if (naturalCont + halfWin <= inputTotalFrames && maxSearch >= minSearch) {
                bestInPos = findBestOverlap(in, ch, naturalCont, minSearch, maxSearch, halfWin);
            }

            accumulateWindow(in, bestInPos, ch, winSize, hann, outAccum, outPos);
            prevInPos = bestInPos;
            outPos += hopOut;
        }

        int finalFrames = Math.min(outTotalFrames, outPos + halfWin);
        ShortBuffer resultBuf = MemoryUtil.memAllocShort(finalFrames * ch);
        for (int i = 0; i < finalFrames * ch; i++) {
            int sample = Math.round(outAccum[i]);
            if (sample > 32767) sample = 32767;
            else if (sample < -32768) sample = -32768;
            resultBuf.put(i, (short) sample);
        }
        resultBuf.limit(finalFrames * ch);
        resultBuf.position(0);

        return new StretchResult(resultBuf, finalFrames);
    }

    private static void accumulateWindow(short[] in, int inFrame, int ch, int winSize, float[] hann, float[] outAccum, int outFrame) {
        if (ch == 1) {
            for (int i = 0; i < winSize; i++) {
                outAccum[outFrame + i] += in[inFrame + i] * hann[i];
            }
        } else {
            for (int i = 0; i < winSize; i++) {
                float w = hann[i];
                int inIdx = (inFrame + i) * 2;
                int outIdx = (outFrame + i) * 2;
                outAccum[outIdx] += in[inIdx] * w;
                outAccum[outIdx + 1] += in[inIdx + 1] * w;
            }
        }
    }

    private static int findBestOverlap(short[] in, int ch, int refFrame, int minSearch, int maxSearch, int compareLen) {
        int bestPos = minSearch;
        double bestScore = Double.NEGATIVE_INFINITY;
        // Stride de 2 muestras para acelerar la búsqueda manteniendo precisión de fase
        final int step = 2;

        for (int cand = minSearch; cand <= maxSearch; cand++) {
            double dot = 0.0;
            double energyCand = 1.0;
            if (ch == 1) {
                for (int i = 0; i < compareLen; i += step) {
                    double a = in[refFrame + i];
                    double b = in[cand + i];
                    dot += a * b;
                    energyCand += b * b;
                }
            } else {
                for (int i = 0; i < compareLen; i += step) {
                    int refIdx = (refFrame + i) * 2;
                    int candIdx = (cand + i) * 2;
                    double a = in[refIdx] + in[refIdx + 1];
                    double b = in[candIdx] + in[candIdx + 1];
                    dot += a * b;
                    energyCand += b * b;
                }
            }
            double score = dot / Math.sqrt(energyCand);
            if (score > bestScore) {
                bestScore = score;
                bestPos = cand;
            }
        }
        return bestPos;
    }
}
