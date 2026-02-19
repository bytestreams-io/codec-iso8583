package io.bytestreams.codec.iso8583;

import java.util.stream.IntStream;

/**
 * Represents a set of bits indicating the presence or absence of data elements in a message. Bit
 * indices are 1-based.
 */
public interface Bitmap {

  /**
   * Returns the total number of bits in the bitmap.
   *
   * @return the total number of bits.
   */
  int capacity();

  /**
   * Returns whether the given bit is set.
   *
   * @param bit the 1-based bit index to check.
   * @return {@code true} if the bit is set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}.
   */
  boolean get(int bit);

  /**
   * Sets the given bit.
   *
   * @param bit the 1-based bit index to set.
   * @return {@code true} if the bit was not already set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}.
   */
  boolean set(int bit);

  /**
   * Clears the given bit.
   *
   * @param bit the 1-based bit index to clear.
   * @return {@code true} if the bit was set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}.
   */
  boolean clear(int bit);

  /**
   * Returns the number of bits that are set.
   *
   * @return the number of bits set.
   */
  int cardinality();

  /**
   * Returns a stream of the 1-based indices of all set bits.
   *
   * @return a stream of set bit indices.
   */
  IntStream stream();

  /**
   * Converts the bitmap to a byte array in big-endian bit order (bit 1 is the MSB of byte 0).
   *
   * @return the byte array representation of the bitmap.
   */
  byte[] toByteArray();
}
