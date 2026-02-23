package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.util.Preconditions;
import java.util.BitSet;
import java.util.stream.IntStream;

/**
 * A {@link Bitmap} backed by a {@link BitSet} divided into fixed-size blocks. Blocks are
 * automatically allocated when bits are set, and deallocated when all bits in trailing blocks are
 * cleared.
 *
 * <p>The first bit of each block (bit 1, bit {@code size * 8 + 1}, etc.) is the extension
 * indicator, which signals whether a subsequent block is present. Extension bits are managed
 * automatically and cannot be set or cleared directly.
 *
 * <pre>{@code
 * // A standard ISO 8583 bitmap with 8-byte blocks, up to 3 extensions
 * MultiBlockBitmap bitmap = new MultiBlockBitmap(8, 3);
 * bitmap.set(3);   // sets bit 3 in block 0
 * bitmap.set(65);  // auto-expands to block 1, extension bit (bit 1) is set automatically
 * bitmap.get(1);   // true — extension indicator was auto-set
 * bitmap.toByteArray(); // 16 bytes (2 blocks)
 * }</pre>
 */
public class MultiBlockBitmap implements Bitmap {
  static final String EXTENSION_BIT_ERROR =
      "bit %d is an extension indicator and cannot be modified";
  static final String MAX_BLOCKS_ERROR = "maxBlocks should be between 1 and %d, but got [%d]";
  private final int size;
  private final int bitsPerBlock;
  private final int maxBlocks;
  private final BitSet bitSet;
  private int activeBlocks;

  /**
   * Creates a new MultiBlockBitmap with the maximum number of blocks calculated from the block
   * size.
   *
   * @param size the number of bytes per block.
   * @throws IllegalArgumentException if {@code size} is less than 1 or exceeds the maximum
   *     capacity.
   */
  public MultiBlockBitmap(int size) {
    this(size, Bitmaps.MAXIMUM_SIZE / size);
  }

  /**
   * Creates a new MultiBlockBitmap.
   *
   * @param size the number of bytes per block.
   * @param maxBlocks the maximum number of blocks.
   * @throws IllegalArgumentException if {@code size} is less than 1 or exceeds the maximum
   *     capacity, or if {@code maxBlocks} is less than 1 or exceeds the maximum allowed for the
   *     given size.
   */
  public MultiBlockBitmap(int size, int maxBlocks) {
    Bitmaps.checkSize(size);
    int maxAllowedBlocks = Bitmaps.MAXIMUM_SIZE / size;
    Preconditions.check(maxBlocks > 0, MAX_BLOCKS_ERROR, maxAllowedBlocks, maxBlocks);
    Preconditions.check(
        maxBlocks <= maxAllowedBlocks, MAX_BLOCKS_ERROR, maxAllowedBlocks, maxBlocks);
    this.size = size;
    this.bitsPerBlock = size * Byte.SIZE;
    this.maxBlocks = maxBlocks;
    this.activeBlocks = 1;
    this.bitSet = new BitSet();
  }

  /**
   * Returns the total number of bits in the bitmap.
   *
   * @return the total number of bits.
   */
  @Override
  public int capacity() {
    return maxBlocks * bitsPerBlock;
  }

  /**
   * Returns whether the given bit is set.
   *
   * @param bit the 1-based bit index to check.
   * @return {@code true} if the bit is set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}.
   */
  @Override
  public boolean get(int bit) {
    checkBit(bit);
    return bitSet.get(bit - 1);
  }

  /**
   * Sets the given bit. Blocks are automatically allocated as needed. Extension indicators are
   * updated automatically.
   *
   * @param bit the 1-based bit index to set.
   * @return {@code true} if the bit was not already set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}, or if {@code bit} is an extension indicator.
   */
  @Override
  public boolean set(int bit) {
    checkBit(bit);
    checkNotExtensionBit(bit);
    expandTo(bit);
    boolean changed = !bitSet.get(bit - 1);
    bitSet.set(bit - 1);
    if (changed) {
      syncExtensionBits(bit);
    }
    return changed;
  }

  /**
   * Clears the given bit. Extension indicators are updated automatically. Trailing empty blocks are
   * deallocated.
   *
   * @param bit the 1-based bit index to clear.
   * @return {@code true} if the bit was set, {@code false} otherwise.
   * @throws IllegalArgumentException if {@code bit} is less than 1 or greater than {@link
   *     #capacity()}, or if {@code bit} is an extension indicator.
   */
  @Override
  public boolean clear(int bit) {
    checkBit(bit);
    checkNotExtensionBit(bit);
    boolean changed = bitSet.get(bit - 1);
    bitSet.clear(bit - 1);
    if (changed) {
      syncExtensionBits(bit);
      shrinkActiveBlocks();
    }
    return changed;
  }

  /**
   * Returns the number of bits that are set.
   *
   * @return the number of bits set.
   */
  @Override
  public int cardinality() {
    return bitSet.cardinality();
  }

  /**
   * Returns a stream of the 1-based indices of all set bits.
   *
   * @return a stream of set bit indices.
   */
  @Override
  public IntStream stream() {
    return bitSet.stream().map(bit -> bit + 1);
  }

  /**
   * Converts the bitmap to a byte array in big-endian bit order. Trailing empty blocks are omitted.
   *
   * @return the byte array representation of the bitmap.
   */
  @Override
  public byte[] toByteArray() {
    byte[] packed = new byte[activeBlocks * size];
    for (int i = 0; i < packed.length; i++) {
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

  private void checkNotExtensionBit(int bit) {
    Preconditions.check((bit - 1) % bitsPerBlock != 0, EXTENSION_BIT_ERROR, bit);
  }

  private void expandTo(int bit) {
    int requiredBlocks = (bit - 1) / bitsPerBlock + 1;
    if (requiredBlocks > activeBlocks) {
      activeBlocks = requiredBlocks;
    }
  }

  private void syncExtensionBits(int changedBit) {
    int changedBlock = (changedBit - 1) / bitsPerBlock;
    // Last block never has an extension indicator
    bitSet.clear((activeBlocks - 1) * bitsPerBlock);
    // Update extension indicators from the changed block backward, stopping early
    for (int i = Math.min(changedBlock, activeBlocks - 2); i >= 0; i--) {
      int extensionBitIndex = i * bitsPerBlock;
      boolean shouldBeSet = hasAnyBitSet(i + 1);
      if (bitSet.get(extensionBitIndex) == shouldBeSet) {
        break;
      }
      if (shouldBeSet) {
        bitSet.set(extensionBitIndex);
      } else {
        bitSet.clear(extensionBitIndex);
      }
    }
  }

  private void shrinkActiveBlocks() {
    while (activeBlocks > 1 && !hasAnyBitSet(activeBlocks - 1)) {
      activeBlocks--;
    }
  }

  private boolean hasAnyBitSet(int block) {
    int start = block * bitsPerBlock;
    return !bitSet.get(start, start + bitsPerBlock).isEmpty();
  }
}
