package com.moodbuds.media;

import java.util.Base64;
import com.moodbuds.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImageMetadataTest {
    @Test void detectsRealPngFromContent(){
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var result=ImageMetadata.detect(png);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.width()).isEqualTo(1); assertThat(result.height()).isEqualTo(1);
    }
    @Test void rejectsTextDisguisedAsImage(){
        assertThatThrownBy(()->ImageMetadata.detect("not an image".getBytes())).isInstanceOf(ApiException.class)
                .extracting(e->((ApiException)e).code()).isEqualTo("UNSUPPORTED_IMAGE");
    }
    @Test void readsWebpExtendedDimensions(){
        byte[] webp=new byte[30];
        System.arraycopy("RIFF".getBytes(),0,webp,0,4); System.arraycopy("WEBP".getBytes(),0,webp,8,4); System.arraycopy("VP8X".getBytes(),0,webp,12,4);
        webp[24]=9; webp[27]=19;
        var result=ImageMetadata.detect(webp);
        assertThat(result.contentType()).isEqualTo("image/webp"); assertThat(result.width()).isEqualTo(10); assertThat(result.height()).isEqualTo(20);
    }
}
