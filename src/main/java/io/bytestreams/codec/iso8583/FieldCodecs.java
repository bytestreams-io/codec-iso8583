package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.Codecs;

/** Factory methods for commonly used ISO 8583 field codecs. */
public final class FieldCodecs {

  private FieldCodecs() {}

  /**
   * Creates a codec for encoding and decoding a {@link SingleBlockBitmap}.
   *
   * @param size the number of bytes in the bitmap.
   * @return the codec.
   * @throws IllegalArgumentException if {@code size} is less than 1 or exceeds the maximum
   *     capacity.
   */
  public static Codec<SingleBlockBitmap> singleBlockBitmap(int size) {
    Bitmaps.checkSize(size);
    return Codecs.binary(size).xmap(SingleBlockBitmap::valueOf, SingleBlockBitmap::toByteArray);
  }

  /**
   * Creates a codec for encoding and decoding a {@link MultiBlockBitmap}. Decoding reads blocks
   * dynamically based on extension indicators.
   *
   * @param size the number of bytes per block.
   * @return the codec.
   * @throws IllegalArgumentException if {@code size} is less than 1 or exceeds the maximum
   *     capacity.
   */
  public static Codec<MultiBlockBitmap> multiBlockBitmap(int size) {
    Bitmaps.checkSize(size);
    return new MultiBlockBitmapCodec(size);
  }

  /**
   * Creates a codec for encoding and decoding a {@link MultiBlockBitmap} with a maximum number of
   * blocks. Decoding reads blocks dynamically based on extension indicators.
   *
   * @param size the number of bytes per block.
   * @param maxBlocks the maximum number of blocks.
   * @return the codec.
   * @throws IllegalArgumentException if {@code size} is less than 1 or exceeds the maximum
   *     capacity, or if {@code maxBlocks} is invalid for the given size.
   */
  public static Codec<MultiBlockBitmap> multiBlockBitmap(int size, int maxBlocks) {
    Bitmaps.checkSize(size);
    return new MultiBlockBitmapCodec(size, maxBlocks);
  }

  /**
   * Creates a codec for encoding and decoding BER-TLV tag identifiers as uppercase hex strings.
   * Decoding reads bytes dynamically based on the tag structure.
   *
   * @return the codec.
   */
  public static Codec<String> tlvTag() {
    return new TlvTagCodec();
  }

  /**
   * Creates a codec for encoding and decoding BER-TLV length fields. Supports short form (0-127)
   * and long form (128-2,147,483,647).
   *
   * @return the codec.
   */
  public static Codec<Integer> tlvLength() {
    return new TlvLengthCodec();
  }
}
