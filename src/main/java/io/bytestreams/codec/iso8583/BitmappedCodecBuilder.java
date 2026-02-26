package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.NotImplementedCodec;
import io.bytestreams.codec.core.SequentialObjectCodec;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * A two-phase builder for constructing an {@link SequentialObjectCodec} over a {@link Bitmapped}
 * object.
 *
 * <p>Phase 1 ({@code BitmappedCodecBuilder}): add non-optional fields and then the bitmap field.
 * Phase 2 ({@code DataFieldBuilder}): add bitmap-driven data fields and build.
 *
 * @param <T> the bitmapped object type
 */
public class BitmappedCodecBuilder<T extends Bitmapped> {

  private final SequentialObjectCodec.Builder<T> delegate;

  private BitmappedCodecBuilder(SequentialObjectCodec.Builder<T> delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates a new builder.
   *
   * @param factory supplier that creates new instances during decoding
   * @param <T> the bitmapped object type
   * @return a new builder
   */
  public static <T extends Bitmapped> BitmappedCodecBuilder<T> builder(Supplier<T> factory) {
    return new BitmappedCodecBuilder<>(SequentialObjectCodec.builder(factory));
  }

  /**
   * Adds a non-optional field before the bitmap.
   *
   * @param name the field name
   * @param codec the codec for this field
   * @param getter function to extract the field value
   * @param setter consumer to set the field value
   * @param <V> the field value type
   * @return this builder
   */
  public <V> BitmappedCodecBuilder<T> field(
      String name, Codec<V> codec, Function<T, V> getter, BiConsumer<T, V> setter) {
    delegate.field(name, codec, getter, setter);
    return this;
  }

  /**
   * Adds a {@link SingleBlockBitmap} field named "bitmap" and transitions to the data field phase.
   *
   * @param bytes the number of bytes in the bitmap
   * @param getter function to extract the bitmap
   * @param setter consumer to set the bitmap
   * @return a {@link DataFieldBuilder} for adding data fields
   */
  public DataFieldBuilder<T> singleBlockBitmap(
      int bytes, Function<T, SingleBlockBitmap> getter, BiConsumer<T, SingleBlockBitmap> setter) {
    delegate.field("bitmap", FieldCodecs.singleBlockBitmap(bytes), getter, setter);
    return new DataFieldBuilder<>(delegate, bit -> false);
  }

  /**
   * Adds a {@link MultiBlockBitmap} field named "bitmap" and transitions to the data field phase.
   *
   * @param bytesPerBlock the number of bytes per block
   * @param getter function to extract the bitmap
   * @param setter consumer to set the bitmap
   * @return a {@link DataFieldBuilder} for adding data fields
   */
  public DataFieldBuilder<T> multiBlockBitmap(
      int bytesPerBlock,
      Function<T, MultiBlockBitmap> getter,
      BiConsumer<T, MultiBlockBitmap> setter) {
    delegate.field("bitmap", FieldCodecs.multiBlockBitmap(bytesPerBlock), getter, setter);
    int bitsPerBlock = bytesPerBlock * Byte.SIZE;
    DataFieldBuilder<T> builder = new DataFieldBuilder<>(delegate, bit -> bit % bitsPerBlock == 1);
    builder.skipExtensionBits();
    return builder;
  }

  /**
   * Phase 2 builder for adding bitmap-driven data fields.
   *
   * @param <T> the bitmapped object type
   */
  public static class DataFieldBuilder<T extends Bitmapped> {
    private final SequentialObjectCodec.Builder<T> delegate;
    private final IntPredicate isExtensionBit;
    private int currentBit = 1;
    private int extensionCount = 1;

    DataFieldBuilder(SequentialObjectCodec.Builder<T> delegate, IntPredicate isExtensionBit) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.isExtensionBit = Objects.requireNonNull(isExtensionBit, "isExtensionBit");
    }

    static <T> void unreachable(T msg, Object value) {
      throw new IllegalStateException("setter method for skipped field should never be called");
    }

    static <T> Object unreachable(T msg) {
      throw new IllegalStateException("getter method for skipped field should never be called");
    }

    /**
     * Adds an optional data field whose presence is driven by the bitmap.
     *
     * @param name the field name
     * @param codec the codec for this field
     * @param getter function to extract the field value
     * @param setter consumer to set the field value
     * @param <V> the field value type
     * @return this builder
     */
    public <V> DataFieldBuilder<T> dataField(
        String name, Codec<V> codec, Function<T, V> getter, BiConsumer<T, V> setter) {
      int bit = currentBit++;
      delegate.field(name, codec, getter, setter, msg -> msg.getBitmap().get(bit));
      skipExtensionBits();
      return this;
    }

    /**
     * Skips a bit position, registering a {@link NotImplementedCodec} that throws if the bit is
     * set.
     *
     * @param name descriptive name for the skipped field
     * @return this builder
     */
    public DataFieldBuilder<T> skip(String name) {
      int bit = currentBit++;
      skipBit(name, bit);
      skipExtensionBits();
      return this;
    }

    private void skipExtensionBits() {
      while (isExtensionBit.test(currentBit)) {
        int bit = currentBit++;
        skipBit(String.format("bitmap extension %d indicator", extensionCount++), bit);
      }
    }

    private void skipBit(String name, int bit) {
      delegate.field(
          name,
          new NotImplementedCodec<>(),
          DataFieldBuilder::unreachable,
          DataFieldBuilder::unreachable,
          msg -> msg.getBitmap().get(bit));
    }

    /**
     * Builds the codec.
     *
     * @return the constructed {@link SequentialObjectCodec}
     */
    public SequentialObjectCodec<T> build() {
      return delegate.build();
    }
  }
}
