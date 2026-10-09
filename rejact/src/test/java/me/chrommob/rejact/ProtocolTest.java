package me.chrommob.rejact;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void websocketHandshakeMatchesRfc() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", Ws.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }
    @Test void rejectsMalformedFrames() {
        for (byte[] input : List.of(
                new byte[]{(byte)0x82, 0}, // unmasked
                new byte[]{(byte)0x82, (byte)0xfe}, // truncated length
                new byte[]{(byte)0x82, (byte)0xff, (byte)0xff, 0, 0, 0, 0, 0, 0, 0},
                new byte[]{0x09, (byte)0x80, 0, 0, 0, 0}, // fragmented control
                new byte[]{(byte)0xc2, (byte)0x80, 0, 0, 0, 0})) { // reserved bit
            assertThrows(IOException.class, () -> Ws.read(new ByteArrayInputStream(input)));
        }
    }
    @Test void readsMaskedPayload() throws Exception {
        var frame = Ws.read(new ByteArrayInputStream(new byte[]{(byte)0x82, (byte)0x82, 1, 2, 3, 4, 6, 10}));
        assertTrue(frame.fin());
        assertArrayEquals(new byte[]{7, 8}, frame.payload());
    }
    @Test void validatesWireLengthsBeforeReading() {
        for (byte[] frame : List.of(new byte[]{1,0,0,0}, new byte[]{1,0,100,0,0}, new byte[]{1,0,0,0,0,1})) {
            assertThrows(IllegalArgumentException.class, () -> Wire.decode(frame));
        }
        assertEquals(Wire.LOAD, Wire.decode(new byte[]{1,0,0,0,0,0,0}).type());
        assertThrows(IllegalArgumentException.class, () -> Wire.decode(new byte[]{1,0,0,-1,-1,-1,-1}));
    }
    @Test void largeStringsEncodeAndOversizedStringsAreRejected() {
        byte[] encoded = Wire.encode(List.of(Ops.text("x", "界".repeat(200000))));
        assertEquals(0xb2, encoded[0] & 255);
        assertTrue(encoded.length > 600000);
        assertThrows(IllegalArgumentException.class, () -> Wire.encode(List.of(Ops.text("x", "a".repeat(Wire.MAX_MESSAGE + 1)))));
    }
    @Test void jsonRejectsTrailingInputAndExcessiveNesting() {
        for (String text : List.of("{} trailing", "", "[", "{", "\"a\nb\"", "01", "[".repeat(2000))) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(text), text.substring(0, Math.min(20, text.length())));
        }
        assertEquals(Map.of("x", List.of(1L, 2L)), Json.parse("{\"x\":[1,2]}"));
        assertFalse(Json.write("</script><script>alert(1)</script>").contains("<"));
    }
}
