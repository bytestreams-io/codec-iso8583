package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.Codecs;

/** Factory methods for commonly used ISO 8583 field codecs. */
public final class FieldCodecs {

  private FieldCodecs() {}

  /**
   * Creates a codec for encoding and decoding a {@link SingleBlockBitmap}.
   *
   * @param length the number of bytes in the bitmap.
   * @return the codec.
   */
  public static Codec<SingleBlockBitmap> singleBlockBitmap(int length) {
    Bitmaps.checkSize(length);
    return Codecs.binary(length).xmap(SingleBlockBitmap::valueOf, SingleBlockBitmap::toByteArray);
  }
}
