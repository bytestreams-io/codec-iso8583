package io.bytestreams.codec.iso8583;

import java.util.BitSet;
import java.util.stream.IntStream;

/**
 * SingleBlockBitmap is a {@link Bitmap} backed by a {@link BitSet}. It has a fixed capacity and
 * cannot be extended.
 */
public class SingleBlockBitmap implements Bitmap {
  private final int size;
  private final BitSet bitSet;

  /**
   * Creates a new SingleBlockBitmap.
   *
   * @param size the number of bytes in the block.
   */
  public SingleBlockBitmap(int size) {
    Bitmaps.checkSize(size);
    this.size = size;
    this.bitSet = new BitSet(size * Byte.SIZE);
  }

  /**
   * Creates a new SingleBlockBitmap.
   *
   * @param bytes the byte array representation of the bitmap.
   * @return the SingleBlockBitmap.
   */
  public static SingleBlockBitmap valueOf(byte[] bytes) {
    SingleBlockBitmap bitmap = new SingleBlockBitmap(bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      for (int j = 0; j < Byte.SIZE; j++) {
        if ((bytes[i] & (1 << (7 - j))) != 0) {
          bitmap.bitSet.set(i * Byte.SIZE + j);
        }
      }
    }
    return bitmap;
  }

  /**
   * @return the total number of bits in the {@link Bitmap}.
   */
  @Override
  public int capacity() {
    return size * Byte.SIZE;
  }

  /**
   * Checks if the given bit is set.
   *
   * @param bit the bit to check. NOTE: The bit index starts from {@code 1}.
   * @return {@code true} if the bit is set, {@code false} otherwise.
   */
  @Override
  public boolean get(int bit) {
    checkBit(bit);
    return bitSet.get(bit - 1);
  }

  /**
   * Sets the given bit.
   *
   * @param bit the bit to set. NOTE: The bit index starts from {@code 1}.
   */
  @Override
  public boolean set(int bit) {
    checkBit(bit);
    boolean changed = !bitSet.get(bit - 1);
    bitSet.set(bit - 1);
    return changed;
  }

  /**
   * Clears the given bit.
   *
   * @param bit the bit to clear. NOTE: The bit index starts from {@code 1}.
   */
  @Override
  public boolean clear(int bit) {
    checkBit(bit);
    boolean changed = bitSet.get(bit - 1);
    bitSet.clear(bit - 1);
    return changed;
  }

  /**
   * @return the number of bits set in the {@link Bitmap}.
   */
  @Override
  public int cardinality() {
    return bitSet.cardinality();
  }

  /**
   * @return a stream of the bits set in the {@link Bitmap}.
   */
  @Override
  public IntStream stream() {
    return bitSet.stream().map(bit -> bit + 1);
  }

  /**
   * Converts the {@link Bitmap} to a byte array.
   *
   * @return the byte array representation of the {@link Bitmap}.
   */
  @Override
  public byte[] toByteArray() {
    byte[] packed = new byte[size];
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < Byte.SIZE; j++) {
        if (bitSet.get(i * Byte.SIZE + j)) {
          packed[i] |= (byte) (1 << (7 - j));
        }
      }
    }
    return packed;
  }

  private void checkBit(int bit) {
    Bitmaps.checkBit(bit, capacity());
  }
}
