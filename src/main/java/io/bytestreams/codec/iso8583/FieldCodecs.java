package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.Codecs;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

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

  /**
   * Creates an LLVAR (2-digit length prefix) variable-length codec with a byte-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param valueCodec the codec for the value.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> llvar(IntFunction<Codec<Integer>> encoding, Codec<V> valueCodec) {
    return Codecs.prefixed(encoding.apply(2), valueCodec);
  }

  /**
   * Creates an LLVAR (2-digit length prefix) variable-length codec with an item-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param lengthOf a function that returns the item count for a given value.
   * @param codecFactory a function that creates a codec for the given item count.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> llvar(
      IntFunction<Codec<Integer>> encoding,
      ToIntFunction<V> lengthOf,
      IntFunction<Codec<V>> codecFactory) {
    return Codecs.prefixed(encoding.apply(2), lengthOf, codecFactory);
  }

  /**
   * Creates an LLLVAR (3-digit length prefix) variable-length codec with a byte-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param valueCodec the codec for the value.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> lllvar(IntFunction<Codec<Integer>> encoding, Codec<V> valueCodec) {
    return Codecs.prefixed(encoding.apply(3), valueCodec);
  }

  /**
   * Creates an LLLVAR (3-digit length prefix) variable-length codec with an item-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param lengthOf a function that returns the item count for a given value.
   * @param codecFactory a function that creates a codec for the given item count.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> lllvar(
      IntFunction<Codec<Integer>> encoding,
      ToIntFunction<V> lengthOf,
      IntFunction<Codec<V>> codecFactory) {
    return Codecs.prefixed(encoding.apply(3), lengthOf, codecFactory);
  }

  /**
   * Creates an LLLLVAR (4-digit length prefix) variable-length codec with a byte-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param valueCodec the codec for the value.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> llllvar(IntFunction<Codec<Integer>> encoding, Codec<V> valueCodec) {
    return Codecs.prefixed(encoding.apply(4), valueCodec);
  }

  /**
   * Creates an LLLLVAR (4-digit length prefix) variable-length codec with an item-count prefix.
   *
   * @param encoding a function that creates a length codec for a given digit count (e.g. {@code
   *     Codecs::asciiInt}).
   * @param lengthOf a function that returns the item count for a given value.
   * @param codecFactory a function that creates a codec for the given item count.
   * @param <V> the value type.
   * @return the codec.
   */
  public static <V> Codec<V> llllvar(
      IntFunction<Codec<Integer>> encoding,
      ToIntFunction<V> lengthOf,
      IntFunction<Codec<V>> codecFactory) {
    return Codecs.prefixed(encoding.apply(4), lengthOf, codecFactory);
  }
}
