package io.bytestreams.codec.core;

import io.bytestreams.codec.core.util.Preconditions;
import io.bytestreams.codec.core.util.Predicates;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A codec for objects with sequential fields.
 *
 * <p>Each field is encoded/decoded in the order it was added to the builder. Optional fields use a
 * predicate to determine presence - if the predicate returns false, the field is skipped during
 * both encoding and decoding.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SequentialObjectCodec<Message> codec = Codecs.<Message>sequential(Message::new)
 *     .field("id", idCodec, Message::getId, Message::setId)
 *     .field("content", contentCodec, Message::getContent, Message::setContent)
 *     .field("tag", tagCodec, Message::getTag, Message::setTag,
 *            msg -> msg.getId() > 0)  // optional, based on earlier field
 *     .build();
 * }</pre>
 *
 * @param <T> the type of object to encode/decode
 */
public class SequentialObjectCodec<T> implements Codec<T>, Inspectable<T> {

  private static final Logger logger = LoggerFactory.getLogger(SequentialObjectCodec.class);
  private static final String MDC_KEY = "codec.field";
  private static final String LOG_KEY_FIELD = "field";

  private final List<FieldCodec<T, ?>> fields;
  private final Supplier<T> factory;

  SequentialObjectCodec(List<FieldCodec<T, ?>> fields, Supplier<T> factory) {
    this.fields = List.copyOf(fields);
    this.factory = factory;
  }

  /**
   * Creates a new builder for constructing a SequentialObjectCodec.
   *
   * @param factory factory that creates new instances during decoding
   * @param <T> the type of object to encode/decode
   * @return a new builder
   */
  public static <T> Builder<T> builder(Supplier<T> factory) {
    return new Builder<>(factory);
  }

  @Override
  public EncodeResult encode(T value, OutputStream output) throws IOException {
    int fieldCount = 0;
    int totalBytes = 0;
    for (FieldCodec<T, ?> field : fields) {
      EncodeResult result = field.encode(value, output);
      totalBytes += result.bytes();
      if (result.bytes() > 0) {
        fieldCount++;
      }
    }
    logger
        .atDebug()
        .addKeyValue("type", value.getClass().getSimpleName())
        .addKeyValue("fields", fieldCount)
        .addKeyValue("bytes", totalBytes)
        .log("encoded");
    return new EncodeResult(fieldCount, totalBytes);
  }

  @Override
  public T decode(InputStream input) throws IOException {
    T instance = Objects.requireNonNull(factory.get(), "factory.get() returned null");
    for (FieldCodec<T, ?> field : fields) {
      field.decode(instance, input);
    }
    logger.atDebug().addKeyValue("type", instance.getClass().getSimpleName()).log("decoded");
    return instance;
  }

  @Override
  public Object inspect(T object) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (FieldCodec<T, ?> field : fields) {
      if (field.presence().test(object)) {
        result.put(field.name(), Inspector.inspect(field.codec(), field.get(object)));
      }
    }
    return result;
  }

  /** Builder for constructing a SequentialObjectCodec. */
  public static class Builder<T> {
    private final List<FieldCodec<T, ?>> fields = new ArrayList<>();
    private final Supplier<T> factory;

    Builder(Supplier<T> factory) {
      this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * Adds a required field to the codec.
     *
     * @param name the field name (used in error messages)
     * @param codec the codec for this field's value
     * @param getter function to extract the field value for encoding
     * @param setter consumer to set the field value when decoding
     * @param <V> the field value type
     * @return this builder
     */
    public <V> Builder<T> field(
        String name, Codec<V> codec, Function<T, V> getter, BiConsumer<T, V> setter) {
      return field(name, codec, getter, setter, Predicates.alwaysTrue());
    }

    /**
     * Adds a field to the codec with a presence predicate.
     *
     * <p>The presence predicate determines whether the field should be encoded/decoded. If the
     * predicate returns false, the field is skipped. Note that during decoding, the predicate can
     * only reference fields that have already been decoded (earlier in the field order).
     *
     * @param name the field name (used in error messages)
     * @param codec the codec for this field's value
     * @param getter function to extract the field value for encoding
     * @param setter consumer to set the field value when decoding
     * @param presence predicate to determine if field is present
     * @param <V> the field value type
     * @return this builder
     */
    public <V> Builder<T> field(
        String name,
        Codec<V> codec,
        Function<T, V> getter,
        BiConsumer<T, V> setter,
        Predicate<T> presence) {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(codec, "codec");
      Objects.requireNonNull(getter, "getter");
      Objects.requireNonNull(setter, "setter");
      Objects.requireNonNull(presence, "presence");
      fields.add(new FieldCodec<>(name, codec, getter, setter, presence));
      return this;
    }

    /**
     * Adds a field to the codec using a FieldSpec.
     *
     * <p>The field's presence is determined by {@link FieldSpec#presence()}, which defaults to
     * always true (required field).
     *
     * @param spec the field specification
     * @param <V> the field value type
     * @return this builder
     */
    public <V> Builder<T> field(FieldSpec<T, V> spec) {
      Objects.requireNonNull(spec, "spec");
      return field(spec.name(), spec.codec(), spec::get, spec::set, spec.presence());
    }

    /**
     * Builds the SequentialObjectCodec.
     *
     * @return the constructed codec
     * @throws IllegalArgumentException if no fields were added
     */
    public SequentialObjectCodec<T> build() {
      Preconditions.check(!fields.isEmpty(), "at least one field is required");
      return new SequentialObjectCodec<>(fields, factory);
    }
  }

  /** Package-private field codec that handles encoding/decoding a single field. */
  static class FieldCodec<T, V> {
    private final String name;
    private final Codec<V> codec;
    private final Function<T, V> getter;
    private final BiConsumer<T, V> setter;
    private final Predicate<T> presence;

    FieldCodec(
        String name,
        Codec<V> codec,
        Function<T, V> getter,
        BiConsumer<T, V> setter,
        Predicate<T> presence) {
      this.name = name;
      this.codec = codec;
      this.getter = getter;
      this.setter = setter;
      this.presence = presence;
    }

    String name() {
      return name;
    }

    Codec<V> codec() {
      return codec;
    }

    Predicate<T> presence() {
      return presence;
    }

    V get(T object) {
      return getter.apply(object);
    }

    private static String pushFieldPath(String name) {
      String previous = MDC.get(MDC_KEY);
      MDC.put(MDC_KEY, previous == null ? name : previous + "." + name);
      return previous;
    }

    private static void popFieldPath(String previous) {
      if (previous == null) {
        MDC.remove(MDC_KEY);
      } else {
        MDC.put(MDC_KEY, previous);
      }
    }

    EncodeResult encode(T object, OutputStream output) {
      if (!presence.test(object)) {
        logger.atTrace().addKeyValue(LOG_KEY_FIELD, name).log("skipped");
        return EncodeResult.EMPTY;
      }
      boolean trace = logger.isTraceEnabled();
      String previousPath = trace ? pushFieldPath(name) : null;
      try {
        EncodeResult result = codec.encode(getter.apply(object), output);
        if (trace) {
          logger
              .atTrace()
              .addKeyValue(LOG_KEY_FIELD, MDC.get(MDC_KEY))
              .addKeyValue("bytes", result.bytes())
              .log("encoded");
        }
        return result;
      } catch (CodecException e) {
        throw e.withField(name);
      } catch (Exception e) {
        throw new CodecException(e.getMessage(), e).withField(name);
      } finally {
        if (trace) {
          popFieldPath(previousPath);
        }
      }
    }

    void decode(T object, InputStream input) {
      if (!presence.test(object)) {
        logger.atTrace().addKeyValue(LOG_KEY_FIELD, name).log("skipped");
        return;
      }
      boolean trace = logger.isTraceEnabled();
      String previousPath = trace ? pushFieldPath(name) : null;
      try {
        setter.accept(object, codec.decode(input));
        if (trace) {
          logger.atTrace().addKeyValue(LOG_KEY_FIELD, MDC.get(MDC_KEY)).log("decoded");
        }
      } catch (CodecException e) {
        throw e.withField(name);
      } catch (Exception e) {
        throw new CodecException(e.getMessage(), e).withField(name);
      } finally {
        if (trace) {
          popFieldPath(previousPath);
        }
      }
    }
  }
}
