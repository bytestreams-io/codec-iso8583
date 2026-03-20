package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.FieldSpec;
import io.bytestreams.codec.core.util.Preconditions;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A {@link FieldSpec} that adds a bitmap bit index and automatic bitmap management.
 *
 * <p>When {@link #set(Object, Object)} is called, the bitmap is automatically updated: non-null
 * values set the bit, null values clear it. The {@link #presence()} predicate checks the bitmap bit
 * during encoding and decoding.
 *
 * <pre>{@code
 * class IsoMessage extends DataObject implements Bitmapped {
 *   static final BitmappedFieldSpec<IsoMessage, String> PAN =
 *       BitmappedFieldSpec.of(2, DataObject.field("pan", panCodec));
 *
 *   public String getPan() { return get(PAN); }
 *   public void setPan(String pan) { set(PAN, pan); }
 * }
 * }</pre>
 *
 * @param <T> the object type (must implement {@link Bitmapped})
 * @param <V> the field value type
 */
public interface BitmappedFieldSpec<T extends Bitmapped, V> extends FieldSpec<T, V> {

  /**
   * Returns the bitmap bit index for this field. Must be positive (bits are 1-indexed).
   *
   * @return the bit index, always &gt; 0
   */
  int bit();

  /**
   * Wraps a {@link FieldSpec} with bitmap management at the given bit index.
   *
   * <p>The returned spec delegates to the wrapped spec for get/set, and additionally sets or clears
   * the bitmap bit when a value is set.
   *
   * @param bit the bitmap bit index
   * @param delegate the field spec to wrap
   * @param <T> the object type (must implement Bitmapped)
   * @param <V> the field value type
   * @return a new BitmappedFieldSpec
   */
  static <T extends Bitmapped, V> BitmappedFieldSpec<T, V> of(int bit, FieldSpec<T, V> delegate) {
    Preconditions.check(bit > 0, "bit must be positive, got: %d", bit);
    Objects.requireNonNull(delegate, "delegate");
    return new BitmappedFieldSpec<>() {
      @Override
      public int bit() {
        return bit;
      }

      @Override
      public String name() {
        return delegate.name();
      }

      @Override
      public Codec<V> codec() {
        return delegate.codec();
      }

      @Override
      public V get(T obj) {
        return delegate.get(obj);
      }

      @Override
      public void set(T obj, V value) {
        delegate.set(obj, value);
        if (value != null) {
          obj.getBitmap().set(bit);
        } else {
          obj.getBitmap().clear(bit);
        }
      }

      @Override
      public Predicate<T> presence() {
        return msg -> msg.getBitmap().get(bit);
      }
    };
  }
}
