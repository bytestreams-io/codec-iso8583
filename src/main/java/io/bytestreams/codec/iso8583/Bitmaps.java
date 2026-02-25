package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.util.Preconditions;
import java.util.BitSet;

/** Package-private utilities for {@link Bitmap} implementations. */
final class Bitmaps {
  static final int MAXIMUM_SIZE = Integer.MAX_VALUE / Byte.SIZE;
  static final String SIZE_ERROR = "size should be greater than 0, but got [%d]";
  static final String MAXIMUM_CAPACITY_ERROR = "maximum capacity %d bytes exceeded: [%d]";
  static final String BIT_ERROR = "bit should be between 1 and %d, but got [%d]";

  private Bitmaps() {}

  static void checkSize(int size) {
    Preconditions.check(size > 0, SIZE_ERROR, size);
    Preconditions.check(size <= MAXIMUM_SIZE, MAXIMUM_CAPACITY_ERROR, MAXIMUM_SIZE, size);
  }

  static void checkBit(int bit, int capacity) {
    Preconditions.check(bit > 0, BIT_ERROR, capacity, bit);
    Preconditions.check(bit <= capacity, BIT_ERROR, capacity, bit);
  }

  static byte[] toByteArray(BitSet bitSet, int length) {
    byte[] packed = new byte[length];
    for (int i = 0; i < length; i++) {
      for (int j = 0; j < Byte.SIZE; j++) {
        if (bitSet.get(i * Byte.SIZE + j)) {
          packed[i] |= (byte) (1 << (7 - j));
        }
      }
    }
    return packed;
  }
}
