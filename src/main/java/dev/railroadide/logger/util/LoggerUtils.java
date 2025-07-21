package dev.railroadide.logger.util;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class LoggerUtils {
    public static void compress(Path path) {
        Path outputPath = path.resolveSibling(path.getFileName().toString() + ".tar.gz");
        try (var inputChannel = FileChannel.open(path, StandardOpenOption.READ);
             var outChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             InputStream inputStream = Channels.newInputStream(inputChannel);
             OutputStream outputStream = Channels.newOutputStream(outChannel);
             var bufferedOutputStream = new BufferedOutputStream(outputStream);
             var gzipOutputStream = new GzipCompressorOutputStream(bufferedOutputStream)) {
            var buf = new byte[8 * 1024];
            int length;
            while ((length = inputStream.read(buf)) != -1) {
                gzipOutputStream.write(buf, 0, length);
            }
        } catch (IOException exception) {
            System.out.println();
        }
    }
}
