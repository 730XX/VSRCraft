package com.vsrcraft.client.parser;

import com.vsrcraft.client.domain.ReplayData;
import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de archivos .osr (osu! replay) para osu!mania (mode = 3).
 * Lee la cabecera binaria, descomprime el stream LZMA y convierte
 * la máscara de bits de cada frame en eventos (timeMs, lane, pressed).
 */
public final class OsrReplayParser {

    private static final int OSU_MODE_MANIA = 3;
    private static final int MOD_MIRROR = 1 << 30;

    private OsrReplayParser() {}

    public record OsrHeader(int gameMode, String beatmapMd5, String playerName, int score, int maxCombo, int mods) {}

    public static OsrHeader readHeader(Path osrPath) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(osrPath))) {
            BinaryReader reader = new BinaryReader(in);
            return readHeaderInternal(reader);
        }
    }

    public static ReplayData parse(Path osrPath, int keyCount, String beatmapId) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(osrPath))) {
            BinaryReader reader = new BinaryReader(in);
            OsrHeader header = readHeaderInternal(reader);

            if (header.gameMode() != OSU_MODE_MANIA) {
                throw new IOException("El replay no es de osu!mania (modo=" + header.gameMode() + ").");
            }

            reader.readOsuString(); // lifeBarGraph
            reader.readLong();      // Windows ticks timestamp
            int compressedLength = reader.readInt();
            if (compressedLength <= 0 || compressedLength > 32 * 1024 * 1024) {
                throw new IOException("Longitud de stream LZMA inválida en .osr: " + compressedLength);
            }

            byte[] compressed = reader.readBytes(compressedLength);
            String rawFrames = decompressLzma(compressed);
            List<ReplayData.ReplayEvent> events = parseManiaFrames(rawFrames, keyCount, (header.mods() & MOD_MIRROR) != 0);

            ReplayData data = new ReplayData(
                    beatmapId != null ? beatmapId : "",
                    header.score(),
                    100.0f,
                    header.maxCombo(),
                    "OSR",
                    events
            );
            data.playerName = header.playerName();
            data.osuBeatmapMd5 = header.beatmapMd5();
            data.mods = header.mods();
            return data;
        }
    }

    private static OsrHeader readHeaderInternal(BinaryReader reader) throws IOException {
        int gameMode = reader.readUnsignedByte();
        reader.readInt(); // gameVersion
        String beatmapMd5 = reader.readOsuString();
        String playerName = reader.readOsuString();
        reader.readOsuString(); // replayMd5

        reader.readShort(); // n300
        reader.readShort(); // n100
        reader.readShort(); // n50
        reader.readShort(); // nGeki
        reader.readShort(); // nKatu
        reader.readShort(); // nMiss

        int score = reader.readInt();
        int maxCombo = reader.readUnsignedShort();
        reader.readUnsignedByte(); // perfect
        int mods = reader.readInt();

        return new OsrHeader(gameMode, beatmapMd5, playerName, score, maxCombo, mods);
    }

    private static String decompressLzma(byte[] compressed) throws IOException {
        try (LZMAInputStream lzma = new LZMAInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = lzma.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static List<ReplayData.ReplayEvent> parseManiaFrames(String rawFrames, int keyCount, boolean mirror) {
        List<ReplayData.ReplayEvent> events = new ArrayList<>();
        if (rawFrames == null || rawFrames.isEmpty() || keyCount <= 0) {
            return events;
        }

        String[] frames = rawFrames.split(",");
        long currentTimeMs = 0L;
        int prevMask = 0;

        for (String frame : frames) {
            if (frame.isEmpty()) continue;
            String[] parts = frame.split("\\|");
            if (parts.length < 2) continue;

            long deltaMs;
            int mask;
            try {
                deltaMs = Long.parseLong(parts[0].trim());
                if (deltaMs == -12345L) {
                    continue; // Frame final de seed RNG en osu!
                }
                mask = (int) Math.round(Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                continue;
            }

            currentTimeMs += deltaMs;

            for (int bit = 0; bit < keyCount; bit++) {
                boolean wasPressed = (prevMask & (1 << bit)) != 0;
                boolean isPressed = (mask & (1 << bit)) != 0;
                if (wasPressed != isPressed) {
                    int lane = mirror ? (keyCount - 1 - bit) : bit;
                    events.add(new ReplayData.ReplayEvent(currentTimeMs, lane, isPressed));
                }
            }
            prevMask = mask;
        }

        return events;
    }

    private static final class BinaryReader {
        private final InputStream in;

        private BinaryReader(InputStream in) {
            this.in = in;
        }

        private int readUnsignedByte() throws IOException {
            int b = in.read();
            if (b < 0) throw new EOFException("Fin inesperado leyendo .osr");
            return b;
        }

        private byte[] readBytes(int count) throws IOException {
            byte[] buf = new byte[count];
            int offset = 0;
            while (offset < count) {
                int read = in.read(buf, offset, count - offset);
                if (read < 0) throw new EOFException("Fin inesperado leyendo .osr");
                offset += read;
            }
            return buf;
        }

        private short readShort() throws IOException {
            return ByteBuffer.wrap(readBytes(2)).order(ByteOrder.LITTLE_ENDIAN).getShort();
        }

        private int readUnsignedShort() throws IOException {
            return readShort() & 0xFFFF;
        }

        private int readInt() throws IOException {
            return ByteBuffer.wrap(readBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        private long readLong() throws IOException {
            return ByteBuffer.wrap(readBytes(8)).order(ByteOrder.LITTLE_ENDIAN).getLong();
        }

        private int readUleb128() throws IOException {
            int result = 0;
            int shift = 0;
            while (true) {
                int b = readUnsignedByte();
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
        }

        private String readOsuString() throws IOException {
            int marker = readUnsignedByte();
            if (marker == 0x00) {
                return "";
            }
            if (marker != 0x0B) {
                throw new IOException("Marcador de string inválido en .osr: 0x" + Integer.toHexString(marker));
            }
            int length = readUleb128();
            if (length <= 0) {
                return "";
            }
            return new String(readBytes(length), StandardCharsets.UTF_8);
        }
    }
}
