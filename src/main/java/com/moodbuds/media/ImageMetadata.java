package com.moodbuds.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.moodbuds.common.ApiException;

final class ImageMetadata {
    private ImageMetadata() {}

    static Detected detect(byte[] bytes) {
        if (isPng(bytes)) return decoded(bytes, "image/png", "png");
        if (isJpeg(bytes)) return decoded(bytes, "image/jpeg", "jpg");
        if (isWebp(bytes)) return webp(bytes);
        throw ApiException.badRequest("UNSUPPORTED_IMAGE", "Only valid JPEG, PNG, and WebP images are accepted");
    }

    private static Detected decoded(byte[] bytes, String contentType, String extension) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw invalid();
            return new Detected(contentType, extension, image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private static Detected webp(byte[] b) {
        if (b.length < 30) throw invalid();
        String chunk = ascii(b, 12, 4);
        int width;
        int height;
        if ("VP8X".equals(chunk)) {
            width = 1 + le24(b, 24);
            height = 1 + le24(b, 27);
        } else if ("VP8L".equals(chunk) && (b[20] & 0xff) == 0x2f) {
            width = 1 + ((b[21] & 0xff) | ((b[22] & 0x3f) << 8));
            height = 1 + (((b[22] & 0xc0) >> 6) | ((b[23] & 0xff) << 2) | ((b[24] & 0x0f) << 10));
        } else if ("VP8 ".equals(chunk) && (b[23] & 0xff) == 0x9d && (b[24] & 0xff) == 1 && (b[25] & 0xff) == 0x2a) {
            width = ((b[27] & 0xff) << 8 | (b[26] & 0xff)) & 0x3fff;
            height = ((b[29] & 0xff) << 8 | (b[28] & 0xff)) & 0x3fff;
        } else throw invalid();
        if (width <= 0 || height <= 0) throw invalid();
        return new Detected("image/webp", "webp", width, height);
    }

    private static boolean isPng(byte[] b) { return b.length >= 8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10; }
    private static boolean isJpeg(byte[] b) { return b.length >= 4 && (b[0]&255)==0xff && (b[1]&255)==0xd8 && (b[b.length-2]&255)==0xff && (b[b.length-1]&255)==0xd9; }
    private static boolean isWebp(byte[] b) { return b.length >= 16 && "RIFF".equals(ascii(b,0,4)) && "WEBP".equals(ascii(b,8,4)); }
    private static int le24(byte[] b,int i) { return (b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16); }
    private static String ascii(byte[] b,int i,int length) { return new String(b,i,length,java.nio.charset.StandardCharsets.US_ASCII); }
    private static ApiException invalid() { return ApiException.badRequest("INVALID_IMAGE", "The uploaded file is not a readable image"); }

    record Detected(String contentType, String extension, int width, int height) {}
}
