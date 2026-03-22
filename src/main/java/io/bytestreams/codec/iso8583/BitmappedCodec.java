package io.bytestreams.codec.iso8583;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.EncodeResult;
import io.bytestreams.codec.core.FieldSpec;
import io.bytestreams.codec.core.Inspectable;
import io.bytestreams.codec.core.SequentialObjectCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * A codec for bitmap-driven messages that supports field introspection.
 *
 * <p>Wraps a {@link SequentialObjectCodec} and implements {@link Inspectable} to produce structured
 * representations of decoded messages.
 *
 * @param <T> the bitmapped message type
 */
public class BitmappedCodec<T extends Bitmapped> implements Codec<T>, Inspectable<T> {

  private final SequentialObjectCodec<T> delegate;
  private final Map<Integer, FieldSpec<T, ?>> fields;

  BitmappedCodec(SequentialObjectCodec<T> delegate, Map<Integer, FieldSpec<T, ?>> fields) {
    this.delegate = delegate;
    this.fields = Map.copyOf(fields);
  }

  @Override
  public EncodeResult encode(T value, OutputStream output) throws IOException {
    return delegate.encode(value, output);
  }

  @Override
  public T decode(InputStream input) throws IOException {
    return delegate.decode(input);
  }

  @Override
  public Object inspect(T message) {
    return delegate.inspect(message);
  }

  /**
   * Returns the registered field specs keyed by bit index.
   *
   * @return an unmodifiable map of bit index to field spec
   */
  public Map<Integer, FieldSpec<T, ?>> fieldSpecs() {
    return fields;
  }
}
