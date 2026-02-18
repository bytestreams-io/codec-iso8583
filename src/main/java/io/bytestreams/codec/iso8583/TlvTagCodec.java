package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.EncodeResult;
import io.bytestreams.codec.core.util.InputStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HexFormat;

/** A codec for encoding and decoding BER-TLV tag identifiers as uppercase hex strings. */
class TlvTagCodec implements Codec<String> {
  private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();

  @Override
  public EncodeResult encode(String tag, OutputStream output) throws IOException {
    byte[] bytes = HEX_FORMAT.parseHex(tag);
    output.write(bytes);
    return new EncodeResult(1, bytes.length);
  }

  @Override
  public String decode(InputStream input) throws IOException {
    byte[] first = InputStreams.readFully(input, 1);
    if ((first[0] & 0xff & 0x1F) != 0x1F) {
      return HEX_FORMAT.formatHex(first);
    }
    ByteArrayOutputStream tagBytes = new ByteArrayOutputStream();
    tagBytes.write(first);
    byte[] next;
    do {
      next = InputStreams.readFully(input, 1);
      tagBytes.write(next);
    } while ((next[0] & 0xff & 0x80) != 0);
    return HEX_FORMAT.formatHex(tagBytes.toByteArray());
  }
}
