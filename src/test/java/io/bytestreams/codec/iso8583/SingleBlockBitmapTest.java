package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lyang.randomparamsresolver.RandomParametersExtension.Randomize;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingleBlockBitmapTest extends BitmapTestBase<SingleBlockBitmap> {
  private int size;

  @BeforeEach
  void setUp(@Randomize(intMin = 1, intMax = 8) int size) {
    this.size = size;
    bitmap = new SingleBlockBitmap(size);
  }

  @Test
  void value_of_big_endian_msb_first() {
    // 0x80 = bit 1 (MSB of first byte), 0x01 = bit 16 (LSB of second byte)
    SingleBlockBitmap bitmap = SingleBlockBitmap.valueOf(new byte[] {(byte) 0x80, 0x01});
    assertThat(bitmap.get(1)).isTrue();
    assertThat(bitmap.get(2)).isFalse();
    assertThat(bitmap.get(8)).isFalse();
    assertThat(bitmap.get(9)).isFalse();
    assertThat(bitmap.get(15)).isFalse();
    assertThat(bitmap.get(16)).isTrue();
  }

  @Test
  void to_byte_array_big_endian_msb_first() {
    SingleBlockBitmap bitmap = new SingleBlockBitmap(2);
    bitmap.set(1);
    bitmap.set(16);
    assertThat(bitmap.toByteArray()).isEqualTo(new byte[] {(byte) 0x80, 0x01});
  }

  @Override
  @Test
  void to_byte_array(@Randomize RandomGenerator generator) {
    int bit = generator.nextInt(1, bitmap.capacity() + 1);
    bitmap.set(bit);
    assertThat(SingleBlockBitmap.valueOf(bitmap.toByteArray()).get(bit)).isTrue();
  }

  @Override
  protected void createBitmap(int size) {
    new SingleBlockBitmap(size);
  }

  @Override
  protected int expectedCapacity() {
    return this.size * Byte.SIZE;
  }

  @Override
  protected int randomDataBit(RandomGenerator generator, Bitmap bitmap) {
    return generator.nextInt(1, bitmap.capacity() + 1);
  }
}
