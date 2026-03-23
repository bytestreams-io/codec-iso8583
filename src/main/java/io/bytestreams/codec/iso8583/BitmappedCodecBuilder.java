package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.CodecException;
import io.bytestreams.codec.core.FieldSpec;
import io.bytestreams.codec.core.NotImplementedCodec;
import io.bytestreams.codec.core.SequentialObjectCodec;
import io.bytestreams.codec.core.util.Preconditions;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private final Supplier<T> factory;
  private final SequentialObjectCodec.Builder<T> delegate;

  private BitmappedCodecBuilder(Supplier<T> factory, SequentialObjectCodec.Builder<T> delegate) {
    this.factory = factory;
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
    return new BitmappedCodecBuilder<>(factory, SequentialObjectCodec.builder(factory));
  }

  /**
   * Adds a non-optional field before the bitmap.
   *
   * @param spec the field specification
   * @param <V> the field value type
   * @return this builder
   */
  public <V> BitmappedCodecBuilder<T> field(FieldSpec<T, V> spec) {
    delegate.field(spec);
    return this;
  }

  /**
   * Adds the bitmap field and transitions to the data field phase.
   *
   * @param spec the bitmap field specification
   * @param <B> the bitmap type
   * @return a {@link DataFieldBuilder} for adding data fields
   */
  public <B extends Bitmap> DataFieldBuilder<T> bitmap(FieldSpec<T, B> spec) {
    Objects.requireNonNull(spec, "spec");
    T prototype = factory.get();
    B bitmap =
        Objects.requireNonNull(spec.get(prototype), "bitmap must be initialized in the factory");
    return new DataFieldBuilder<>(delegate, bitmap::isExtensionBit, spec);
  }

  /**
   * Phase 2 builder for adding bitmap-driven data fields using {@link BitmappedFieldSpec}.
   *
   * @param <T> the bitmapped object type
   */
  public static class DataFieldBuilder<T extends Bitmapped> {
    private static final Logger logger = LoggerFactory.getLogger(DataFieldBuilder.class);
    private static final String EXTENSION_BIT_ERROR =
        "bit %d is an extension bit and cannot be used as a data field";
    private static final String DUPLICATE_BIT_ERROR = "duplicate codec for bit %d";
    private static final String UNREGISTERED_BIT_ERROR =
        "bit %d is set in bitmap but has no codec (use dataField, skip, or reject)";

    private final SequentialObjectCodec.Builder<T> delegate;
    private final IntPredicate isExtensionBit;
    private final TreeMap<Integer, FieldSpec<T, ?>> dataFields = new TreeMap<>();
    private final FieldSpec<T, ?> bitmapSpec;

    <B extends Bitmap> DataFieldBuilder(
        SequentialObjectCodec.Builder<T> delegate,
        IntPredicate isExtensionBit,
        FieldSpec<T, B> bitmapSpec) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.isExtensionBit = Objects.requireNonNull(isExtensionBit, "isExtensionBit");
      this.bitmapSpec = Objects.requireNonNull(bitmapSpec, "bitmapSpec");
    }

    static <T extends Bitmapped, V> V skip(T msg) {
      return null;
    }

    static <T, V> void reject(T msg, V value) {
      throw new IllegalStateException("setter method for rejected field should never be called");
    }

    static <T, V> V reject(T msg) {
      throw new IllegalStateException("getter method for rejected field should never be called");
    }

    /**
     * Adds an optional data field using a {@link BitmappedFieldSpec}.
     *
     * <p>The bit index and presence predicate are derived from the spec. The bit index must not be
     * an extension bit or a duplicate of an already-registered bit.
     *
     * @param spec the bitmapped field specification
     * @param <V> the field value type
     * @return this builder
     * @throws IllegalArgumentException if the spec's bit index is an extension bit or duplicate
     */
    public <V> DataFieldBuilder<T> dataField(BitmappedFieldSpec<T, V> spec) {
      Objects.requireNonNull(spec, "spec");
      validateBit(spec.bit());
      dataFields.put(spec.bit(), spec);
      return this;
    }

    /**
     * Skips a data field by reading and discarding its value during decode. During encode, the
     * field is written if the bit is set in the bitmap.
     *
     * <p>Use this for fields that exist on the wire but whose values are not needed.
     *
     * @param bit the bitmap bit index
     * @param codec the codec to read/write the field (value is discarded on decode)
     * @return this builder
     * @throws IllegalArgumentException if the bit index is an extension bit, not positive, or
     *     duplicate
     */
    public <V> DataFieldBuilder<T> skip(int bit, Codec<V> codec) {
      Preconditions.check(bit > 0, "bit must be positive, got: %d", bit);
      Objects.requireNonNull(codec, "codec");
      validateBit(bit);
      dataFields.put(
          bit,
          FieldSpec.of(
              "skip(" + bit + ")",
              codec,
              DataFieldBuilder::skip,
              (msg, v) -> {
                msg.getBitmap().clear(bit);
                logger.atDebug().addKeyValue("bit", bit).log("skipped field");
              },
              msg -> msg.getBitmap().get(bit)));
      return this;
    }

    /**
     * Rejects a data field by throwing if the bit is set. Use this for fields that should never
     * appear on the wire.
     *
     * @param bit the bitmap bit index
     * @param name descriptive name for error messages
     * @return this builder
     * @throws IllegalArgumentException if the bit index is an extension bit, not positive, or
     *     duplicate
     */
    public DataFieldBuilder<T> reject(int bit, String name) {
      Preconditions.check(bit > 0, "bit must be positive, got: %d", bit);
      Objects.requireNonNull(name, "name");
      validateBit(bit);
      dataFields.put(
          bit,
          FieldSpec.of(
              "reject(" + bit + ")",
              new NotImplementedCodec<>(),
              DataFieldBuilder::reject,
              DataFieldBuilder::reject,
              msg -> msg.getBitmap().get(bit)));
      return this;
    }

    private void validateBit(int bit) {
      Preconditions.check(!isExtensionBit.test(bit), EXTENSION_BIT_ERROR, bit);
      Preconditions.check(!dataFields.containsKey(bit), DUPLICATE_BIT_ERROR, bit);
    }

    /**
     * Builds the codec. Sorts data fields by bit index and validates the bitmap during decode.
     *
     * @return the constructed {@link BitmappedCodec}
     */
    public BitmappedCodec<T> build() {
      Set<Integer> registeredBits = Set.copyOf(dataFields.keySet());
      int lastBit = dataFields.isEmpty() ? 0 : dataFields.lastKey();
      addBitmapField(registeredBits, lastBit);
      dataFields.values().forEach(delegate::field);
      return new BitmappedCodec<>(delegate.build(), dataFields);
    }

    @SuppressWarnings("unchecked")
    private void addBitmapField(Set<Integer> registeredBits, int lastBit) {
      FieldSpec<T, Object> spec = (FieldSpec<T, Object>) bitmapSpec;
      delegate.field(
          spec.name(),
          spec.codec(),
          spec::get,
          validatingSetter(spec, registeredBits, lastBit),
          spec.presence());
    }

    private BiConsumer<T, Object> validatingSetter(
        FieldSpec<T, Object> spec, Set<Integer> registeredBits, int lastBit) {
      return (msg, bitmapValue) -> {
        spec.set(msg, bitmapValue);
        if (registeredBits.isEmpty()) {
          return;
        }
        msg.getBitmap().stream()
            .filter(bit -> bit <= lastBit)
            .filter(bit -> !isExtensionBit.test(bit))
            .filter(bit -> !registeredBits.contains(bit))
            .findFirst()
            .ifPresent(
                bit -> {
                  throw new CodecException(UNREGISTERED_BIT_ERROR.formatted(bit), null);
                });
        msg.getBitmap().stream()
            .filter(bit -> bit > lastBit)
            .filter(bit -> !isExtensionBit.test(bit))
            .forEach(
                bit ->
                    logger.atWarn().addKeyValue("bit", bit).log("unregistered bit set in bitmap"));
      };
    }
  }
}
