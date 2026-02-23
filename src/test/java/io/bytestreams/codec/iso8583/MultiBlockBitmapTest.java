package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lyang.randomparamsresolver.RandomParametersExtension.Randomize;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiBlockBitmapTest extends BitmapTestBase<MultiBlockBitmap> {

  private int size;
  private int maxBlocks;

  @BeforeEach
  void setUp(
      @Randomize(intMin = 1, intMax = 9) int size,
      @Randomize(intMin = 2, intMax = 4) int maxBlocks) {
    this.size = size;
    this.maxBlocks = maxBlocks;
    this.bitmap = new MultiBlockBitmap(size, maxBlocks);
  }

  @Test
  void default_constructor() {
    MultiBlockBitmap bitmap = new MultiBlockBitmap(8);
    int expectedMaxBlocks = Bitmaps.MAXIMUM_SIZE / 8;
    assertThat(bitmap.capacity()).isEqualTo(expectedMaxBlocks * 8 * Byte.SIZE);
  }

  @Test
  void validates_max_blocks() {
    int maxAllowed = Bitmaps.MAXIMUM_SIZE / size;
    assertThatThrownBy(() -> new MultiBlockBitmap(size, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxBlocks should be between 1 and %d, but got [0]", maxAllowed);
    assertThatThrownBy(() -> new MultiBlockBitmap(size, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxBlocks should be between 1 and %d, but got [-1]", maxAllowed);
  }

  @Test
  void validates_max_blocks_overflow() {
    int maxAllowed = Bitmaps.MAXIMUM_SIZE / size;
    assertThatThrownBy(() -> new MultiBlockBitmap(size, maxAllowed + 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "maxBlocks should be between 1 and %d, but got [%d]", maxAllowed, maxAllowed + 1);
  }

  @Test
  void set_rejects_extension_bit(@Randomize RandomGenerator generator) {
    int extensionBit = randomExtensionBit(generator, bitmap);
    assertThatThrownBy(() -> bitmap.set(extensionBit)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clear_rejects_extension_bit(@Randomize RandomGenerator generator) {
    int extensionBit = randomExtensionBit(generator, bitmap);
    assertThatThrownBy(() -> bitmap.clear(extensionBit))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void to_byte_array_big_endian_msb_first() {
    // 3 blocks of 1 byte each
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 3);
    // Initially 1 block of zeros
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[1]);
    // Set bit 10 → auto-expands to 2 blocks
    // bit 1 = extension indicator (auto-set by sync) = 0x80
    // bit 10 = second bit of byte 1 = 0x40
    bitmap.set(10);
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[] {(byte) 0x80, 0x40});
    // Set bit 18 → auto-expands to 3 blocks
    // block 1 extension indicator (bit 9) auto-set = 0xC0
    bitmap.set(18);
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[] {(byte) 0x80, (byte) 0xC0, 0x40});
  }

  @Override
  @Test
  void to_byte_array(@Randomize RandomGenerator generator) {
    int bit = randomDataBit(generator, bitmap);
    bitmap.set(bit);
    SingleBlockBitmap result = SingleBlockBitmap.valueOf(bitmap.toByteArray());
    assertThat(result.get(bit)).isTrue();
  }

  @Test
  void to_byte_array_without_using_extensions(@Randomize RandomGenerator generator) {
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[size]);

    int baseDataBit =
        generator.ints(1, size * Byte.SIZE).filter(b -> b != 1).findFirst().orElseThrow();
    bitmap.set(baseDataBit);
    SingleBlockBitmap result = SingleBlockBitmap.valueOf(bitmap.toByteArray());
    assertThat(result.get(baseDataBit)).isTrue();
  }

  @Test
  void set_does_not_set_extension_when_next_block_is_empty() {
    // 3 blocks of 1 byte each
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 3);
    // Expand to 3 blocks, then clear the data to leave blocks empty
    bitmap.set(18);
    bitmap.clear(18);
    // Set a data bit in block 0 — block 1 is empty so no extension
    bitmap.set(2);
    assertThat(bitmap.get(1)).isFalse();
    assertThat(bitmap.get(2)).isTrue();
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[] {0x40});
  }

  @Test
  void clear_shrinks_active_blocks() {
    // 3 blocks of 1 byte each
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 3);
    // Expand to 3 blocks
    bitmap.set(18);
    assertThat(bitmap.toByteArray()).hasSize(3);
    // Clear last block data → shrinks to 2 blocks
    bitmap.clear(18);
    // Extension bit for block 0 should now be cleared (block 1 was already empty)
    assertThat(bitmap.get(1)).isFalse();
    assertThat(bitmap.toByteArray()).hasSize(1);
  }

  @Test
  void clear_does_not_shrink_when_last_block_has_data() {
    // 2 blocks of 1 byte each
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 2);
    // Set two data bits in block 1
    bitmap.set(10);
    bitmap.set(11);
    // Clear one — block 1 still has data, should not shrink
    bitmap.clear(10);
    assertThat(bitmap.get(1)).isTrue();
    assertThat(bitmap.toByteArray()).hasSize(2);
  }

  @Test
  void to_byte_array_after_clearing_extension() {
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 2);
    bitmap.set(10);
    bitmap.clear(10);
    // blocks expanded to 2 but block 1 is now empty
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[1]);
  }

  @Test
  void to_byte_array_with_used_extensions(@Randomize RandomGenerator generator) {
    int extensionBit = randomExtensionBit(generator, bitmap);
    int dataBit = extensionBit + size * Byte.SIZE + 1;

    bitmap.set(dataBit);
    SingleBlockBitmap result = SingleBlockBitmap.valueOf(bitmap.toByteArray());

    assertThat(result.get(extensionBit)).isTrue();
    assertThat(result.get(dataBit)).isTrue();
  }

  @Test
  void to_byte_array_with_fully_used_extensions() {
    for (int i = 0; i < maxBlocks - 1; i++) {
      int dataBit = (i + 1) * size * Byte.SIZE + 2;
      bitmap.set(dataBit);
    }
    SingleBlockBitmap result = SingleBlockBitmap.valueOf(bitmap.toByteArray());
    for (int i = 0; i < maxBlocks - 1; i++) {
      int extensionBit = i * size * Byte.SIZE + 1;
      int dataBit = (i + 1) * size * Byte.SIZE + 2;
      assertThat(result.get(extensionBit)).isTrue();
      assertThat(result.get(dataBit)).isTrue();
    }
  }

  @Override
  protected void createBitmap(int size) {
    new MultiBlockBitmap(size);
  }

  @Override
  protected int expectedCapacity() {
    return size * Byte.SIZE * maxBlocks;
  }

  @Override
  protected int randomDataBit(RandomGenerator generator, Bitmap bitmap) {
    return generator
        .ints(1, bitmap.capacity() + 1)
        .filter(b -> b % (size * Byte.SIZE) != 1)
        .findFirst()
        .orElseThrow();
  }

  private int randomExtensionBit(RandomGenerator generator, Bitmap bitmap) {
    return generator
        .ints(1, bitmap.capacity())
        .filter(b -> b % (size * Byte.SIZE) == 1)
        .filter(b -> b < ((maxBlocks - 1) * size * Byte.SIZE))
        .findFirst()
        .orElseThrow();
  }
}
