package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lyang.randomparamsresolver.RandomParametersExtension;
import io.github.lyang.randomparamsresolver.RandomParametersExtension.Randomize;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(RandomParametersExtension.class)
abstract class BitmapTestBase<T extends Bitmap> {
  protected T bitmap;

  @Test
  void validates_size(@Randomize(intMax = 1) int negative) {
    assertThatThrownBy(() -> createBitmap(negative))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("size should be greater than 0, but got [%d]", negative);
  }

  @Test
  void validates_maximum_capacity() {
    assertThatThrownBy(() -> createBitmap(Integer.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maximum capacity 268435455 bytes exceeded: [%d]", Integer.MAX_VALUE);
  }

  @Test
  void capacity() {
    assertThat(bitmap.capacity()).isEqualTo(expectedCapacity());
  }

  @Test
  void get(@Randomize RandomGenerator generator) {
    assertThat(bitmap.get(generator.nextInt(1, bitmap.capacity()))).isFalse();
  }

  @Test
  void get_out_of_bounds(@Randomize RandomGenerator generator) {
    int negative = generator.nextInt(Integer.MIN_VALUE, 1);
    assertThatThrownBy(() -> bitmap.get(negative))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bit should be between 1 and %d, but got [%d]", bitmap.capacity(), negative);

    int overCapacity = generator.nextInt(bitmap.capacity() + 1, Integer.MAX_VALUE);
    assertThatThrownBy(() -> bitmap.get(overCapacity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "bit should be between 1 and %d, but got [%d]", bitmap.capacity(), overCapacity);
  }

  @Test
  void set(@Randomize RandomGenerator generator) {
    int bit = randomDataBit(generator, bitmap);
    assertThat(bitmap.get(bit)).isFalse();
    assertThat(bitmap.set(bit)).isTrue();
    assertThat(bitmap.get(bit)).isTrue();
    assertThat(bitmap.set(bit)).isFalse();
  }

  @Test
  void set_out_of_bounds(@Randomize RandomGenerator generator) {
    int negative = generator.nextInt(Integer.MIN_VALUE, 1);
    assertThatThrownBy(() -> bitmap.set(negative))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bit should be between 1 and %d, but got [%d]", bitmap.capacity(), negative);

    int overCapacity = generator.nextInt(bitmap.capacity() + 1, Integer.MAX_VALUE);
    assertThatThrownBy(() -> bitmap.set(overCapacity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "bit should be between 1 and %d, but got [%d]", bitmap.capacity(), overCapacity);
  }

  @Test
  void clear(@Randomize RandomGenerator generator) {
    int bit = randomDataBit(generator, bitmap);

    assertThat(bitmap.get(bit)).isFalse();
    assertThat(bitmap.clear(bit)).isFalse();
    assertThat(bitmap.get(bit)).isFalse();

    bitmap.set(bit);
    assertThat(bitmap.get(bit)).isTrue();
    assertThat(bitmap.clear(bit)).isTrue();
    assertThat(bitmap.get(bit)).isFalse();
  }

  @Test
  void clear_out_of_bounds(@Randomize RandomGenerator generator) {
    int negative = generator.nextInt(Integer.MIN_VALUE, 1);
    assertThatThrownBy(() -> bitmap.clear(negative))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bit should be between 1 and %d, but got [%d]", bitmap.capacity(), negative);

    int overCapacity = generator.nextInt(bitmap.capacity() + 1, Integer.MAX_VALUE);
    assertThatThrownBy(() -> bitmap.clear(overCapacity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "bit should be between 1 and %d, but got [%d]", bitmap.capacity(), overCapacity);
  }

  @Test
  void cardinality(@Randomize RandomGenerator generator) {
    int bit = randomDataBit(generator, bitmap);

    assertThat(bitmap.cardinality()).isZero();
    bitmap.set(bit);
    assertThat(bitmap.cardinality()).isPositive();

    bitmap.clear(bit);
    assertThat(bitmap.cardinality()).isZero();
  }

  @Test
  void int_stream(@Randomize RandomGenerator generator) {
    int bit = randomDataBit(generator, bitmap);
    assertThat(bitmap.stream().boxed().toList()).isEmpty();

    bitmap.set(bit);
    assertThat(bitmap.stream().boxed().toList()).contains(bit);
  }

  @Test
  abstract void to_byte_array(@Randomize RandomGenerator generator);

  protected abstract void createBitmap(int size);

  protected abstract int expectedCapacity();

  protected abstract int randomDataBit(RandomGenerator generator, Bitmap bitmap);
}
