package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.EncodeResult;
import io.bytestreams.codec.core.util.InputStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** A codec for encoding and decoding a {@link MultiBlockBitmap} as hex ASCII strings. */
class HexMultiBlockBitmapCodec implements Codec<MultiBlockBitmap> {
  private final int size;
  private final int maxBlocks;
  private final int bitsPerBlock;

  HexMultiBlockBitmapCodec(int size) {
    this(size, Bitmaps.MAXIMUM_SIZE / size);
  }

  HexMultiBlockBitmapCodec(int size, int maxBlocks) {
    this.size = size;
    this.maxBlocks = maxBlocks;
    this.bitsPerBlock = size * Byte.SIZE;
  }

  @Override
  public EncodeResult encode(MultiBlockBitmap bitmap, OutputStream output) throws IOException {
    byte[] raw = bitmap.toByteArray();
    byte[] hexBytes = Bitmaps.HEX.formatHex(raw).getBytes(StandardCharsets.US_ASCII);
    output.write(hexBytes);
    return new EncodeResult(raw.length / size, hexBytes.length);
  }

  @Override
  public MultiBlockBitmap decode(InputStream input) throws IOException {
    MultiBlockBitmap bitmap = new MultiBlockBitmap(size, maxBlocks);
    int blockIndex = 0;
    byte[] block;
    do {
      byte[] hexChars = InputStreams.readFully(input, size * 2);
      block = Bitmaps.HEX.parseHex(new String(hexChars, StandardCharsets.US_ASCII));
      Bitmaps.setDataBits(bitmap, block, blockIndex, bitsPerBlock);
      blockIndex++;
    } while ((block[0] & 0x80) != 0);
    return bitmap;
  }
}
