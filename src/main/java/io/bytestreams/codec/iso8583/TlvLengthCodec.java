package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.EncodeResult;
import io.bytestreams.codec.core.util.InputStreams;
import io.bytestreams.codec.core.util.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A codec for encoding and decoding BER-TLV length fields. */
class TlvLengthCodec implements Codec<Integer> {

  @Override
  public EncodeResult encode(Integer length, OutputStream output) throws IOException {
    Preconditions.check(length >= 0, "length must not be negative: %d", length);
    if (length <= 0x7F) {
      output.write(length);
      return new EncodeResult(1, 1);
    }
    int numBytes;
    if (length <= 0xFF) {
      numBytes = 1;
    } else if (length <= 0xFFFF) {
      numBytes = 2;
    } else if (length <= 0xFFFFFF) {
      numBytes = 3;
    } else {
      numBytes = 4;
    }
    output.write(0x80 | numBytes);
    for (int i = (numBytes - 1) * 8; i >= 0; i -= 8) {
      output.write((length >> i) & 0xFF);
    }
    return new EncodeResult(1, 1 + numBytes);
  }

  @Override
  public Integer decode(InputStream input) throws IOException {
    byte[] first = InputStreams.readFully(input, 1);
    int firstByte = first[0] & 0xFF;
    if ((firstByte & 0x80) == 0) {
      return firstByte;
    }
    int numBytes = firstByte & 0x7F;
    Preconditions.check(numBytes != 0, "indefinite length not supported");
    Preconditions.check(numBytes <= 4, "length field too large: %d bytes", numBytes);
    byte[] bytes = InputStreams.readFully(input, numBytes);
    int length = 0;
    for (byte b : bytes) {
      length = (length << 8) | (b & 0xFF);
    }
    Preconditions.check(length >= 0, "length overflow: %d bytes", numBytes);
    return length;
  }
}
