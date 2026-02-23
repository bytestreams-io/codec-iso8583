package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.EncodeResult;
import io.bytestreams.codec.core.util.InputStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A codec for encoding and decoding a {@link MultiBlockBitmap}. */
class MultiBlockBitmapCodec implements Codec<MultiBlockBitmap> {
  private final int size;
  private final int maxBlocks;
  private final int bitsPerBlock;

  MultiBlockBitmapCodec(int size) {
    this(size, Bitmaps.MAXIMUM_SIZE / size);
  }

  MultiBlockBitmapCodec(int size, int maxBlocks) {
    this.size = size;
    this.maxBlocks = maxBlocks;
    this.bitsPerBlock = size * Byte.SIZE;
  }

  @Override
  public EncodeResult encode(MultiBlockBitmap bitmap, OutputStream output) throws IOException {
    byte[] byteArray = bitmap.toByteArray();
    output.write(byteArray);
    return new EncodeResult(byteArray.length / size, byteArray.length);
  }

  @Override
  public MultiBlockBitmap decode(InputStream input) throws IOException {
    MultiBlockBitmap bitmap = new MultiBlockBitmap(size, maxBlocks);
    int blockIndex = 0;
    byte[] block;
    do {
      block = InputStreams.readFully(input, size);
      setDataBits(bitmap, block, blockIndex);
      blockIndex++;
    } while ((block[0] & 0x80) != 0);
    return bitmap;
  }

  private void setDataBits(MultiBlockBitmap bitmap, byte[] block, int blockIndex) {
    int offset = blockIndex * bitsPerBlock;
    for (int i = 0; i < block.length; i++) {
      for (int j = 0; j < Byte.SIZE; j++) {
        if (i == 0 && j == 0) {
          continue; // extension indicator is auto-managed
        }
        if ((block[i] & 0xff & (1 << (7 - j))) != 0) {
          bitmap.set(offset + i * Byte.SIZE + j + 1);
        }
      }
    }
  }
}
